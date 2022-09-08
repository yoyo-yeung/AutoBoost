package program.execution;

import entity.ACCESS;
import entity.LOG_ITEM;
import entity.METHOD_TYPE;
import helper.Properties;
import org.apache.commons.lang3.ClassUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DirectedMultigraph;
import program.analysis.ClassDetails;
import program.analysis.MethodDetails;
import program.execution.variable.*;
import program.instrumentation.InstrumentResult;
import soot.Modifier;

import java.io.Serializable;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static helper.Helper.accessibilityCheck;
import static helper.Helper.getRequiredPackage;

public class ExecutionTrace {
    private static final Logger logger = LogManager.getLogger(ExecutionTrace.class);
    private static final ExecutionTrace singleton = new ExecutionTrace();
    private static final AtomicInteger exeIDGenerator = new AtomicInteger(0);
    private static final AtomicInteger varIDGenerator = new AtomicInteger(1);
    private final Map<Integer, MethodExecution> allMethodExecs;
    private final Map<Integer, VarDetail> allVars; // store all vardetail used, needed for lookups
    private final Map<Integer, Integer> varToDefMap;
    private final DirectedMultigraph<Integer, CallOrderEdge> callGraph;
    private final VarDetail nullVar;
    private final Map<VarDetail, Stack<MethodExecution>> varToParentStackCache = new HashMap<>(); // cache last retrieval results to save time

    /**
     * Constructor of ExecutionTrace, set up all vars.
     */
    public ExecutionTrace() {
        this.allMethodExecs = new ConcurrentHashMap<>();
        this.allVars = new ConcurrentHashMap<>();
        this.varToDefMap = new HashMap<Integer, Integer>();
        callGraph = new DirectedMultigraph<>(CallOrderEdge.class);
        nullVar = new ObjVarDetails(0, Object.class, "null");
        this.allVars.put(nullVar.getID(), nullVar);
    }

    /**
     * @return singleton of class, used across classes of program
     */
    public static ExecutionTrace getSingleton() {
        return singleton;
    }


    /**
     * @return all method executions stored, ID -> MethodExecution
     */
    public Map<Integer, MethodExecution> getAllMethodExecs() {
        return allMethodExecs;
    }


    /**
     * @return All Variables and their details stored, ID -> VarDetail
     */
    public Map<Integer, VarDetail> getAllVars() {
        return allVars;
    }


    /**
     * List all VarDetail ID and MethodExecution ID where the VarDetail is defined
     *
     * @return Map between a VarDetail ID and MethodExecution ID
     */
    public Map<Integer, Integer> getVarToDefMap() {
        return varToDefMap;
    }

    /**
     * Find method execution where the provided VarDetail is defined
     *
     * @param varID
     * @return ID of MethodExecution
     */
    public Integer getDefExeList(Integer varID) {
        return this.varToDefMap.getOrDefault(varID, null);
    }


    /**
     * @return new ID for MethodExecution
     */
    public int getNewExeID() {
        return exeIDGenerator.incrementAndGet();
    }

    /**
     * @return new ID for VarDetail
     */
    public int getNewVarID() {
        return varIDGenerator.incrementAndGet();
    }

