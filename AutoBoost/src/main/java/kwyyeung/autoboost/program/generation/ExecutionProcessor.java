package kwyyeung.autoboost.program.generation;

import kwyyeung.autoboost.application.AutoBoost;
import kwyyeung.autoboost.application.PROGRAM_STATE;
import kwyyeung.autoboost.entity.ACCESS;
import kwyyeung.autoboost.entity.METHOD_TYPE;
import kwyyeung.autoboost.entity.UnrecognizableException;
import kwyyeung.autoboost.helper.Helper;
import kwyyeung.autoboost.helper.Properties;
import kwyyeung.autoboost.program.analysis.MethodDetails;
import kwyyeung.autoboost.program.execution.ExecutionTrace;
import kwyyeung.autoboost.program.execution.MethodExecution;
import kwyyeung.autoboost.program.execution.variable.*;
import kwyyeung.autoboost.program.generation.test.TestCase;
import kwyyeung.autoboost.program.instrumentation.InstrumentResult;
import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mockito.Mockito;

import java.lang.reflect.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class ExecutionProcessor {
    private static final Logger logger = LogManager.getLogger(ExecutionProcessor.class);
    private static final String[] SKIP_MEMBER_METHODS = {"equals", "toString", "hashCode"};
    private static final String[] SKIP_STATIC_METHODS = {"hashCode"};
    private final ExecutionTrace executionTrace = ExecutionTrace.getSingleton();
    private final Map<Class, MethodExecution> classToDefExeMap = new HashMap<>();
    private final InstrumentResult instrumentResult = InstrumentResult.getSingleton();

    protected boolean testSetUp(MethodExecution target) {
        return isPossibleTarget(target) && targetIsBestMatch(target) && canConstructCallee(target) && checkAndSetRequiredPackage(target) && canRecreateParams(target) && (target.getRequiredPackage().isEmpty() || target.getRequiredPackage().startsWith(kwyyeung.autoboost.helper.Properties.getSingleton().getPUT()));
    }

    /**
     * @param target
     * @return if the setup is successful
     */
    protected boolean normalTestSetUp(MethodExecution target) {
        return targetIsAssertable(target);
    }

    protected boolean exceptionalTestSetUp(MethodExecution target) {
        return targetIsThrowingException(target);
    }


    private boolean canRecreateParams(MethodExecution target) {
        Set<Integer> toMock = new HashSet<>();
        Set<Integer> cannotMock = new HashSet<>();
        if (target.getCalleeId() != -1 && target.getCallee() instanceof ObjVarDetails) {
            MethodExecution defExe = getExeConstructingClass(target.getCallee().getType(), true);
            toMock.addAll(getInputAndDes(defExe));
            cannotMock.addAll(getUnmockableInputs(defExe));
        }
        toMock.addAll(getInputAndDes(target));
        cannotMock.addAll(getUnmockableInputs(target));
        return IntStream.range(0, target.getParams().size())
                .allMatch(pID -> {
                    VarDetail p = executionTrace.getVarDetailByID(target.getParams().get(pID));
                    Class<?> paramType = Helper.sootTypeToClass(target.getMethodInvoked().getParameterTypes().get(pID));
                    return canRecreateParam(target, paramType, p);
                }) &&
                !hasUnmockableUsages(target, toMock, cannotMock, new HashSet<>());
    }

    private boolean hasUnmockableUsages(MethodExecution target, Set<Integer> mockables, Set<Integer> unmockables, Set<MethodExecution> processed) {
        if (processed.contains(target)) return false;
        processed.add(target);
        if (executionTrace.getChildren(target.getID()).stream()
                .map(executionTrace::getMethodExecutionByID)
                .anyMatch(e -> {
                    MethodDetails md = e.getMethodInvoked();
                    if (unmockables.contains(e.getCalleeId())) return true;
                    if (mockables.contains(e.getCalleeId())) {
                        if (md.isFieldAccess()) return true;
//                        if (e.getParams().stream().map(executionTrace::getVarDetailByID).filter(p -> p instanceof ObjVarDetails && !p.equals(executionTrace.getNullVar())).anyMatch(p -> !target.getParams().contains(p.getID())))
//                            return true;
//                        if (!IntStream.range(0, e.getParams().size()).allMatch(pID -> {
//                            VarDetail p = executionTrace.getVarDetailByID(e.getParams().get(pID));
//                            Class<?> paramType = sootTypeToClass(e.getMethodInvoked().getParameterTypes().get(pID));
//                            if(p instanceof ObjVarDetails)
//                                logger.debug("herere");
//                            return canRecreateParam(target, paramType, p);
//                        })) return true;
                        if ((md.getName().equals("equals") || md.getName().equals("hashCode") || md.getName().equals("getClass")))
                            return true;
                        if (e.getReturnValId() != -1 && !canProvideReturnVal(e, executionTrace.getVarDetailByID(e.getReturnValId())))
                            return true;
                    }
                    if (InstrumentResult.getSingleton().isLibMethod(md.getId()) && e.getParams().stream().anyMatch(mockables::contains))
                        return true;
                    return hasUnmockableUsages(e, mockables, unmockables, processed);
                })) {
            return true;
        }
        processed.remove(target);
        return false;
    }

    private boolean canProvideReturnVal(MethodExecution execution, VarDetail returnVal) {
        if (returnVal instanceof EnumVarDetails && returnVal.getType().equals(Class.class)) {
            String neededPackage = null;
            try {
                neededPackage = Helper.getRequiredPackage(ClassUtils.getClass(((EnumVarDetails) returnVal).getValue()));
            } catch (ClassNotFoundException ignored) {
                logger.error(((EnumVarDetails) returnVal).getValue() + " class not found ");
            }
            if (neededPackage == null || (!neededPackage.isEmpty() && !execution.getRequiredPackage().isEmpty() && !execution.getRequiredPackage().equals(neededPackage)))
                return false;
            if (!neededPackage.isEmpty()) execution.setRequiredPackage(neededPackage);
        }
        Class<?> declaredReturnType = execution.getReturnValId() == -1 ? null : execution.getMethodInvoked().getReturnType();
        if (returnVal instanceof ObjVarDetails && !returnVal.equals(executionTrace.getNullVar()) && ((declaredReturnType == null && Helper.getRequiredPackage(returnVal.getType()) == null) || (declaredReturnType != null && !declaredReturnType.isAssignableFrom(Helper.getAccessibleSuperType(returnVal.getType(), execution.getRequiredPackage())))))
            return false;
        if (returnVal instanceof ArrVarDetails)
            return ((ArrVarDetails) returnVal).getComponents().stream().map(executionTrace::getVarDetailByID).allMatch(c -> canProvideReturnVal(execution, c));
        if (returnVal instanceof MapVarDetails)
            return ((MapVarDetails) returnVal).getKeyValuePairs().stream().flatMap(c -> Stream.of(c.getKey(), c.getValue())).map(executionTrace::getVarDetailByID).allMatch(c -> canProvideReturnVal(execution, c));
        return true;
    }

    private boolean canRecreateParam(MethodExecution target, Class<?> paramType, VarDetail p) {
        if (p instanceof ObjVarDetails) {
            if (p.equals(executionTrace.getNullVar())) return true;
            if (paramType != null && !paramType.isAssignableFrom(Helper.getAccessibleMockableSuperType(p.getType(), target.getRequiredPackage())))
                return false;
            if (paramType == null && Helper.getRequiredPackage(p.getType()) == null) return false;
            if (executionTrace.hasFieldAccess(target, p) && p.getType().getPackage().getName().startsWith(kwyyeung.autoboost.helper.Properties.getSingleton().getPUT()))
                return false;
            if (Helper.isCannotMockType(p.getType())) {
                if ((executionTrace.hasUsageAsCallee(target, Collections.singleton(p.getID())) || executionTrace.hasFieldAccess(target, p)) && executionTrace.getParentExeStack(p, true) == null)
                    return false;
                else
                    return getExeConstructingClass(p.getType(), true) != null || executionTrace.getParentExeStack(p, true) != null;
            }

            return true;
        }
        if (p instanceof ArrVarDetails)
            return ((ArrVarDetails) p).getComponents().stream()
                    .map(executionTrace::getVarDetailByID)
                    .allMatch(pc -> canRecreateParam(target, paramType.getComponentType(), pc));
        if (p instanceof MapVarDetails)
            return ((MapVarDetails) p).getKeyValuePairs().stream().flatMap(c -> Stream.of(c.getKey(), c.getValue()))
                    .map(executionTrace::getVarDetailByID)
                    .allMatch(pc -> canRecreateParam(target, null, pc));
        if (p instanceof EnumVarDetails) {
            String requiredPackage = "";
            try {
                if (!p.getType().equals(Class.class) && !p.getType().isEnum()) {
                    if (p.getType().getPackage().getName().startsWith(kwyyeung.autoboost.helper.Properties.getSingleton().getPUT())) {
                        return InstrumentResult.getSingleton().getClassPublicFieldsMap().getOrDefault(p.getType().getName(), new HashSet<>()).contains(((EnumVarDetails) p).getValue());
                    } else
                        return Modifier.isPublic(p.getType().getField(((EnumVarDetails) p).getValue()).getModifiers());
                }
                if (p.getType().equals(Class.class))
                    requiredPackage = Helper.getRequiredPackage(ClassUtils.getClass(((EnumVarDetails) p).getValue()));
                if (p.getType().isEnum())
                    requiredPackage = Helper.getRequiredPackage(p.getType());
                if (requiredPackage == null || (!target.getRequiredPackage().isEmpty() && !requiredPackage.isEmpty() && !requiredPackage.equals(target.getRequiredPackage())))
                    return false;
                if (!requiredPackage.isEmpty())
                    target.setRequiredPackage(requiredPackage);
            } catch (ClassNotFoundException | NoSuchFieldException e) {
                logger.error(((EnumVarDetails) p).getValue() + "  not found ");
                throw new RuntimeException(e);
            }
        }
        return true;
    }

    private Set<Integer> getInputAndDes(MethodExecution execution) {
        Set<Integer> inputsAndDes = execution.getParams().stream()
                .map(executionTrace::getVarDetailByID)
                .map(executionTrace::getRelatedObjVarIDs)
                .flatMap(Collection::stream)
                .map(executionTrace::getVarDetailByID)
                .filter(p -> !Helper.isCannotMockType(p.getType())) // do not track the var if they are NOT to be mocked
//                .filter(p -> {
//                    return p.getType().getName().startsWith(Properties.getSingleton().getPUT()) || (executionTrace.getParentExeStack(p, true) == null || executionTrace.getParentExeStack(p, true).stream().anyMatch(e -> e.getMethodInvoked().getDeclaringClass().getPackageName().startsWith(Properties.getSingleton().getPUT())));
//                })
                .map(VarDetail::getID)
                .collect(Collectors.toSet());

        return getDes(execution, new HashSet<>(inputsAndDes));
    }

    private Set<Integer> getUnmockableInputs(MethodExecution execution) {
        return execution.getParams().stream()
                .map(executionTrace::getVarDetailByID)
                .map(executionTrace::getRelatedObjVarIDs)
                .flatMap(Collection::stream)
                .map(executionTrace::getVarDetailByID)
                .filter(p -> !p.equals(executionTrace.getNullVar()))
                .filter(p -> Helper.isCannotMockType(p.getType()) && (executionTrace.getParentExeStack(p, true) == null || executionTrace.getParentExeStack(p, true).stream().anyMatch(e -> e.getMethodInvoked().getDeclaringClass().getPackageName().startsWith(kwyyeung.autoboost.helper.Properties.getSingleton().getPUT())))) // do not track the var if they are NOT to be mocked
                .map(VarDetail::getID)
                .collect(Collectors.toSet());
    }

    private Set<Integer> getDes(MethodExecution execution, Set<Integer> inputsAndDes) {
        return getDes(execution, inputsAndDes, new HashSet<>());
    }

    /**
     * Get descendants of provided inputs in the provided execution
     * dfs approach
     *
     * @param execution    Method execution under review
     * @param inputsAndDes ids of Set of inputs to investigate
     * @return Set of ids of vardetails found in execution matching criteria
     */
    private Set<Integer> getDes(MethodExecution execution, Set<Integer> inputsAndDes, Set<MethodExecution> covered) {
        if (inputsAndDes.size() == 0 || covered.contains(execution)) return inputsAndDes;
        covered.add(execution);
        executionTrace.getChildren(execution.getID())
                .forEach(cID -> {
                    MethodExecution c = executionTrace.getMethodExecutionByID(cID);
                    if (c.getCalleeId() != -1 && inputsAndDes.contains(c.getCalleeId())) { // only consider callee, if it is param, it either would call sth inside that makes impact / its lib method that would be considered invalid later
                        inputsAndDes.addAll(executionTrace.getRelatedObjVarIDs(c.getResultThisId()));
                        inputsAndDes.addAll(executionTrace.getRelatedObjVarIDs(c.getReturnValId()));
                    }
                    if (c.getCalleeId() == -1) {
                        inputsAndDes.addAll(executionTrace.getRelatedObjVarIDs(c.getReturnValId()));
                    }
                    inputsAndDes.addAll(getDes(c, inputsAndDes, covered));
                });
        return inputsAndDes;
    }

    private boolean canConstructCallee(MethodExecution target) {
        if (target.getCalleeId() == -1) return true;
        VarDetail callee = target.getCallee();
        if (callee instanceof ObjVarDetails) {
            if (callee.getType().isAnonymousClass()) return false;
            if (callee.getType().getName().startsWith("com.sun.proxy.$")) return false;
            if (getExeConstructingClass(callee.getType(), true) == null) return false;
        }
        if (callee instanceof EnumVarDetails) {
            if (!callee.getType().isEnum()) {
                try {
                    if (!callee.getType().getPackage().getName().startsWith(kwyyeung.autoboost.helper.Properties.getSingleton().getPUT()) && !soot.Modifier.isPublic(callee.getType().getField(((EnumVarDetails) callee).getValue()).getModifiers()))
                        return false;
                } catch (NoSuchFieldException e) {
                    return false;
                }
                return !callee.getType().getPackage().getName().startsWith(kwyyeung.autoboost.helper.Properties.getSingleton().getPUT()) || InstrumentResult.getSingleton().getClassPublicFieldsMap().getOrDefault(callee.getType().getName(), new HashSet<>()).contains(((EnumVarDetails) callee).getValue());
            }
        }
        return true;
    }

    private boolean checkAndSetRequiredPackage(MethodExecution target) {
        String requiredPackage = "";
        if (target.getCalleeId() != -1) {
            VarDetail callee = target.getCallee();
            if (callee instanceof ObjVarDetails && getExeConstructingClass(callee.getType(), true) != null) {
                MethodExecution defExe = getExeConstructingClass(callee.getType(), true);
                if (defExe.getMethodInvoked().getAccess().equals(ACCESS.PROTECTED) || !Helper.accessibilityCheck(defExe.getMethodInvoked().getdClass(), ""))
                    requiredPackage = defExe.getMethodInvoked().getdClass().getPackage().getName();

            }
            if (callee instanceof EnumVarDetails) {
                if (!callee.getType().isEnum()) {
                    requiredPackage = Helper.getRequiredPackage(callee.getType());
                }
            }
        } else if (target.getMethodInvoked().getType().equals(METHOD_TYPE.STATIC)) {
            requiredPackage = Helper.getRequiredPackage(target.getMethodInvoked().getdClass());
        }
        if (requiredPackage == null) return false;
        if (target.getMethodInvoked().getAccess().equals(ACCESS.PROTECTED)) {
            if (requiredPackage.isEmpty()) requiredPackage = target.getMethodInvoked().getdClass().getPackage().getName();
            else if (!requiredPackage.equals(target.getMethodInvoked().getdClass().getPackage().getName())) {
                return false;
            }
        }

        target.setRequiredPackage(requiredPackage);
        return true;
    }

    private boolean targetIsAssertable(MethodExecution target) {
        if (target.getReturnValId() == -1) return false;
        VarDetail returnVal = executionTrace.getVarDetailByID(target.getReturnValId());
        return varDetailIsAssertable(target, returnVal);
    }

    private boolean targetIsThrowingException(MethodExecution target) {
        return target.getReturnValId() == -1 && target.getExceptionClass() != null && !target.getExceptionClass().equals(UnrecognizableException.class);
    }

    private boolean isPossibleTarget(MethodExecution execution) {
        MethodDetails details = execution.getMethodInvoked();
        if (instrumentResult.isLibMethod(details.getId())) return false;
        if (execution.getCalleeId() != -1) {
            VarDetail callee = execution.getCallee();
            if (callee.getType().getPackage() == null || !callee.getType().getPackage().getName().startsWith(kwyyeung.autoboost.helper.Properties.getSingleton().getPUT()))
                return false;
        }
        if (executionTrace.containsFaultyDef(execution, true) || executionTrace.getAllMethodExecs().values().stream().anyMatch(e -> e.sameCalleeParamNMethod(execution) && !e.sameContent(execution)))
            return false;
        if (details.getAccess().equals(ACCESS.PRIVATE) || details.getName().startsWith("access$")) return false;
        switch (details.getType()) {
            case STATIC_INITIALIZER:
            case CONSTRUCTOR:
                return false;
            case MEMBER:
                if (Arrays.stream(SKIP_MEMBER_METHODS).anyMatch(s -> details.getName().equals(s))) return false;
                break;
            case STATIC:
                if (Arrays.stream(SKIP_STATIC_METHODS).anyMatch(s -> details.getName().equals(s))) return false;
                break;
        }
        return true;

    }

    private boolean targetIsBestMatch(MethodExecution target) {
        MethodDetails details = target.getMethodInvoked();
        try {
            // check if the method is actually called by subclass callee
            // if yes, they cannot be specified in test case and hence cannot be used as target
            Method method = details.getdClass().getDeclaredMethod(details.getName(), details.getParameterTypes().stream().map(Helper::sootTypeToClass).toArray(Class<?>[]::new));
            if (method.isBridge()) return false;
            if (target.getCalleeId() == -1) return true; // if no callee, no overriding problems
            if (target.getCallee().getType().equals(details.getdClass())) return true;
            VarDetail callee = target.getCallee();
            // prevent incorrect method call
            if (method.getDeclaringClass().isAssignableFrom(callee.getType())) //if callee is subclass
                try {
                    return callee.getType().getDeclaredMethod(method.getName(), method.getParameterTypes()).equals(method);
                } catch (NoSuchMethodException noSuchMethodException) {
                    return true;
                }
        } catch (NoSuchMethodException noSuchMethodException) {
            logger.error(noSuchMethodException.getMessage());
        }
        return true;
    }

    private boolean varDetailIsAssertable(MethodExecution target, VarDetail returnVal) {
        if (returnVal instanceof ObjVarDetails) return returnVal.equals(executionTrace.getNullVar());
        if (returnVal instanceof ArrVarDetails) {
            if (((ArrVarDetails) returnVal).getComponents().size() == 0) return true;
            return StringUtils.countMatches(target.getMethodInvoked().getReturnSootType().toString(), "[]") == StringUtils.countMatches(returnVal.getType().getSimpleName(), "[]") && ((ArrVarDetails) returnVal).getComponents().stream().map(executionTrace::getVarDetailByID).allMatch(v -> this.varDetailIsAssertable(target, v));
        }
        if (returnVal instanceof MapVarDetails) {
            if (((MapVarDetails) returnVal).getKeyValuePairs().size() == 0) return true;
            return ((MapVarDetails) returnVal).getKeyValuePairs().stream().flatMap(kvp -> Stream.of(kvp.getKey(), kvp.getValue())).map(executionTrace::getVarDetailByID).map(VarDetail::getType).allMatch(ClassUtils::isPrimitiveOrWrapper);
        }
        if (returnVal instanceof EnumVarDetails) {
            try {
                if (returnVal.getType().equals(Class.class) && !Helper.accessibilityCheck(ClassUtils.getClass(((EnumVarDetails) returnVal).getValue()), target.getRequiredPackage()))
                    return false;
                if (returnVal.getType().isEnum())
                    return Helper.accessibilityCheck(returnVal.getType(), target.getRequiredPackage());
                if (returnVal.getType().getPackage().getName().startsWith(Properties.getSingleton().getPUT()))
                    return instrumentResult.getClassPublicFieldsMap().getOrDefault(returnVal.getType().getName(), new HashSet<>()).contains(((EnumVarDetails) returnVal).getValue());
                else
                    return Modifier.isPublic(returnVal.getType().getField(((EnumVarDetails) returnVal).getValue()).getModifiers());
            } catch (ClassNotFoundException | NoSuchFieldException e) {
                return false;
            }
        }

        return true;
    }

    protected MethodExecution getExeConstructingClass(Class<?> creatingClass, Set<Class> processing, boolean cache) {
        MethodExecution defExe;
        PROGRAM_STATE oldProgramState = AutoBoost.getCurrentProgramState();
        AutoBoost.setCurrentProgramState(PROGRAM_STATE.CONSTRUCTOR_SEARCH);
        if (!this.classToDefExeMap.containsKey(creatingClass)) {
            Comparator constructionPriority = Comparator.comparingInt(e -> ((MethodExecution) e).getMethodInvoked().getAccess().getPerferenceLv()).thenComparingDouble(o -> {
                List<VarDetail> params = ((MethodExecution) o).getParams().stream().map(executionTrace::getVarDetailByID).collect(Collectors.toList());
                return params.size() == 0 ? 1 : params.stream().filter(p -> p instanceof EnumVarDetails || p instanceof PrimitiveVarDetails || p instanceof StringBVarDetails || p instanceof StringVarDetails || p instanceof WrapperVarDetails).count() / params.size();
            });
            if (processing.contains(creatingClass)) return null;
            processing.add(creatingClass);
            Set<Constructor> tried = new HashSet<>();
//            if (creatingClass.getName().startsWith(Properties.getSingleton().getPUT()))
            executionTrace.getAllMethodExecs().values().stream().filter(ex -> canUseForConstructing(creatingClass, ex) && canReenact(ex)).forEach(e -> {
                MethodDetails md = e.getMethodInvoked();
                if (md.getType().equals(METHOD_TYPE.CONSTRUCTOR)) {
                    try {
                        Constructor toCall = md.getdClass().getDeclaredConstructor(md.getParameterTypes().stream().map(Helper::sootTypeToClass).toArray(Class[]::new));
                        tried.add(toCall);
                        if (md.getAccess().equals(ACCESS.PRIVATE)) return;
                        toCall.setAccessible(true);
                        toCall.newInstance(e.getParams().stream().map(executionTrace::getVarDetailByID).map(this::getRecreatedParam).toArray(Object[]::new)).getClass();
                    } catch (NoSuchMethodException | InstantiationException | IllegalAccessException |
                             InvocationTargetException | SecurityException ignored) {
                    }
                }
                if (md.getType().equals(METHOD_TYPE.STATIC) && md.getParameterCount() == 0) {
                    try {
                        Method method = md.getdClass().getDeclaredMethod(md.getName());
                        if (soot.Modifier.isPrivate(method.getModifiers())) return;
                        method.setAccessible(true);
                        method.invoke(null);
                    } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {

                    }
                }
            });

            defExe = (MethodExecution) executionTrace.getConstructingMethodExes().getOrDefault(creatingClass, new HashSet<>()).stream().filter(c -> canUseForConstructing(creatingClass, c)).sorted(constructionPriority).findFirst().orElse(null);
            if (defExe == null) {
                Arrays.stream(creatingClass.getConstructors()).forEach(c -> {
                    try {
                        c.setAccessible(true);
                        c.newInstance(Arrays.stream(c.getParameterTypes()).map(ty -> this.getDefaultParams(ty, processing)).toArray());
                    } catch (InstantiationException | IllegalAccessException | InvocationTargetException ignored) {
                    }
                });
                defExe = (MethodExecution) executionTrace.getConstructingMethodExes().getOrDefault(creatingClass, new HashSet<>()).stream().filter(c -> canUseForConstructing(creatingClass, c)).sorted(constructionPriority).findFirst().orElse(null);
            }
            if (defExe == null) {
                Arrays.stream(creatingClass.getDeclaredMethods()).filter(m -> Modifier.isStatic(m.getModifiers()) && m.getParameterCount() == 0 && m.getReturnType().equals(creatingClass) && Modifier.isPublic(m.getModifiers())).forEach(m -> {
                    try {
                        m.invoke(null);
                    } catch (IllegalAccessException | InvocationTargetException ignored) {
                    }
                });
                defExe = (MethodExecution) executionTrace.getConstructingMethodExes().getOrDefault(creatingClass, new HashSet<>()).stream().filter(c -> canUseForConstructing(creatingClass, c)).sorted(constructionPriority).findFirst().orElse(null);

            }

            if (cache)
                this.classToDefExeMap.put(creatingClass, defExe);
        } else defExe = this.classToDefExeMap.get(creatingClass);
        processing.remove(creatingClass);

        AutoBoost.setCurrentProgramState(oldProgramState);
        return defExe;
    }

    protected MethodExecution getExeConstructingClass(Class<?> creatingClass, boolean cache) {
        return getExeConstructingClass(creatingClass, new HashSet<>(), cache);
    }

    private Object getRecreatedParam(VarDetail varDetail) {
        if (varDetail.equals(executionTrace.getNullVar())) return null;
        if (varDetail instanceof PrimitiveVarDetails) return varDetail.getValue();
        if (varDetail instanceof StringVarDetails) return ((StringVarDetails) varDetail).getValue();
        if (varDetail instanceof StringBVarDetails)
            return ((StringVarDetails) executionTrace.getVarDetailByID(((StringBVarDetails) varDetail).getStringValID())).getValue();
        if (varDetail instanceof WrapperVarDetails) return varDetail.getValue();
        if (varDetail instanceof ArrVarDetails) {
            Stream values = ((ArrVarDetails) varDetail).getComponents().stream().map(executionTrace::getVarDetailByID).map(this::getRecreatedParam);
            if (varDetail.getType().isArray())
                return values.toArray(i -> Array.newInstance(varDetail.getType().getComponentType(), i));
            if (Set.class.isAssignableFrom(varDetail.getType()))
                return values.collect(Collectors.toSet());
            if (List.class.isAssignableFrom(varDetail.getType()))
                return values.collect(Collectors.toList());
        }
        if (varDetail instanceof MapVarDetails)
            return ((MapVarDetails) varDetail).getKeyValuePairs().stream().map(e -> new AbstractMap.SimpleEntry(this.getRecreatedParam(executionTrace.getVarDetailByID(e.getKey())), this.getRecreatedParam(executionTrace.getVarDetailByID(e.getValue())))).collect(Collectors.toMap(AbstractMap.SimpleEntry::getKey, AbstractMap.SimpleEntry::getValue));
        if (varDetail instanceof EnumVarDetails) {
            if (varDetail.getType().isEnum()) {
                return Enum.valueOf((Class) varDetail.getType(), ((EnumVarDetails) varDetail).getValue());
            }
            if (varDetail.getType().equals(Class.class)) {
                try {
                    return ClassUtils.getClass(((EnumVarDetails) varDetail).getValue());
                } catch (ClassNotFoundException ignored) {
                    logger.error(((EnumVarDetails) varDetail).getValue() + " class not found ");
                }
            } else {
                try {
                    Field field = varDetail.getType().getField(((EnumVarDetails) varDetail).getValue());
                    field.setAccessible(true);
                    if (!soot.Modifier.isStatic(field.getModifiers())) return null;
                    return field.get(null);
                } catch (Exception ignored) {
                    logger.error(((EnumVarDetails) varDetail).getValue() + " field not found ");
                }
            }
        }
        return null;
    }

    public Object getDefaultParams(Class<?> paramType, Set<Class> processing) {
        Object res = Helper.getDefaultValue(paramType);
        if (res != null) return res;
        if (paramType.equals(String.class)) return "";
        MethodExecution defExe = this.classToDefExeMap.get(paramType);
        if (defExe != null) {
            try {
                if (defExe.getMethodInvoked().getType().equals(METHOD_TYPE.CONSTRUCTOR))
                    return defExe.getMethodInvoked().getdClass().getDeclaredConstructor(defExe.getMethodInvoked().getParameterTypes().stream().map(Helper::sootTypeToClass).toArray(Class[]::new)).newInstance(defExe.getParams().stream().map(executionTrace::getVarDetailByID).map(this::getRecreatedParam).toArray(Object[]::new));
                else
                    return defExe.getMethodInvoked().getdClass().getDeclaredMethod(defExe.getMethodInvoked().getName()).invoke(null);
            } catch (InstantiationException | NoSuchMethodException | IllegalAccessException |
                     InvocationTargetException ignored) {
                if (soot.Modifier.isFinal(paramType.getModifiers())) return null;
            }
        }
        if (Helper.isCannotMockType(paramType) || soot.Modifier.isFinal(paramType.getModifiers())) return null;
//        if (!this.classToDefExeMap.containsKey(paramType) && processing.contains(paramType)) return Mockito.mock(paramType);
//        if (!this.classToDefExeMap.containsKey(paramType) && !processing.contains(paramType)) getExeConstructingClass(paramType, processing, true);
        return Mockito.mock(paramType);
    }

    private boolean canUseForConstructing(Class<?> creatingClass, MethodExecution methodExecution) {
        MethodDetails methodDetails = methodExecution.getMethodInvoked();
        return !soot.Modifier.isPrivate(methodDetails.getdClass().getModifiers())
                && (methodDetails.getType().equals(METHOD_TYPE.CONSTRUCTOR) && methodExecution.getResultThisId() != -1 && executionTrace.getVarDetailByID(methodExecution.getResultThisId()).getType().equals(creatingClass) || (methodDetails.getType().equals(METHOD_TYPE.STATIC) && methodExecution.getReturnValId() != -1 && executionTrace.getVarDetailByID(methodExecution.getReturnValId()).getType().equals(creatingClass) && methodDetails.getParameterCount() == 0))
                 && !methodDetails.getAccess().equals(ACCESS.PRIVATE)
                && !executionTrace.containsFaultyDef(methodExecution, true);
    }

    private boolean canReenact(MethodExecution methodExecution) {
        MethodDetails methodDetails = methodExecution.getMethodInvoked();
        if (methodDetails.isFieldAccess()) return false;
        try {
            if (!methodDetails.getName().equals("<clinit>") && !methodDetails.getName().equals("<init>"))
                methodDetails.getdClass().getDeclaredMethod(methodDetails.getName(), methodDetails.getParameterTypes().stream().map(Helper::sootTypeToClass).toArray(Class[]::new));
        } catch (NoSuchMethodException e) {
            return false;
        }
        return methodExecution.getParams().stream()
                .map(executionTrace::getVarDetailByID)
                .flatMap(v -> executionTrace.getAllObjVarInvolved(v).stream()).
                allMatch(v -> {
                    if (Helper.isCannotMockType(v.getType()))
                        return (!executionTrace.hasUsageAsCallee(methodExecution, Collections.singleton(v.getID())) && !executionTrace.hasFieldAccess(methodExecution, v) && Arrays.stream(v.getType().getConstructors()).anyMatch(c -> Arrays.stream(c.getParameterTypes()).allMatch(pt -> ClassUtils.isPrimitiveOrWrapper(pt) || pt.equals(String.class)) && soot.Modifier.isPublic(c.getModifiers())))
                                || (executionTrace.getUnmockableVarToDefMap().getOrDefault(v.getID(), null) != null && !executionTrace.containsFaultyDef(executionTrace.getMethodExecutionByID(executionTrace.getUnmockableVarToDefMap().get(v.getID())), true));
                    return v.equals(executionTrace.getNullVar());
                });
    }

    public boolean checkRecreationResult(TestCase testCase, MethodExecution target) {
        if (!testCase.isRecreated() || target.getReturnValId() == -1) return false;
        Object actual = testCase.getObjForVar(target.getReturnValId());
        Object expected = ExecutionChecker.getStandaloneObj(executionTrace.getVarDetailByID(target.getReturnValId()));
        if (expected == null && actual != null) return false;
        if (expected == actual) return true;
        return expected.equals(actual);
    }

    public boolean checkExceptionResult(TestCase testCase, MethodExecution target, Object callee, Object[] params) {
        if(!testCase.isRecreated() || target.getExceptionClass() == null) return false;
        MethodDetails toInvoke = target.getMethodInvoked();
        if (toInvoke.getType() == METHOD_TYPE.CONSTRUCTOR) {
            try {
                Constructor constructor = toInvoke.getdClass().getDeclaredConstructor(toInvoke.getParameterTypes().stream().map(Helper::sootTypeToClass).toArray(Class[]::new));
                constructor.setAccessible(true);
                constructor.newInstance(params);
            }catch (InvocationTargetException e) {
                return e.getCause().getClass().equals(target.getExceptionClass());
            } catch (NoSuchMethodException | InstantiationException | IllegalAccessException e) {
                return false;
            }
        } else {
            try {
                Method method = toInvoke.getdClass().getDeclaredMethod(toInvoke.getName(), toInvoke.getParameterTypes().stream().map(Helper::sootTypeToClass).toArray(Class[]::new));
                method.setAccessible(true);
                method.invoke(callee, params);
            } catch (InvocationTargetException e) {
                return e.getCause().getClass().equals(target.getExceptionClass());
            }catch (NoSuchMethodException | IllegalAccessException e) {
                return false;
            }
        }
        return false;
    }



}
