package program.generation;

import entity.ACCESS;
import entity.METHOD_TYPE;
import entity.UnrecognizableException;
import helper.Properties;
import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import program.analysis.MethodDetails;
import program.execution.ExecutionTrace;
import program.execution.MethodExecution;
import program.execution.stmt.*;
import program.execution.variable.*;
import program.generation.test.*;
import program.instrumentation.InstrumentResult;
import soot.Modifier;
import soot.VoidType;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class TestGenerator {
    private static final Logger logger = LogManager.getLogger(TestGenerator.class);
    private static final TestGenerator singleton = new TestGenerator();
    private static final String[] SKIP_MEMBER_METHODS = {"equals", "toString", "hashCode"};
    private static final String[] SKIP_STATIC_METHODS = {"hashCode"};
    private final ExecutionTrace executionTrace = ExecutionTrace.getSingleton();
    private final InstrumentResult instrumentResult = InstrumentResult.getSingleton();
    private final Set<MethodExecution> coveredExecutions = new HashSet<>();
    private final TestSuite testSuite = new TestSuite();
    private final Set<Integer> passedVar = new HashSet<>();
    private final Set<Integer> passedExe = new HashSet<>();
    private final Set<Integer> failedExe = new HashSet<>();

    public TestGenerator() {
    }

    public static TestGenerator getSingleton() {
        return singleton;
    }

    public void generateResultCheckingTests(List<MethodExecution> snapshot) {
        snapshot.stream()
                .filter(e -> {
                    MethodDetails details = e.getMethodInvoked();
                    return !instrumentResult.isLibMethod(details.getId()) && !(details.getType().equals(METHOD_TYPE.MEMBER) && Arrays.stream(SKIP_MEMBER_METHODS).anyMatch(s -> details.getName().equals(s))) && !(details.getType().equals(METHOD_TYPE.STATIC) && Arrays.stream(SKIP_STATIC_METHODS).anyMatch(s -> details.getName().equals(s))) ;
                })
                .filter(e -> e.getTest() != null)
                .distinct()
                .filter(e -> e.getReturnValId() != -1 && (executionTrace.getDefExeList(e.getReturnValId()) == null || !executionTrace.getDefExeList(e.getReturnValId()).equals(e.getID())) && exeCanBeTested(e.getID(), 0, -1, new HashSet<>(), e.getMethodInvoked().getdClass().getPackage().getName()))// prevent self checking
                .filter(e -> {
                    try {
                        MethodDetails details = e.getMethodInvoked();
                        Method method = details.getdClass().getMethod(details.getName(), details.getParameterTypes().stream().map(t -> {
                            try {
                                return ClassUtils.getClass(t.toQuotedString());
                            } catch (ClassNotFoundException classNotFoundException) {
                                classNotFoundException.printStackTrace();
                                return null;
                            }
                        }).toArray(Class<?>[]::new));
                        if(method.isBridge()) return false;
                        if(e.getCalleeId()==-1) return true;
                        VarDetail callee = executionTrace.getVarDetailByID(e.getCalleeId());
                        // prevent incorrect method call
                        if(!callee.getType().equals(method.getDeclaringClass()) && method.getDeclaringClass().isAssignableFrom(callee.getType()))
                            try{
                                if(!callee.getType().getMethod(method.getName(), method.getParameterTypes()).equals(method))
                                    logger.debug(e.toDetailedString());
                                return callee.getType().getMethod(method.getName(), method.getParameterTypes()).equals(method);
                            }
                            catch (NoSuchMethodException noSuchMethodException) {
                                return true;
                            }
                    } catch (NoSuchMethodException noSuchMethodException) {
                        logger.error(noSuchMethodException.getMessage());
                    }
                    return true;
                })
                .filter(e -> {
                    VarDetail returnVarDetail = executionTrace.getVarDetailByID(e.getReturnValId());
                    Class<?> varDetailClass = returnVarDetail.getClass();
                    if(varDetailClass.equals(ObjVarDetails.class)) return returnVarDetail.equals(executionTrace.getNullVar());
                    if(varDetailClass.equals(ArrVarDetails.class)) {
                        if(((ArrVarDetails) returnVarDetail).getComponents().size() == 0) return true;
                        return StringUtils.countMatches(e.getMethodInvoked().getReturnSootType().toString(), "[]") == StringUtils.countMatches(returnVarDetail.getType().getSimpleName(), "[]") && ((ArrVarDetails) returnVarDetail).getLeaveType().stream().allMatch(ClassUtils::isPrimitiveOrWrapper);
                    }
                    return  true;
                })
                .map(e ->
                {
                    MethodDetails methodDetails = e.getMethodInvoked();

                    ValueTestCase testCase = new ValueTestCase(methodDetails.getdClass().getPackage().getName());
                    MethodInvStmt invStmt = getMethodInvStmt(e, testCase);
                    Class<?> returnValType = executionTrace.getVarDetailByID(e.getReturnValId()).getType();
                    Stmt returnValStmt = new VarStmt(returnValType, testCase.getNewVarID(), e.getReturnValId());
                    Class<?> detailsType = null;
                    try {
                        detailsType = ClassUtils.getClass(methodDetails.getReturnSootType().toString());
                    } catch (ClassNotFoundException classNotFoundException) {
                        classNotFoundException.printStackTrace();
                    }
                    testCase.addStmt(new AssignStmt(returnValStmt,(detailsType!=null && detailsType.equals(returnValType)) ? invStmt : new CastStmt(e.getReturnValId(), returnValType,  invStmt)));
                    Stmt expectedStmt = generateDefStmt(e.getReturnValId(), testCase, false, true);
                    if(ClassUtils.isPrimitiveWrapper(returnValType) && !(executionTrace.getVarDetailByID(e.getReturnValId()) instanceof EnumVarDetails)) {
                        expectedStmt = new CastStmt(expectedStmt.getResultVarDetailID(), ClassUtils.wrapperToPrimitive(returnValType), expectedStmt);
                        returnValStmt = new CastStmt(returnValStmt.getResultVarDetailID(), ClassUtils.wrapperToPrimitive(returnValType), returnValStmt);
                    }
                    testCase.setAssertion(new AssertStmt(expectedStmt, returnValStmt));
                    this.coveredExecutions.add(e);
                    return testCase;
                })
                .forEach(testSuite::assignTestCase);
    }

    public void generateExceptionTests(List<MethodExecution> snapshot) {
        snapshot.stream()
                .filter(e -> e.getReturnValId() == -1 && e.getExceptionClass() != null && !e.getExceptionClass().equals(UnrecognizableException.class) && exeCanBeTested(e.getID(), 0, -1, new HashSet<>(), e.getMethodInvoked().getdClass().getPackage().getName()))
                .map(e -> {
                    MethodDetails m = e.getMethodInvoked();

                    ExceptionTestCase testCase = new ExceptionTestCase(m.getdClass().getPackage().getName());
                    MethodInvStmt invStmt = getMethodInvStmt(e, testCase);
                    testCase.addStmt(invStmt);
                    testCase.setExceptionClass(e.getExceptionClass());
                    return testCase;
                })
                .forEach(testSuite::assignTestCase);

    }

    public boolean exeCanBeTested(Integer methodExecutionID, int lv, int defedVar, Set<Integer> exeUnderCheck, String packageName) {
        MethodExecution execution = executionTrace.getMethodExecutionByID(methodExecutionID);
        MethodDetails details = execution.getMethodInvoked();
        if (passedExe.contains(methodExecutionID))
            return true;
        if (exeUnderCheck.contains(methodExecutionID)) {
            logger.debug("same execution checked" + lv + "\n" + details.toString() + "\n" + execution.toDetailedString());
            return false;
        }
        if (!execution.isReproducible()) {
            logger.debug("CANNOT test: results not reproducible \t" + lv + "\n" + details.toString() + "\n" + execution.toDetailedString());
            return false;
        }
        if (failedExe.contains(methodExecutionID)) {
            logger.debug("CANNOT test: past record \t" + lv + "\n" + details.toString() + "\n" + execution.toDetailedString());
            return false;
        }
//        logger.debug("checking " + lv + "\t" + execution.toDetailedString());
        if (containsFaultyDef(methodExecutionID)) {
            logger.debug("CANNOT test: contains faulty def \t" + lv + "\n" + details.toString() + "\n" + execution.toDetailedString());
            failedExe.add(methodExecutionID);
            return false;
        }
        if (details.getType().equals(METHOD_TYPE.STATIC_INITIALIZER)) {
            failedExe.add(methodExecutionID);
            return false;
        }
        if (details.getAccess().equals(ACCESS.PRIVATE) || (details.getAccess().equals(ACCESS.PROTECTED) && !details.getDeclaringClass().getPackageName().equals(packageName))) {
            logger.debug("CANNOT test: access issue \t" + lv + "\n" + details + "\n" + execution.toDetailedString());
            failedExe.add(methodExecutionID);
            return false;
        }
        if (details.getName().startsWith("access$")) {
            logger.debug("CANNOT test: access$ method");
            failedExe.add(methodExecutionID);
            return false;
        }
        if ((details.getType().equals(METHOD_TYPE.CONSTRUCTOR) && details.getDeclaringClass().isAbstract()) || details.getDeclaringClass().isPrivate() || (!details.getDeclaringClass().isPrivate() && !details.getDeclaringClass().isPublic() && !details.getDeclaringClass().getPackageName().equals(packageName))) {
            logger.debug("CANNOT test: access issue of class \t" + lv + "\n" + details + "\n" + execution.toDetailedString());
            failedExe.add(methodExecutionID);
            return false;
        }
        if (execution.getCalleeId() != -1 && execution.getCalleeId() == defedVar) {
            logger.debug("CANNOT test: callee of def same as callee issue\t" + lv + "\n" + details + "\n" + execution.toDetailedString());
            failedExe.add(methodExecutionID);
            return false;
        }

        if (execution.getParams().contains(defedVar)) {
            logger.debug("CANNOT test: parameter same as callee issue\t" + lv + "\n" + details + "\n" + execution.toDetailedString());
            failedExe.add(methodExecutionID);
            return false;
        }
        exeUnderCheck.add(methodExecutionID);
        if (execution.getCalleeId() != -1 && !varCanBeTested(execution.getCalleeId(), lv + 1, exeUnderCheck, packageName)) {
            logger.debug("CANNOT test: callee issue\t" + lv + "\n" + details + "\n" + execution.toDetailedString());
            failedExe.add(methodExecutionID);
            exeUnderCheck.remove(methodExecutionID);
//            logger.debug(executionTrace.getDefExeList(execution.getCalleeId())!=null? executionTrace.getMethodExecutionByID(executionTrace.getDefExeList(execution.getCalleeId())).toDetailedString() : "");
            return false;
        }
        if (execution.getParams().stream().anyMatch(p -> !varCanBeTested(p, lv + 1, exeUnderCheck, packageName))) {
            logger.debug("CANNOT test: parameter issue\t" + lv + "\n" + details + "\n" + execution.toDetailedString());
            failedExe.add(methodExecutionID);
            exeUnderCheck.remove(methodExecutionID);
//            logger.debug(executionTrace.getDefExeList(execution.getCalleeId())!=null? executionTrace.getMethodExecutionByID(executionTrace.getDefExeList(execution.getCalleeId())).toDetailedString() : "");
            return false;
        }
        if (execution.getReturnValId() != -1 && execution.getReturnValId() != defedVar && !varCanBeTested(execution.getReturnValId(), lv + 1, exeUnderCheck, packageName)) {
            logger.debug("CANNOT test: return val issue\t" + lv + "\n" + details + "\n" + execution.toDetailedString());
            failedExe.add(methodExecutionID);
            exeUnderCheck.remove(methodExecutionID);
            return false;
        }

        if(execution.getCalleeId() != -1) {
            Class<?> calleeType = executionTrace.getVarDetailByID(execution.getCalleeId()).getType() ;
            // if the callee is not an accessible class
            // check if there exist a class between real var type & method declaring class that can be accessed (else the method is NOT accessible)
            if(!accessibilityCheck(calleeType, packageName) ) {
                List<Class<?>> superClasses = ClassUtils.getAllSuperclasses(calleeType);
                superClasses = superClasses.subList(0, superClasses.indexOf(details.getdClass())+1);
                if(superClasses.stream().noneMatch(c -> accessibilityCheck(c, packageName))) {
                    failedExe.add(methodExecutionID);
                    exeUnderCheck.remove(methodExecutionID);
                    return false;
                }

            }
        }
        if(!accessibilityCheck(details.getdClass(), packageName) && details.getType().equals(METHOD_TYPE.CONSTRUCTOR)){
            failedExe.add(methodExecutionID);
            exeUnderCheck.remove(methodExecutionID);
            return false;
        }
        if(details.getType().equals(METHOD_TYPE.STATIC) && !accessibilityCheck(details.getdClass(), packageName)) {
            failedExe.add(methodExecutionID);
            exeUnderCheck.remove(methodExecutionID);
            return false;
        }

        Set<Integer> diffResExe = ExecutionTrace.getSingleton().getAllMethodExecs().values().stream().filter(execution::sameCalleeParamNMethod).filter(e -> e.getReturnValId() != execution.getReturnValId()).map(MethodExecution::getID).collect(Collectors.toSet());
        if(diffResExe.size() > 0 ) {
            failedExe.addAll(diffResExe);
            failedExe.add(methodExecutionID);
            exeUnderCheck.remove(methodExecutionID);
            return false;
        }
        passedExe.add(methodExecutionID);
        exeUnderCheck.remove(methodExecutionID);
        return true;
    }
    public static boolean accessibilityCheck(Class<?> c, String packageName) {
        int modifier = c.getModifiers();
        if(c.getName().contains("$"))
            try {
                Integer.parseInt(c.getName().substring(c.getName().indexOf("$") + 1)); // dont know why isAnonymousClass return false for these
                return false;
            }
            catch (NumberFormatException e ){

            }
        logger.debug(c.getName() + "\t"+(Modifier.isPublic(modifier) || (!Modifier.isPrivate(modifier) && c.getPackage().getName().equals(packageName))));
        return (Modifier.isPublic(modifier) || (!Modifier.isPrivate(modifier) && c.getPackage().getName().equals(packageName))) // access check
                && (!ClassUtils.isInnerClass(c) || (Modifier.isStatic(modifier) && accessibilityCheck(c.getEnclosingClass(), packageName))) // if is inner class, is static inner class and its enclosing class CAN be accessed
                && !c.isAnonymousClass(); // is NOT anonymous class
    }

    private boolean varCanBeTested(Integer varID, int lv, Set<Integer> exeUnderCheck, String packageName) {
        VarDetail varDetail = executionTrace.getVarDetailByID(varID);
        Class<?> varDetailClass = varDetail.getClass();
        if (passedVar.contains(varID)) return true;

        if (varDetailClass.equals(PrimitiveVarDetails.class) || varDetailClass.equals(WrapperVarDetails.class) || varDetailClass.equals(StringVarDetails.class) || varDetailClass.equals(StringBVarDetails.class) || varDetail.equals(executionTrace.getNullVar())) {
            passedVar.add(varID);
            return true;
        }
        else if (varDetailClass.equals(EnumVarDetails.class) ){
            logger.debug(varDetail.toDetailedString() +"\t" + varDetail.getType() + ((EnumVarDetails) varDetail).getValue());
            if(varDetail.getType().equals(Class.class)) {
                try {
                    return TestGenerator.accessibilityCheck(ClassUtils.getClass(((EnumVarDetails) varDetail).getValue()), packageName);
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                    return false;
                }
            }
            else if (!varDetail.getType().isEnum())  {
                try {
                    return varDetail.getType().getPackage().getName().startsWith("java") ? Modifier.isPublic(varDetail.getType().getField(((EnumVarDetails) varDetail).getValue()).getModifiers()) :  InstrumentResult.getSingleton().getClassPublicFieldsMap().getOrDefault(varDetail.getType().getName(), new HashSet<>()).contains(((EnumVarDetails) varDetail).getValue());
                } catch (NoSuchFieldException e) {
                    return false;
                }
            }
            else return TestGenerator.accessibilityCheck(varDetail.getType(), packageName);
        }
        else if (varDetail.getClass().equals(MapVarDetails.class)) {
            if (((MapVarDetails) varDetail).getKeyValuePairs().stream().allMatch(e -> varCanBeTested(e.getKey(), lv + 1, exeUnderCheck, packageName) && varCanBeTested(e.getValue(), lv + 1, exeUnderCheck, packageName))) {
                passedVar.add(varID);
                passedVar.addAll(((MapVarDetails) varDetail).getKeyValuePairs().stream().flatMap(e -> Stream.of(e.getKey(), e.getValue())).collect(Collectors.toSet()));
                return true;
            }
            logger.debug("CANNOT test: Map \t" + lv + "\n" + varDetail.toDetailedString());
            return false;
        } else if (varDetail.getClass().equals(ArrVarDetails.class)) {
            if (((ArrVarDetails) varDetail).getComponents().stream().allMatch(varID1 -> varCanBeTested(varID1, lv + 1, exeUnderCheck, packageName))) {
                passedVar.add(varID);
                passedVar.addAll(((ArrVarDetails) varDetail).getComponents());
                return true;
            }
            logger.debug("CANNOT test: Arr " + "\t" + lv + "\n" + varDetail.toDetailedString());
            return false;
        } else if (!varDetail.equals(ExecutionTrace.getSingleton().getNullVar()) && (executionTrace.getDefExeList(varID) == null || !exeCanBeTested(executionTrace.getDefExeList(varID), lv + 1, varID, exeUnderCheck, packageName))) {
            return false;
        }
        passedVar.add(varID);
        return true;
    }

    private boolean containsFaultyDef(Integer exeID) {
        MethodExecution execution = executionTrace.getMethodExecutionByID(exeID);
        MethodDetails details = execution.getMethodInvoked();

        return Properties.getSingleton().getFaultyFuncIds().stream()
                .map(instrumentResult::getMethodDetailByID)
                .anyMatch(s -> s.equals(details) || (execution.getCalleeId()!=-1 && s.getName().equals(details.getName()) && executionTrace.getVarDetailByID(execution.getCalleeId()).getType().equals(s.getdClass()))) || executionTrace.getAllChildren(exeID).stream().anyMatch(this::containsFaultyDef);

    }

    public Stmt generateDefStmt(Integer varDetailsID, TestCase testCase, boolean checkExisting, boolean store) throws IllegalArgumentException {
        VarDetail varDetail = executionTrace.getVarDetailByID(varDetailsID);
        Class<?> varDetailClass = varDetail.getClass();
        if (checkExisting && testCase.getExistingVar(varDetailsID) != null && testCase.getExistingVar(varDetailsID).size() > 0)
            return testCase.getExistingVar(varDetailsID).get(0);
        if (varDetail.equals(executionTrace.getNullVar()))
            return new ConstantStmt(varDetailsID);
        if (varDetailClass.equals(PrimitiveVarDetails.class) || varDetailClass.equals(WrapperVarDetails.class) || varDetailClass.equals(StringVarDetails.class) || varDetailClass.equals(MapVarDetails.class) || varDetailClass.equals(ArrVarDetails.class) || varDetailClass.equals(EnumVarDetails.class) || varDetailClass.equals(StringBVarDetails.class))
            return getSingleDefStmt(varDetail, testCase);
        else if (executionTrace.getDefExeList(varDetail.getID()) == null) {
            logger.debug(varDetail.toDetailedString());
            throw new IllegalArgumentException("Missing def list");
        } else return getComplexDefStmt(varDetail, testCase);
    }

    private Stmt getSingleDefStmt(VarDetail varDetail, TestCase testCase) {
        Class<?> varDetailClass = varDetail.getClass();
        if (executionTrace.getDefExeList(varDetail.getID()) != null || !(varDetailClass.equals(PrimitiveVarDetails.class) || varDetailClass.equals(WrapperVarDetails.class) || varDetailClass.equals(StringVarDetails.class) || varDetailClass.equals(MapVarDetails.class) || varDetailClass.equals(ArrVarDetails.class) || varDetailClass.equals(EnumVarDetails.class) || varDetailClass.equals(StringBVarDetails.class)))
            throw new IllegalArgumentException("Provided VarDetail cannot be assigned with single stmt");
        if (varDetailClass.equals(PrimitiveVarDetails.class) || varDetailClass.equals(WrapperVarDetails.class) || varDetailClass.equals(StringVarDetails.class) || varDetailClass.equals(EnumVarDetails.class))
            return new ConstantStmt(varDetail.getID());
        List<Stmt> params;
        if(varDetailClass.equals(StringBVarDetails.class)) {
            params = new ArrayList<>();
            params.add(new ConstantStmt(((StringBVarDetails) varDetail).getStringValID()));
        }
        else if (varDetailClass.equals(MapVarDetails.class)) {
            params = ((MapVarDetails) varDetail).getKeyValuePairs().stream().map(e -> new PairStmt(generateDefStmt(e.getKey(), testCase, true, true), generateDefStmt(e.getValue(), testCase, true, true))).collect(Collectors.toList());
        } else {
            params = ((ArrVarDetails) varDetail).getComponents().stream().map(e -> generateDefStmt(e, testCase, true, true)).collect(Collectors.toList());
            if (((ArrVarDetails) varDetail).getComponents().stream().map(executionTrace::getVarDetailByID).noneMatch(e -> e.getType().isArray()) && ((ArrVarDetails) varDetail).getComponents().size() <= 25)
                return new ConstructStmt(varDetail.getID(), null, params);
            else {
                VarStmt varStmt =  new VarStmt(varDetail.getType(), testCase.getNewVarID(), varDetail.getID());
                testCase.addStmt(new AssignStmt(varStmt, new ConstructStmt(varDetail.getID(), null, params)));
                testCase.addOrUpdateVar(varDetail.getID(), varStmt);
                return varStmt;
            }
        }
        VarStmt varStmt = new VarStmt( varDetail.getType(), testCase.getNewVarID(), varDetail.getID());
        testCase.addStmt(new AssignStmt(varStmt, new ConstructStmt(varDetail.getID(), null, params)));
        testCase.addOrUpdateVar(varDetail.getID(), varStmt);
        return varStmt;
    }

    private MethodInvStmt getMethodInvStmt(MethodExecution execution, TestCase testCase) {
        MethodDetails details = execution.getMethodInvoked();
        MethodInvStmt invokeStmt = null;
        VarStmt varStmt;
        List<Stmt> paramStmt = IntStream.range(0, details.getParameterCount()).mapToObj(i -> {
            Stmt returnStmt = generateDefStmt(execution.getParams().get(i), testCase, true, true);
            VarDetail varDetail = ExecutionTrace.getSingleton().getVarDetailByID(returnStmt.getResultVarDetailID());
            try {
                Class<?> requiredType = ClassUtils.getClass(details.getParameterTypes().get(i).toString());
                if(!requiredType.equals(varDetail.getType()) || ExecutionTrace.getSingleton().getNullVar().equals(varDetail)) {
                    returnStmt = new CastStmt(returnStmt.getResultVarDetailID(), requiredType, returnStmt);
                }
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
            return returnStmt;
        }).collect(Collectors.toList());
        switch (details.getType()) {
            case STATIC:
                invokeStmt = new MethodInvStmt(details.getDeclaringClass().getShortName().replace("$","."), details.getId(), paramStmt);
                break;
            case CONSTRUCTOR:
                invokeStmt = new MethodInvStmt("", details.getId(), paramStmt);
                break;
            case MEMBER:
                Stmt callee = generateDefStmt(execution.getCalleeId(), testCase, true, true);
                invokeStmt = new MethodInvStmt(callee.getStmt(new HashSet<>()), details.getId(), paramStmt);
                break;
        }

        return invokeStmt;
    }
    public static Class<?> getAccessibleSuperType(Class<?> c, String packageName) {
        return ClassUtils.getAllSuperclasses(c).stream().filter(c1 -> accessibilityCheck(c1, packageName)).findFirst().orElse(c);
    }


    private VarStmt getComplexDefStmt(VarDetail varDetail, TestCase testCase) {
        int varDetailID = varDetail.getID();
        int e = executionTrace.getDefExeList(varDetailID);
        MethodExecution execution = executionTrace.getMethodExecutionByID(e);
        MethodDetails details = execution.getMethodInvoked();
        MethodInvStmt invokeStmt = getMethodInvStmt(execution, testCase);
        VarStmt varStmt = null;
        switch (details.getType()) {
            case STATIC: // must be return value
                if (details.getReturnSootType().equals(VoidType.v()))
                    testCase.addStmt(invokeStmt);
                else {
                    List<VarStmt> availableStmts = testCase.getExistingVar(execution.getReturnValId());
                    varStmt = availableStmts == null || availableStmts.size() == 0 ? null : availableStmts.get(0);
                    Class<?> actualType = executionTrace.getVarDetailByID(execution.getReturnValId()).getType();
                    if(!accessibilityCheck(actualType, testCase.getPackageName()))
                        actualType = getAccessibleSuperType(actualType, testCase.getPackageName());
                    if (varStmt == null)
                        varStmt = new VarStmt(actualType, testCase.getNewVarID(), execution.getReturnValId());
//                        logger.debug(varStmt.getImports());
                    testCase.addStmt(new AssignStmt(varStmt, (details.getReturnSootType().toString().equals(actualType.getName()) ? invokeStmt : new CastStmt(execution.getReturnValId(), actualType, invokeStmt))));
                    testCase.addOrUpdateVar(execution.getReturnValId(), varStmt);
                }
                break;
            case CONSTRUCTOR:
                varStmt = new VarStmt(executionTrace.getVarDetailByID(execution.getResultThisId()).getType(), testCase.getNewVarID(), execution.getResultThisId());
                testCase.addStmt(new AssignStmt(varStmt, invokeStmt));
                testCase.addOrUpdateVar(execution.getResultThisId(), varStmt);
                break;
            case MEMBER:
                Stmt defStmt = generateDefStmt(execution.getCalleeId(), testCase, true, true);
//                varStmt = (VarStmt) generateDefStmt(execution.getCalleeId(), testCase, true, true);
//                    logger.debug(varStmt.getImports());
                if (!details.getReturnSootType().equals(VoidType.v())) {
//                        logger.debug(details.getReturnType());
                    Class<?> actualType = executionTrace.getVarDetailByID(execution.getReturnValId()).getType();
                    if(!accessibilityCheck(actualType, testCase.getPackageName()))
                        actualType = getAccessibleSuperType(actualType, testCase.getPackageName());
                    VarStmt varStmt1 = new VarStmt(actualType, testCase.getNewVarID(), execution.getReturnValId());
//                        logger.debug(varStmt1.getImports());
                    testCase.addStmt(new AssignStmt(varStmt1, details.getReturnSootType().toString().equals(actualType.getName())? invokeStmt : new CastStmt(execution.getReturnValId(), actualType, invokeStmt)));

                    testCase.addOrUpdateVar(execution.getReturnValId(), varStmt1);
                } else
                    testCase.addStmt(invokeStmt);
                if(defStmt instanceof VarStmt) {
                    testCase.removeVar(execution.getCalleeId(), (VarStmt) defStmt);
                    testCase.addOrUpdateVar(execution.getResultThisId(), (VarStmt) defStmt);
                }

                break;
        }

        return testCase.getExistingVar(varDetailID).get(0);
    }


    public void output() throws IOException {
        Properties properties = Properties.getSingleton();
        int totalCases = 0 ;

        File file = new File(properties.getTestSourceDir());
        file.mkdirs();
        FileOutputStream writeStream;
//        if (Properties.getSingleton().getJunitVer() != 3) {
//            file = new File(packageDir, testSuite.getTestSuiteName() + ".java");
//            file.createNewFile();
//            writeStream = new FileOutputStream(file);
//            writeStream.write(testSuite.output().getBytes(StandardCharsets.UTF_8));
//            writeStream.close();
//        }
        for (TestClass tc : testSuite.getTestClasses()) {
            File packageDir = new File(properties.getTestSourceDir(),tc.getPackageName().replace(".", File.separator));
            if (!packageDir.exists() || !packageDir.isDirectory())
                packageDir.mkdirs();
            file = new File(packageDir, tc.getClassName() + ".java");
            file.createNewFile();
            writeStream = new FileOutputStream(file);
            writeStream.write(tc.output().getBytes(StandardCharsets.UTF_8));
            writeStream.close();
            totalCases += tc.getEnclosedTestCases().size();
        }
        file = new File(properties.getTestSourceDir(), "ab-tests.stdout");
        file.createNewFile();
        writeStream = new FileOutputStream(file);
        writeStream.write(testSuite.getTestClasses().stream().map(c -> c.getPackageName() + "." + c.getClassName()).collect(Collectors.joining(",")).getBytes(StandardCharsets.UTF_8));
        writeStream.close();
        logger.debug("Total no. of test cases generated: " + totalCases);
    }
}