    /**
     * If object already stored, return existing VarDetail ID stored
     * If not, create a new VarDetail and return the corresponding ID
     *
     * @param execution
     * @param type       type of the object to be stored
     * @param objValue   object to be stored
     * @param process    the current process, e.g. logging callee? param? or return value?
     * @param subElement
     * @return VarDetail storing the provided object
     */
    public VarDetail getVarDetail(MethodExecution execution, Class<?> type, Object objValue, LOG_ITEM process, boolean subElement) {
        boolean artificialEnum = false;
        if (objValue == null) return nullVar;
        if (type.isEnum()) {
            objValue = ((Enum) objValue).name();
        } else if (!type.isArray() && !type.equals(String.class) && !ClassUtils.isPrimitiveOrWrapper(type) && !((ArrVarDetails.availableTypeCheck(type) && ArrVarDetails.availableTypeCheck(objValue.getClass())) || (MapVarDetails.availableTypeCheck(type) && MapVarDetails.availableTypeCheck(objValue.getClass())))) {
            Object finalObjValue1 = objValue;

            Class<?> finalType = type;
            Optional<String> match = Arrays.stream(type.getDeclaredFields()).filter(f -> f.getType().equals(finalType)).filter(f -> {
                try {
                    return (!InstrumentResult.getSingleton().getClassPublicFieldsMap().containsKey(finalType.getName()) && Modifier.isPublic(finalType.getField(f.getName()).getModifiers())) || InstrumentResult.getSingleton().getClassPublicFieldsMap().get(finalType.getName()).contains(f.getName());
                } catch (NoSuchFieldException e) {
                    return false;
                }
            }).filter(f -> {
                try {
                    Object val = f.get(finalObjValue1);
                    return val != null && System.identityHashCode(val) == System.identityHashCode(finalObjValue1);
                } catch (IllegalAccessException e) {
                    return false;
                }
            }).map(Field::getName).findAny();

            if (match.isPresent()) {
                artificialEnum = true;
                objValue = match.get();
            } else if (type.equals(Class.class)) {
                artificialEnum = true;
                if (((Class) objValue).isArray()) objValue = Array.class;
                objValue = ((Class) objValue).getName();
            } else objValue = toStringWithAttr(objValue);
        } else if (ClassUtils.isPrimitiveOrWrapper(type)) {
            if ((type.equals(double.class) || type.equals(Double.class))) {
                if (Double.isNaN(((Double) objValue))) {
                    artificialEnum = true;
                    type = Double.class;
                    objValue = "NaN";
                } else if (Double.POSITIVE_INFINITY == ((Double) objValue)) {
                    artificialEnum = true;
                    type = Double.class;
                    objValue = "POSITIVE_INFINITY";
                } else if (Double.NEGATIVE_INFINITY == ((Double) objValue)) {
                    artificialEnum = true;
                    type = Double.class;
                    objValue = "NEGATIVE_INFINITY";
                }
            }
            if (type.equals(Integer.class) || type.equals(int.class)) {
                if (Integer.MAX_VALUE == ((Integer) objValue)) {
                    artificialEnum = true;
                    type = Integer.class;
                    objValue = "MAX_VALUE";
                } else if (Integer.MIN_VALUE == ((Integer) objValue)) {
                    artificialEnum = true;
                    type = Integer.class;
                    objValue = "MIN_VALUE";
                }
            }
        }
        VarDetail varDetail = findExistingVarDetail(execution, type, objValue, process, artificialEnum);
        if (varDetail == null) {
            if (type.isEnum() || artificialEnum)
                varDetail = new EnumVarDetails(getNewVarID(), type, (String) objValue);
            else if (type.isPrimitive()) {
                try {
                    varDetail = new PrimitiveVarDetails(getNewVarID(), type, objValue);
                } catch (NoSuchFieldException | IllegalAccessException e) {
                    logger.error("Error when trying to create PrimitiveVarDetails");
                    varDetail = null;
                }
            } else if (type.equals(String.class))
                varDetail = new StringVarDetails(getNewVarID(), (String) objValue);
            else if (StringBVarDetails.availableTypeCheck(type))
                varDetail = new StringBVarDetails(getNewVarID(), type, getVarDetail(execution, String.class, objValue.toString(), process, true).getID());
            else if (ArrVarDetails.availableTypeCheck(type) && ArrVarDetails.availableTypeCheck(objValue.getClass())) {
                varDetail = new ArrVarDetails(getNewVarID(), getComponentStream(execution, type, objValue, process).collect(Collectors.toList()), objValue);
            } else if (MapVarDetails.availableTypeCheck(type) && MapVarDetails.availableTypeCheck(objValue.getClass())) {
                varDetail = new MapVarDetails(getNewVarID(), type, ((Map<?, ?>) objValue).entrySet().stream()
                        .map(e -> new AbstractMap.SimpleEntry<Integer, Integer>(getVarDetail(execution, getClassOfObj(e.getKey()), e.getKey(), process, true).getID(), getVarDetail(execution, getClassOfObj(e.getValue()), e.getValue(), process, true).getID()))
                        .collect(Collectors.<Map.Entry<Integer, Integer>>toSet()), objValue);
            } else if (ClassUtils.isPrimitiveWrapper(type)) {
                varDetail = new WrapperVarDetails(getNewVarID(), type, objValue);
            } else {
                // other cases
                varDetail = new ObjVarDetails(getNewVarID(), type, (String) objValue);
            }
            assert varDetail != null; // if null, then fail to generate test
            addNewVarDetail(varDetail);
            if (!subElement) {
                if (setCurrentExeAsDef(varDetail, process, execution)) addNewVarDetailDef(varDetail, execution.getID());
                else addVarDetailUsage(varDetail, execution.getID());


            }
        } else {
            if (!subElement) {
                if (setCurrentExeAsDef(varDetail, process, execution))
                    addNewVarDetailDef(varDetail, execution.getID());
                else
                    addVarDetailUsage(varDetail, execution.getID());
            }
        }
        return varDetail;
    }

    /**
     * Find if the current execution should be recognized as def of a var
     * e.g. If the variable is a callee, then it is a USE.
     *
     * @param varDetail the current Variable Details under review
     * @param process   the current logging step
     * @param execution the execution where the variable is used
     * @return if this occurance should be stored as def
     */
    private boolean setCurrentExeAsDef(VarDetail varDetail, LOG_ITEM process, MethodExecution execution) {
        if (!(varDetail instanceof ObjVarDetails)) return false; // no need to get a def if it is not an object
        // must be a resulting var
        switch (process) {
            case RETURN_ITEM:
            case RETURN_THIS:
                break;
            default:
                return false;
        }
        MethodDetails methodDetails = execution.getMethodInvoked();
        if ((methodDetails.getType().equals(METHOD_TYPE.MEMBER) && execution.getCallee().equals(varDetail)) || execution.getParams().stream().anyMatch(p -> p == varDetail.getID()))
            return false; // if they were inputs to begin with
        if (methodDetails.getAccess().equals(ACCESS.PRIVATE) || methodDetails.isFieldAccess())
            return false;
        if(methodDetails.getType().equals(METHOD_TYPE.MEMBER) && (getParentExeStack(execution.getCallee(), false) == null || getParentExeStack(execution.getCallee(), false).contains(execution)))
            return false;
        MethodExecution existingDef = getDefExeList(varDetail.getID()) == null ? null : getMethodExecutionByID(getDefExeList(varDetail.getID()));
        if (existingDef == null) return true;
        if (existingDef.sameCalleeParamNMethod(execution)) return false; // would compare method, callee, params
        MethodDetails existingDefDetails = existingDef.getMethodInvoked();
        if (existingDefDetails.getType().getRank() < methodDetails.getType().getRank()) return false;
        if (methodDetails.getType().equals(METHOD_TYPE.MEMBER) && getParentExeStack(execution.getCallee(), false) == null)
            return false;
        return Stream.of(execution, existingDef)
                .map(e -> new AbstractMap.SimpleEntry<MethodExecution, Integer>(e,
                        (e.getMethodInvoked().getType().equals(METHOD_TYPE.MEMBER) && e.getResultThisId() == varDetail.getID() ? -1000 : 0) + e.getMethodInvoked().getParameterCount() + e.getParams().stream().map(this::getVarDetailByID).filter(p -> p.getType().isAnonymousClass() ).mapToInt(i->1000).sum()))
                .min(Comparator.comparingInt(AbstractMap.SimpleEntry::getValue))
                .get().getKey().equals(execution);
    }


    /**
     * Used when the variable storing is of array/Collection type.
     * Retrieve Stream of VarDetail IDs of components in the array/Collection
     *
     * @param execution
     * @param type      the type of variable (Array/subclasses of Collection/etc)
     * @param obj       the variable
     * @param process   the current logging step
     * @return Stream of VarDetail IDs
     */
    private Stream<Integer> getComponentStream(MethodExecution execution, Class<?> type, Object obj, LOG_ITEM process) {
        if (!ArrVarDetails.availableTypeCheck(type))
            throw new IllegalArgumentException("Provided Obj cannot be handled.");
        Stream<Integer> componentStream;
        if (type.isArray()) {
            componentStream = IntStream.range(0, Array.getLength(obj)).mapToObj(i -> getVarDetail(execution, type.getComponentType(), Array.get(obj, i), process, true).getID());
        } else
            componentStream = ((Collection) obj).stream().map(v -> getVarDetail(execution, getClassOfObj(v), v, process, true).getID());
        if (Set.class.isAssignableFrom(type))
            componentStream = componentStream.sorted();
        return componentStream;
    }

    /**
     * If the obj was defined and stored before, return ID of the corresponding ObjVarDetails for reuse. Else return -1
     *
     * @param execution
     * @param objValue
     * @param process
     * @param artificialEnum
     * @return ObjVarDetails if the obj was defined and stored before, null if not
     */
    private VarDetail findExistingVarDetail(MethodExecution execution, Class<?> type, Object objValue, LOG_ITEM process, boolean artificialEnum) {
        Class<?> varDetailType = null;
//        logger.debug("findExisting ");
        if (type.isEnum() || artificialEnum) {
            if (type.equals(Class.class))
                objValue = objValue.toString().replace("$", ".") + ".class";
            varDetailType = EnumVarDetails.class;
        } else if (ArrVarDetails.availableTypeCheck(type) && ArrVarDetails.availableTypeCheck(objValue.getClass())) {
            objValue = getComponentStream(execution, type, objValue, process).map(String::valueOf).collect(Collectors.joining(Properties.getDELIMITER()));
            varDetailType = ArrVarDetails.class;
        } else if (MapVarDetails.availableTypeCheck(type) && MapVarDetails.availableTypeCheck(objValue.getClass())) {
            objValue = ((Map<?, ?>) objValue).entrySet().stream().map(comp -> getVarDetail(execution, getClassOfObj(comp.getKey()), comp.getKey(), process, true).getID() + "=" + getVarDetail(execution, getClassOfObj(comp.getValue()), comp.getValue(), process, true).getID()).sorted().collect(Collectors.joining(Properties.getDELIMITER()));
            varDetailType = MapVarDetails.class;
        }
        // high chance of having the same value
//        if (process.equals(LOG_ITEM.RETURN_THIS) && ExecutionLogger.getLatestExecution().getCalleeId() != -1) {
//            VarDetail calleeDetails = getVarDetailByID(ExecutionLogger.getLatestExecution().getCalleeId());
//            if (calleeDetails.sameValue(type, objValue))
//                return calleeDetails.getID();
//        }
        Object finalObjValue1 = objValue;
        Class<?> finalVarDetailType = varDetailType;
        Optional<VarDetail> result;
        result = this.allVars.values().stream()
                .filter(Objects::nonNull)
                .filter(v -> {
                    if (finalVarDetailType != null) return finalVarDetailType.isInstance(v);
                    else if (type.isPrimitive()) return v instanceof PrimitiveVarDetails;
                    else if (type.equals(String.class)) return v instanceof StringVarDetails;
                    else if (StringBVarDetails.availableTypeCheck(type)) return v instanceof StringBVarDetails;
                    else if (ClassUtils.isPrimitiveOrWrapper(type)) return v instanceof WrapperVarDetails;
                    else return v instanceof ObjVarDetails;
                })
                .filter(v -> v.sameValue(type, finalObjValue1))

                .findAny();
        return result.orElse(null);
    }

    private Class<?> getClassOfObj(Object obj) {
        if (obj != null) return obj.getClass();
        else return Object.class;
    }

    /**
     * Set up all maps using VarDetail key as ID
     *
     * @param varID key of VarDetail
     */
    private void setUpVarMap(int varID) {
        if (!this.varToDefMap.containsKey(varID))
            this.varToDefMap.put(varID, null);
    }

    public void addNewVarDetail(VarDetail detail) {
        if (!this.allVars.containsKey(detail.getID()))
            this.allVars.put(detail.getID(), detail);
    }

    /**
     * Add a newly created VarDetail to the collection
     * Add def-relationship between method execution and VarDetail
     *
     * @param detail      Newly created VarDetail to document
     * @param executionID ID of MethodExecution that define a new VarDetail
     */
    public void addNewVarDetailDef(VarDetail detail, int executionID) {
        this.setUpVarMap(detail.getID());
        // only store it as a def if it is an object (need construction)
        if (detail instanceof ObjVarDetails)
            this.varToDefMap.put(detail.getID(), executionID);
    }

    /**
     * Add record of a MethodExecution (with ID executionID) using a particular VarDetail (with ID detailID)
     *
     * @param detail      ID of existing VarDetail
     * @param executionID ID of a method execution
     */
    public void addVarDetailUsage(VarDetail detail, int executionID) {
        this.setUpVarMap(detail.getID());
    }

    public void addMethodExecution(MethodExecution execution) {
        int executionID = execution.getID();
        this.callGraph.addVertex(executionID); // add vertex even if it has no son/ father
    }

    public void updateFinishedMethodExecution(MethodExecution execution) {
        int executionID = execution.getID();
        this.allMethodExecs.put(executionID, execution);
    }

    public void addMethodRelationship(int father, int son, int exeOrder) {
        this.callGraph.addVertex(father);
        this.callGraph.addVertex(son);
        this.callGraph.addEdge(father, son, new CallOrderEdge(exeOrder));
    }


    public void changeVertex(int original, int now) {
        if (!this.callGraph.containsVertex(original)) return;
        this.callGraph.addVertex(now);
        this.callGraph.removeAllEdges(original, now);
        this.callGraph.removeAllEdges(now, original);
        this.callGraph.outgoingEdgesOf(original).forEach(e -> addMethodRelationship(now, this.callGraph.getEdgeTarget(e), e.getLabel()));
        this.callGraph.incomingEdgesOf(original).forEach(e -> addMethodRelationship(this.callGraph.getEdgeSource(e), now, e.getLabel()));
        this.callGraph.removeVertex(original);
    }

    public void removeVertex(int vertex) {
        if (!this.callGraph.containsVertex(vertex)) return;
        this.callGraph.removeVertex(vertex);
    }

    public List<Integer> getChildren(int father) {
        List<Integer> results = new ArrayList<>();
        this.callGraph.outgoingEdgesOf(father).stream().filter(e -> this.callGraph.getEdgeTarget(e) != father).sorted(Comparator.comparingInt(CallOrderEdge::getLabel)).forEach(e -> {
            results.add(this.callGraph.getEdgeTarget(e));
        });
        return results;
    }

    public VarDetail getVarDetailByID(int varID) {
        if (!this.allVars.containsKey(varID))
            throw new IllegalArgumentException("VarDetail with ID" + varID + " does not exist.");
        return this.allVars.get(varID);
    }

    public MethodExecution getMethodExecutionByID(int exeID) {
        if (this.allMethodExecs.containsKey(exeID)) return this.allMethodExecs.get(exeID);
        Optional<MethodExecution> result = ExecutionLogger.getAllExecuting().stream().filter(e -> e.getID() == exeID).findAny();
        if (result.isPresent()) return result.get();
        else throw new IllegalArgumentException("MethodExecution with ID " + exeID + " does not exist");
    }

    public VarDetail getNullVar() {
        return nullVar;
    }

    private String toStringWithAttr(Object obj) {
        if (obj == null) return "null";
        StringBuilder result = new StringBuilder(1000);
        toCustomString("this", obj, 7, result, new HashMap<>());
        result.trimToSize();
        return result.toString();
    }

    private void toCustomString(String fieldName, Object obj, int depth, StringBuilder result, Map<Integer, String> hashCodeToFieldMap) {
        if (obj == null) {
            result.append(nullVar.getID());
            return;
        } else if (ClassUtils.isPrimitiveOrWrapper(obj.getClass()) || obj.getClass().equals(String.class)) {
            if (obj.getClass().equals(String.class) && ((String) obj).length() > 100)
                result.append("ID:").append(getVarDetail(null, obj.getClass(), obj, null, true).getID());
            else
                result.append(obj);
            return;
        }
        if (hashCodeToFieldMap.containsKey(System.identityHashCode(obj))) {
            result.append(hashCodeToFieldMap.get(System.identityHashCode(obj)));
            return;
        } else hashCodeToFieldMap.put(System.identityHashCode(obj), fieldName);
        if (depth == 0) {
            result.append("[]");
            return;
        } else if (obj.getClass().isArray()) {
            result.append("[");
            IntStream.range(0, Array.getLength(obj)).forEach(i -> {
                if (Array.get(obj, i) == null) result.append("null");
                else toCustomString(fieldName + "." + i, Array.get(obj, i), depth - 1, result, hashCodeToFieldMap);
                if (i < Array.getLength(obj) - 1) result.append(",");
            });
            result.append("]");
            return;
        } else if (MapVarDetails.availableTypeCheck(obj.getClass())) {

            result.append("{");
            AtomicInteger i = new AtomicInteger();
            ((Map<?, ?>) obj).forEach((key, value) -> {
                result.append("[");
                toCustomString(fieldName + "." + i.get() + ".key", key, depth - 1, result, hashCodeToFieldMap);
                result.append("=");
                toCustomString(fieldName + "." + i.getAndIncrement() + ".value", value, depth - 1, result, hashCodeToFieldMap);
                result.append("]");
            });
            result.append("}");
            return;
        } else if (obj instanceof Collection && obj.getClass().getName().startsWith("java.")) {
            result.append("{");
            AtomicInteger y = new AtomicInteger();
            ((Collection) obj).forEach(i -> {
                toCustomString(fieldName + "." + y.getAndIncrement(), i, depth - 1, result, hashCodeToFieldMap);
                result.append(",");
            });
            result.deleteCharAt(result.length() - 1);
            result.append("}");

        }

        if (!InstrumentResult.getSingleton().getClassDetailsMap().containsKey(obj.getClass().getName())) {
            Class<?> CUC = obj.getClass();
            List<Class> classesToGetFields = new ArrayList<>(ClassUtils.getAllSuperclasses(CUC));
            classesToGetFields.add(CUC);
            classesToGetFields.removeIf(c -> c.equals(Object.class) || c.equals(Serializable.class) || c.equals(Field.class) || c.equals(Class.class));
            InstrumentResult.getSingleton().addClassDetails(new ClassDetails(CUC.getName(), classesToGetFields.stream()
                    .flatMap(c -> Arrays.stream(c.getDeclaredFields()))
                    .collect(Collectors.toList())));
        }
        result.append("{");
        InstrumentResult.getSingleton().getClassDetailsByID(obj.getClass().getName()).getClassFields()
                .forEach(f -> {
                    f.setAccessible(true);
                    try {
                        result.append(f.getName()).append("=");
                        toCustomString(fieldName + "." + f.getName(), f.get(obj), depth - 1, result, hashCodeToFieldMap);
                        result.append(",");
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    }
                });
        result.deleteCharAt(result.length() - 1);
        result.append("}");
    }

    public void clear() {
        allMethodExecs.clear();
        varToDefMap.clear();
    }

    public void replacePossibleDefExe(MethodExecution original, MethodExecution repl) {
        Set<Integer> varInvolved = new HashSet<>(original.getParams());
        varInvolved.add(original.getCalleeId());
        varInvolved.add(original.getReturnValId());
        varInvolved.add(original.getResultThisId());
        if (original.getReturnValId() != -1) {
            VarDetail returnVarDetail = getVarDetailByID(original.getReturnValId());
            if (returnVarDetail instanceof ArrVarDetails)
                varInvolved.addAll(((ArrVarDetails) returnVarDetail).getComponents());
            if (returnVarDetail instanceof MapVarDetails)
                varInvolved.addAll(((MapVarDetails) returnVarDetail).getKeyValuePairs().stream().flatMap(e -> Stream.of(e.getKey(), e.getValue())).collect(Collectors.toList()));
        }
        varInvolved.remove(-1);
        varInvolved.removeIf(v -> !varToDefMap.containsKey(v) || varToDefMap.get(v) == null || varToDefMap.get(v) != original.getID());
        varInvolved.forEach(v -> varToDefMap.replace(v, original.getID(), repl.getID()));

    }

    public DirectedMultigraph<Integer, CallOrderEdge> getCallGraph() {
        return callGraph;
    }

    /**
     * Update canTest field in each execution storing if the execution can be tested
     */
    public void checkTestabilityOfExecutions() {
        logger.info("Checking if executions can be tested ");
        Queue<MethodExecution> executionQueue = this.allMethodExecs.values().stream().sorted(Comparator.comparingInt(MethodExecution::getID)).collect(Collectors.toCollection(LinkedList::new));
        Set<Integer> checked = new HashSet<>();
        Map<Integer, Set<Integer>> exeToInputVarsMap = new HashMap<>();
        while (!executionQueue.isEmpty()) {
            MethodExecution execution = executionQueue.poll();
            if(execution.getCalleeId()!=-1 && getParentExeStack(execution.getCallee(), true)==null) {
                execution.setCanTest(false);
                checked.add(execution.getID());
                continue;
            }
            if (execution.getCalleeId() != -1 && getDefExeList(execution.getCalleeId()) != null && !checked.contains(getDefExeList(execution.getCalleeId()))) {
                executionQueue.add(execution); // if the callee is not checked yet, wait
                continue;
            }
            logger.debug("checking " +  execution.toSimpleString());
            checked.add(execution.getID());
            execution.setCanTest(canTestExecution(execution) && canTestCallee(execution) && compatibilityCheck(execution));
            if (!execution.isCanTest()) continue;

            Set<Integer> inputsAndDes = getInputAndDes(execution);
            exeToInputVarsMap.put(execution.getID(), inputsAndDes);
            if (execution.getCalleeId() != -1 && execution.getCallee() instanceof ObjVarDetails)
                inputsAndDes.addAll(getParentExeStack(execution.getCallee(), true).stream().map(e -> exeToInputVarsMap.getOrDefault(e.getID(), new HashSet<>())).flatMap(Collection::stream).collect(Collectors.toSet())); // may change to accumulative putting to Map if it takes LONG
            execution.setCanTest(execution.getParams().stream().map(this::getVarDetailByID).noneMatch(p->isUnmockableParam(execution, p)) && !hasUnmockableUsage(execution, inputsAndDes));

            if(!execution.getRequiredPackage().isEmpty() && !execution.getRequiredPackage().startsWith(Properties.getSingleton().getPUT()))
                execution.setCanTest(false);
            logger.debug(executionQueue.size() +" checks remaining");
        }
    }

    /**
     * Check if there exist unmockable usage of vars specified in the provided execution
     *
     * @param execution Method Execution under investigation
     * @param vars      VarDetail IDs to check
     * @return if there is unmockable usages of vars in execution
     */
    private boolean hasUnmockableUsage(MethodExecution execution, Set<Integer> vars) {
        logger.debug("Checking has unmockable usages " + execution.toSimpleString());
        return getChildren(execution.getID()).stream()
                .map(this::getMethodExecutionByID)
                .anyMatch(e -> {
                    MethodDetails methodDetails = e.getMethodInvoked();
                    if (methodDetails.isFieldAccess() && vars.contains(e.getCalleeId()))
                        return true;
                    if(vars.contains(e.getCalleeId()) && e.getParams().stream().map(this::getVarDetailByID).anyMatch(v -> isUnmockableParam(execution,v)))
                        return true;

                    if(vars.contains(e.getCalleeId())) {
                        try {
                            Method toMock = methodDetails.getdClass().getMethod(methodDetails.getName(), methodDetails.getParameterTypes().stream().map(t -> {
                                try {
                                    return ClassUtils.getClass(t.toQuotedString());
                                } catch (ClassNotFoundException classNotFoundException) {
                                    classNotFoundException.printStackTrace();
                                    return null;
                                }
                            }).toArray(Class<?>[]::new));

                            if(Modifier.isPrivate(toMock.getModifiers()) || methodDetails.getName().equals("equals") || methodDetails.getName().equals("hashCode"))
                                return true;
                            if(e.getReturnValId()!=-1) {
                                VarDetail returnVal = this.getVarDetailByID(e.getReturnValId());
                                if(!canProvideReturnVal(execution, returnVal)) return true;
                            }
                        } catch (NoSuchMethodException ignored) {
                        }
                    }
                    if (InstrumentResult.getSingleton().isLibMethod(methodDetails.getId()) && e.getParams().stream().anyMatch(vars::contains))
                        return true;

                    return hasUnmockableUsage(e, vars);
                });
    }

    private boolean canProvideReturnVal(MethodExecution execution, VarDetail returnVal) {
        if (returnVal instanceof EnumVarDetails && returnVal.getType().equals(Class.class)) {
            String neededPackage = null;
            try {
                neededPackage = getRequiredPackage(ClassUtils.getClass(((EnumVarDetails) returnVal).getValue()));
            } catch (ClassNotFoundException ignored) {
            }
            if(neededPackage == null || (!neededPackage.isEmpty() && !execution.getRequiredPackage().equals(neededPackage))) return false;
            execution.setRequiredPackage(neededPackage);
        }
        if(returnVal instanceof ObjVarDetails && (returnVal.getTypeSimpleName().startsWith("$") || returnVal.getType().isAnonymousClass())) return false;
        if(returnVal instanceof ArrVarDetails) return ((ArrVarDetails) returnVal).getComponents().stream().map(this::getVarDetailByID).allMatch(c -> canProvideReturnVal(execution, c));
        if(returnVal instanceof MapVarDetails) return ((MapVarDetails) returnVal).getKeyValuePairs().stream().flatMap(c-> Stream.of(c.getKey(), c.getValue())).map(this::getVarDetailByID).allMatch(c-> canProvideReturnVal(execution, c));
        return true;
    }

    private boolean isUnmockableParam(MethodExecution execution, VarDetail p) {
        if(p instanceof ObjVarDetails && (p.getType().isAnonymousClass() || p.getTypeSimpleName().startsWith("$"))) return true;
        if(p instanceof ObjVarDetails && p.getID()!=execution.getCalleeId() && !execution.getParams().contains(p.getID()) && !p.equals(nullVar)) return true;
        if(p instanceof ArrVarDetails) return ((ArrVarDetails) p).getComponents().stream().map(this::getVarDetailByID).anyMatch(c -> isUnmockableParam(execution, c));
        if(p instanceof MapVarDetails) return ((MapVarDetails) p).getKeyValuePairs().stream().flatMap(c-> Stream.of(c.getKey(), c.getValue())).map(this::getVarDetailByID).anyMatch(c-> isUnmockableParam(execution, c));
        if(p instanceof EnumVarDetails && p.getType().equals(Class.class)) {
            try {
                String requiredPackage = getRequiredPackage(ClassUtils.getClass(((EnumVarDetails) p).getValue()));
                if(requiredPackage == null || (!execution.getRequiredPackage().isEmpty() && !execution.getRequiredPackage().equals(requiredPackage)) ) return true;
                else execution.setRequiredPackage(requiredPackage);
            } catch (ClassNotFoundException ignored) {

            }
        }
        return false;
    }
    /**
     * Get stack of method executions needed to create the var specified
     *
     * @param var Variable to create
     * @param cache
     * @return stack of method executions for creation
     */
    public Stack<MethodExecution> getParentExeStack(VarDetail var, boolean cache) {
        Stack<MethodExecution> executionStack = new Stack<>();
        if (!(var instanceof ObjVarDetails)) return null;
        if(varToParentStackCache.containsKey(var)) return varToParentStackCache.get(var);
        while (var != null) {
            if (var instanceof EnumVarDetails) break;
            Integer def = getDefExeList(var.getID());
            if (def == null) return null; // i.e. can not be produced
            MethodExecution defExe = getMethodExecutionByID(def);
            if (executionStack.contains(defExe))
                return null; // if its a loop def

            executionStack.push(defExe);
            if (defExe.getMethodInvoked().getType().equals(METHOD_TYPE.MEMBER))
                var = defExe.getCallee();
            else
                var = null;
        }
        if(cache)
            varToParentStackCache.put(var, executionStack);
        return executionStack;
    }

    /**
     * @param execution MethodExecution under check
     * @return if the execution provided is / runs faulty methods
     */
    private boolean containsFaultyDef(MethodExecution execution) {
        InstrumentResult instrumentResult = InstrumentResult.getSingleton();
        MethodDetails details = execution.getMethodInvoked();
        return Properties.getSingleton().getFaultyFuncIds().stream()
                .map(instrumentResult::getMethodDetailByID)
                .anyMatch(s -> s.equals(details) || (execution.getCalleeId() != -1 && s.getName().equals(details.getName()) && getVarDetailByID(execution.getCalleeId()).getType().equals(s.getdClass()))) || getChildren(execution.getID()).stream().anyMatch(this::containsFaultyDef);
    }

    /**
     * @param exeID id of MethodExecution under check
     * @return if the execution provided is / runs faulty methods
     */
    private boolean containsFaultyDef(int exeID) {
        return containsFaultyDef(getMethodExecutionByID(exeID));
    }


    /**
     * @param execution MethodExecution under check
     * @return if the execution can be tested based on requirements on the execution itself
     */
    private boolean canTestExecution(MethodExecution execution) {
        MethodDetails methodDetails = execution.getMethodInvoked();
        logger.debug("Checking can test execution " + execution.toSimpleString());
        if (containsFaultyDef(execution) || getAllMethodExecs().values().stream().anyMatch(e -> e.sameCalleeParamNMethod(execution) && !e.sameContent(execution)))
            return false;
        if (methodDetails.getAccess().equals(ACCESS.PRIVATE) || methodDetails.getName().startsWith("access$"))
            return false;
        switch (methodDetails.getType()) {
            case STATIC_INITIALIZER:
                return false;
            case CONSTRUCTOR:
                if (methodDetails.getDeclaringClass().isAbstract()) return false;
            case STATIC:
                String neededPackage = getRequiredPackage(methodDetails.getdClass());
                if (neededPackage == null) return false;
                execution.setRequiredPackage(neededPackage);
        }
        if(methodDetails.getAccess().equals(ACCESS.PROTECTED)) {
            if(execution.getRequiredPackage().isEmpty())
                execution.setRequiredPackage(methodDetails.getDeclaringClass().getPackageName());
            else if(!execution.getRequiredPackage().equals(methodDetails.getDeclaringClass().getPackageName()))
                return false;
        }
        return true;
    }

    /**
     * @param execution MethodExecution under check
     * @return if the execution can be tested based on its callee requirement
     */
    private boolean canTestCallee(MethodExecution execution) {
        if (execution.getCalleeId() == -1) return true;
        logger.debug("Checking can test callee " + execution.getCallee().toString());
        if (execution.getCallee() instanceof ObjVarDetails) {
            if(execution.getCallee().getType().isAnonymousClass()) return false;
            Stack<MethodExecution> parentStack = getParentExeStack(execution.getCallee(), true);
            if (parentStack == null || parentStack.contains(execution) || !parentStack.stream().allMatch(MethodExecution::isCanTest))
                return false;
        }
        if (execution.getCallee() instanceof EnumVarDetails) {
            EnumVarDetails callee = (EnumVarDetails) execution.getCallee();
            // is field
            if (!callee.getType().isEnum()) {
                try {
                    if (!callee.getType().getPackage().getName().startsWith(Properties.getSingleton().getPUT()) && !Modifier.isPublic(callee.getType().getField(callee.getValue()).getModifiers()))
                        return false;
                } catch (NoSuchFieldException e) {
                    return false;
                }
                if (callee.getType().getPackage().getName().startsWith(Properties.getSingleton().getPUT()) && !InstrumentResult.getSingleton().getClassPublicFieldsMap().getOrDefault(callee.getType().getName(), new HashSet<>()).contains(callee.getValue()))
                    return false;
            }
            execution.setRequiredPackage(getRequiredPackage(callee.getType()));
            return execution.getRequiredPackage() != null && compatibilityCheck(execution, execution.getRequiredPackage());
        }
        return true;
    }

    /**
     * @param execution MethodExecution under check
     * @return if the package required (if any) by its callee (if any) is compatible with that of the execution
     */
    private boolean compatibilityCheck(MethodExecution execution) {
        logger.debug("checking compatibility for " + execution.toSimpleString());
        if (execution.getCalleeId() == -1 || !(execution.getCallee() instanceof ObjVarDetails)) return true;
        if (getDefExeList(execution.getCalleeId()) == null) return false;
        MethodExecution calleeDef = getMethodExecutionByID(getDefExeList(execution.getCalleeId()));
        ObjVarDetails callee = (ObjVarDetails) execution.getCallee();
        if (calleeDef.getRequiredPackage().isEmpty() || calleeDef.getRequiredPackage().equals(callee.getType().getPackage().getName()))
            return true;
        if (!compatibilityCheck(execution, calleeDef.getRequiredPackage()))
            return false;
        execution.setRequiredPackage(calleeDef.getRequiredPackage());
        return true;
    }

    /**
     * @param execution       MethodExecution under check
     * @param requiredPackage package required
     * @return if the package required is compatible with that of the execution
     */
    private boolean compatibilityCheck(MethodExecution execution, String requiredPackage) {
        if (execution.getCallee().getType().getPackage().getName().equals(requiredPackage) || requiredPackage.isEmpty())
            return true;
        MethodDetails details = execution.getMethodInvoked();
        List<Class<?>> superClasses = ClassUtils.getAllSuperclasses(execution.getCallee().getType());
        superClasses = superClasses.subList(0, superClasses.indexOf(details.getdClass()) + 1);
        return superClasses.stream().anyMatch(c -> accessibilityCheck(c, requiredPackage));
    }

    /**
     * Get inputs and their descendants in a method execution
     *
     * @param execution method execution under review
     * @return set of ids of vardetails being used as inputs or descendants of inputs in the execution
     */
    private Set<Integer> getInputAndDes(MethodExecution execution) {
        Set<Integer> inputsAndDes = execution.getParams().stream()
                .map(this::getVarDetailByID)
                .map(this::getRelatedObjVarIDs)
                .flatMap(Collection::stream)
                .collect(Collectors.toSet());

        return getDes(execution, new HashSet<>(inputsAndDes));
    }

    /**
     * Get descendants of provided inputs in the provided execution
     * dfs approach
     *
     * @param execution    Method execution under review
     * @param inputsAndDes ids of Set of inputs to investigate
     * @return Set of ids of vardetails found in execution matching criteria
     */
    private Set<Integer> getDes(MethodExecution execution, Set<Integer> inputsAndDes) {
        if (inputsAndDes.size() == 0) return inputsAndDes;
        getChildren(execution.getID())
                .forEach(cID -> {
                    MethodExecution c = getMethodExecutionByID(cID);
                    if (c.getCalleeId() != -1 && inputsAndDes.contains(c.getCalleeId())) { // only consider callee, if it is param, it either would call sth inside that makes impact / its lib method that would be considered invalid later
                        inputsAndDes.addAll(getRelatedObjVarIDs(c.getResultThisId()));
                        inputsAndDes.addAll(getRelatedObjVarIDs(c.getReturnValId()));
                    }
                    inputsAndDes.addAll(getDes(c, inputsAndDes));
                });
        return inputsAndDes;
    }

    /**
     * Get Object Vardetail IDs relating to input
     *
     * @param varDetailID varDetail to investigate
     * @return Set of ids matching criteria
     */
    private Set<Integer> getRelatedObjVarIDs(int varDetailID) {
        if (varDetailID == -1) return new HashSet<>();
        return getRelatedObjVarIDs(getVarDetailByID(varDetailID));
    }

    /**
     * Get Object Vardetail IDs relating to input
     *
     * @param varDetail varDetail to investigate
     * @return Set of ids matching criteria
     */
    private Set<Integer> getRelatedObjVarIDs(VarDetail varDetail) {
        Set<Integer> relatedVarIDs = new HashSet<>();
        if (varDetail instanceof StringVarDetails || varDetail instanceof StringBVarDetails || varDetail instanceof PrimitiveVarDetails || varDetail instanceof EnumVarDetails)
            return relatedVarIDs;
        relatedVarIDs.add(varDetail.getID());
        if (varDetail instanceof ArrVarDetails)
            relatedVarIDs.addAll(((ArrVarDetails) varDetail).getComponents().stream().map(this::getRelatedObjVarIDs).flatMap(Collection::stream).collect(Collectors.toSet()));
        if (varDetail instanceof MapVarDetails)
            relatedVarIDs.addAll(((MapVarDetails) varDetail).getKeyValuePairs().stream()
                    .flatMap(pair -> Stream.of(pair.getKey(), pair.getValue()))
                    .map(this::getRelatedObjVarIDs)
                    .flatMap(Collection::stream)
                    .collect(Collectors.toSet()));
        return relatedVarIDs;
    }

    public static class CallOrderEdge extends DefaultEdge {
        private final int label;

        /**
         * Constructs a CallOrderEdge
         *
         * @param label the label of the new edge
         */
        public CallOrderEdge(int label) {
            this.label = label;
        }

        /**
         * Gets the label associated with this edge
         *
         * @return edge label
         */
        public int getLabel() {
            return label;
        }

        @Override
        public String toString() {
            return "(" + getSource() + " : " + getTarget() + " : " + label + ")";
        }
    }
}
