package program.generation;

import entity.ACCESS;
import entity.METHOD_TYPE;
import entity.UnrecognizableException;
import helper.Helper;
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static helper.Helper.*;

public class TestGenerator {
    private static final Logger logger = LogManager.getLogger(TestGenerator.class);
    private static final TestGenerator singleton = new TestGenerator();
    private static final String[] SKIP_MEMBER_METHODS = {"equals", "toString", "hashCode"};
    private static final String[] SKIP_STATIC_METHODS = {"hashCode"};
    private final ExecutionTrace executionTrace = ExecutionTrace.getSingleton();
    private final InstrumentResult instrumentResult = InstrumentResult.getSingleton();
    private final TestSuite testSuite = new TestSuite();

    public TestGenerator() {
    }

    public static TestGenerator getSingleton() {
        return singleton;
    }

    public void generateResultCheckingTests(List<MethodExecution> snapshot) {
        logger.info("Start generating test cases");
        snapshot.stream()
                .distinct()
                .filter(this::canUseAsTargetExecution)
                .map(e ->
                {
                    logger.debug("Generating test case for execution " + e.toSimpleString());

                    ValueTestCase testCase = new ValueTestCase(getPackageForTestCase(e));
                    String callee = prepareAndGetCallee(e, testCase);
                    List<Stmt> params = prepareAndGetRequiredParams(e, testCase);
                    setUpMockedParamsAndCalls(e, testCase);
                    Stmt expected = prepareAndGetAssertVal(executionTrace.getVarDetailByID(e.getReturnValId()), testCase);
                    Stmt actual = new MethodInvStmt(callee, e.getMethodInvoked().getId(), params);
                    Class<?> returnType = executionTrace.getVarDetailByID(e.getReturnValId()).getType();
                    if (ClassUtils.isPrimitiveWrapper(returnType) && !(executionTrace.getVarDetailByID(e.getReturnValId()) instanceof EnumVarDetails)) {
                        expected = new CastStmt(expected.getResultVarDetailID(), ClassUtils.wrapperToPrimitive(returnType), expected);
                        actual = new CastStmt(actual.getResultVarDetailID(), ClassUtils.wrapperToPrimitive(returnType), actual);
                    }
                    testCase.setAssertion(new AssertStmt(expected, actual));
                    return testCase;
                })
                .forEach(testSuite::assignTestCase);
    }

    public void generateExceptionTests(List<MethodExecution> snapshot) {
        logger.info("Start generating test cases involving exceptions");
        snapshot.stream()
                .filter(e -> e.getReturnValId() == -1 && e.getExceptionClass() != null && !e.getExceptionClass().equals(UnrecognizableException.class) && e.isCanTest())
                .map(e -> {
                    logger.debug("Generating exception checking test case for execution " + e.toSimpleString());

                    ExceptionTestCase testCase = new ExceptionTestCase(getPackageForTestCase(e));
                    String callee = prepareAndGetCallee(e, testCase);
                    List<Stmt> params = prepareAndGetRequiredParams(e, testCase);
                    setUpMockedParamsAndCalls(e, testCase);
                    testCase.addStmt(new MethodInvStmt(callee, e.getMethodInvoked().getId(), params));
                    testCase.setExceptionClass(e.getExceptionClass());
                    return testCase;
                })
                .forEach(testSuite::assignTestCase);
    }


    public void output() throws IOException {
        logger.info("Outputting tests");
        Properties properties = Properties.getSingleton();
        int totalCases = 0;

        File file = new File(properties.getTestSourceDir());
        file.mkdirs();
        FileOutputStream writeStream;
        for (TestClass tc : testSuite.getTestClasses()) {
            File packageDir = new File(properties.getTestSourceDir(), tc.getPackageName().replace(".", File.separator));
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
        logger.info("Total no. of test cases generated: " + totalCases);
    }

    /**
     * @param execution Method execution
     * @return if the execution should be used as target for test generation
     */
    private boolean canUseAsTargetExecution(MethodExecution execution) {
        if (execution.getTest() == null || !execution.isCanTest() || execution.getReturnValId() == -1) return false;
        return shouldTestMethod(execution) && methodIsDirectlyCallable(execution) && canCheckReturnValue(execution);
    }

    /**
     * @param execution Method Execution
     * @return if the method executed should be tested
     * If it is a library method, it should NOT be tested
     */
    private boolean shouldTestMethod(MethodExecution execution) {
        MethodDetails details = execution.getMethodInvoked();
        if (instrumentResult.isLibMethod(details.getId())) return false;
        switch (details.getType()) {
            case MEMBER:
                if (Arrays.stream(SKIP_MEMBER_METHODS).anyMatch(s -> details.getName().equals(s))) return false;
                break;
            case STATIC:
                if (Arrays.stream(SKIP_STATIC_METHODS).anyMatch(s -> details.getName().equals(s))) return false;
                break;
        }
        return true;
    }

    /**
     * @param e MethodExecution
     * @return if the method execution can be directly called in test cases (e.g. if it is an override method, calling the method would actually be calling the override one
     */
    private boolean methodIsDirectlyCallable(MethodExecution e) {

        MethodDetails details = e.getMethodInvoked();
        try {
            // check if the method is actually called by subclass callee
            // if yes, they cannot be specified in test case and hence cannot be used as target
            Method method = details.getdClass().getDeclaredMethod(details.getName(), details.getParameterTypes().stream().map(Helper::sootTypeToClass).toArray(Class<?>[]::new));
            if (method.isBridge()) return false;
            if (e.getCalleeId() == -1) return true; // if no callee, no overriding problems
            if (e.getCallee().getType().equals(details.getdClass())) return true;
            VarDetail callee = e.getCallee();
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

    /**
     * @param e Method Execution
     * @return if the return value of the execution can be checked in test cases
     */
    private boolean canCheckReturnValue(MethodExecution e) {
        if (e.getReturnValId() == -1) return false;
        VarDetail returnVarDetail = executionTrace.getVarDetailByID(e.getReturnValId());
        Class<?> varDetailClass = returnVarDetail.getClass();
        if (varDetailClass.equals(ObjVarDetails.class)) return returnVarDetail.equals(executionTrace.getNullVar());
        if (varDetailClass.equals(ArrVarDetails.class)) {
            if (((ArrVarDetails) returnVarDetail).getComponents().size() == 0) return true;
            return StringUtils.countMatches(e.getMethodInvoked().getReturnSootType().toString(), "[]") == StringUtils.countMatches(returnVarDetail.getType().getSimpleName(), "[]") && ((ArrVarDetails) returnVarDetail).getLeaveType().stream().allMatch(ClassUtils::isPrimitiveOrWrapper);
        }
        if(varDetailClass.equals(MapVarDetails.class)) {
            if(((MapVarDetails) returnVarDetail).getKeyValuePairs().size() == 0) return true;
            return ((MapVarDetails) returnVarDetail).getKeyValuePairs().stream().flatMap(kvp -> Stream.of(kvp.getKey(), kvp.getValue())).map(executionTrace::getVarDetailByID).map(VarDetail::getType).allMatch(ClassUtils::isPrimitiveOrWrapper);
        }
        try {
            if(varDetailClass.equals(EnumVarDetails.class) && returnVarDetail.getType().equals(Class.class) && !accessibilityCheck(ClassUtils.getClass(((EnumVarDetails) returnVarDetail).getValue()), e.getRequiredPackage()))
            return false;
            if(varDetailClass.equals(EnumVarDetails.class) && !returnVarDetail.getType().equals(Class.class)) {
                if(returnVarDetail.getType().getPackage().getName().startsWith(Properties.getSingleton().getPUT()))
                    return instrumentResult.getClassPublicFieldsMap().getOrDefault(returnVarDetail.getType().getName(), new HashSet<>()).contains(((EnumVarDetails) returnVarDetail).getValue());
                else
                    return Modifier.isPublic(returnVarDetail.getType().getField(((EnumVarDetails) returnVarDetail).getValue()).getModifiers());
            }
        } catch (ClassNotFoundException | NoSuchFieldException ignored) {
        }
        return true;
    }

    private void setUpMockedParamsAndCalls(MethodExecution execution, TestCase testCase) {
        Set<MockOccurrence> mockOccurrences = new HashSet<>();
        setUpMockedParams(execution, testCase, mockOccurrences);
        createMockedParamCalls(testCase, mockOccurrences);
    }

    private void setUpMockedParams(MethodExecution execution, TestCase testCase, Set<MockOccurrence> mockOccurrences) {
        executionTrace.getChildren(execution.getID()).stream().map(executionTrace::getMethodExecutionByID)
                .filter(e -> !e.getMethodInvoked().isFieldAccess())
                .forEach(e -> {
                    if (e.getCalleeId() != -1 && e.getCallee() instanceof ObjVarDetails && testCase.getExistingMockedVar(e.getCallee()) != null) {
                        VarStmt mockedVar = testCase.getExistingMockedVar(e.getCallee());
                        if (e.getReturnValId() != -1) {
                            MockOccurrence occurrence = mockOccurrences.stream().filter(o -> o.sameCallInfo(mockedVar, e)).findAny().orElse(new MockOccurrence(mockedVar, e.getMethodInvoked(), e.getParams()));
                            mockOccurrences.add(occurrence);
                            occurrence.addReturnVar(e.getReturnValId());
                            VarDetail returnVal = executionTrace.getVarDetailByID(e.getReturnValId());
                            e.getParams().stream().map(executionTrace::getVarDetailByID).filter(this::isVarToMock).forEach(p ->
                                createMockVars(p, testCase));
                            if (isVarToMock(returnVal))
                                createMockVars(returnVal, testCase);

                        }
                        testCase.addOrUpdateMockedVar(executionTrace.getVarDetailByID(e.getResultThisId()), mockedVar);
                    } else {
                        setUpMockedParams(e, testCase, mockOccurrences);
                    }
                });
    }
    private boolean isVarToMock(VarDetail v) {
        return (v instanceof ArrVarDetails || v instanceof MapVarDetails || v instanceof ObjVarDetails) && !(v instanceof ObjVarDetails && v.equals(executionTrace.getNullVar())) && !(v instanceof ArrVarDetails && ((ArrVarDetails) v).getComponents().stream().map(executionTrace::getVarDetailByID).noneMatch(this::isVarToMock)) && !(v instanceof MapVarDetails && ((MapVarDetails) v).getKeyValuePairs().stream().flatMap(e -> Stream.of(e.getKey(), e.getValue())).map(executionTrace::getVarDetailByID).noneMatch(this::isVarToMock));
    }

    private void createMockVars(VarDetail v, TestCase testCase) {
         Stmt res = prepareAndGetConstantVar(v, testCase.getPackageName());
         if(res != null) return;
         if(v instanceof ArrVarDetails)
             ((ArrVarDetails) v).getComponents().stream().map(executionTrace::getVarDetailByID).forEach(p -> createMockVars(p, testCase));

         else if(v instanceof MapVarDetails)
             ((MapVarDetails) v).getKeyValuePairs().stream().flatMap(e -> Stream.of(e.getKey(), e.getValue())).map(executionTrace::getVarDetailByID).forEach(p -> createMockVars(p, testCase));

         else {
             Class<?> valType = getAccessibleSuperType(v.getType(), testCase.getPackageName());
             res = new VarStmt(valType, testCase.getNewVarID(), v.getID());
             testCase.addStmt(new AssignStmt(res, new MockInitStmt(valType)));
             testCase.addOrUpdateMockedVar(v, (VarStmt) res);
         }
    }
    private void createMockedParamCalls(TestCase testCase, Set<MockOccurrence> mockOccurrences) {
        mockOccurrences.forEach(m -> {
            List<Stmt> paramStmts = m.getParamVarID().stream().map(executionTrace::getVarDetailByID)
                    .map(p -> getCreatedOrConstantVar(p, testCase))
                    .collect(Collectors.toList());
            MethodInvStmt methodInvStmt = new MethodInvStmt(m.getMockedVar().getStmt(new HashSet<>()), m.getInovkedMethod().getId(), paramStmts);
            boolean skipChecking = m.getReturnVars().stream().map(executionTrace::getVarDetailByID).allMatch(p -> p instanceof EnumVarDetails && p.getType().equals(Class.class));
            testCase.addStmt(new MockCallRetStmt(methodInvStmt, m.getReturnVars().stream().map(executionTrace::getVarDetailByID).map(p -> getCreatedOrConstantVar(p, testCase)).collect(Collectors.toList()), skipChecking));
        });
    }

    private Stmt getCreatedOrConstantVar(VarDetail p, TestCase testCase) {
        Stmt varStmt = testCase.getExistingCreatedOrMockedVar(p);
        if (varStmt != null) return varStmt;
        varStmt = prepareAndGetConstantVar(p, testCase.getPackageName());
        if (varStmt != null) return varStmt;
        List<Stmt> components;
        if (p instanceof MapVarDetails) {
            components = ((MapVarDetails) p).getKeyValuePairs().stream().map(e -> new PairStmt(getCreatedOrConstantVar(executionTrace.getVarDetailByID(e.getKey()), testCase), getCreatedOrConstantVar(executionTrace.getVarDetailByID(e.getValue()), testCase))).collect(Collectors.toList());
            varStmt = new VarStmt(p.getType(), testCase.getNewVarID(  ), p.getID());
            testCase.addStmt(new AssignStmt(varStmt, new ConstructStmt(p.getID(), null, components)));
            testCase.addOrUpdateVar(p.getID(), (VarStmt) varStmt);
            return varStmt;
        } else if (p instanceof ArrVarDetails) {
            components = ((ArrVarDetails) p).getComponents().stream().map(e -> getCreatedOrConstantVar(executionTrace.getVarDetailByID(e), testCase)).collect(Collectors.toList());
            if (((ArrVarDetails) p).getComponents().stream().map(executionTrace::getVarDetailByID).noneMatch(c -> c.getType().isArray()) && ((ArrVarDetails) p).getComponents().size() < 25)
                return new ConstructStmt(p.getID(), null, components);
            else {
                varStmt = new VarStmt(p.getType(), testCase.getNewVarID(), p.getID());
                testCase.addStmt(new AssignStmt(varStmt, new ConstructStmt(p.getID(), null, components)));
                testCase.addOrUpdateVar(p.getID(), (VarStmt) varStmt);
                return varStmt;
            }
        }
        throw new IllegalArgumentException("VarDetail " + p.toDetailedString() + " provided cannot be used. ");
    }

    private String prepareAndGetCallee(MethodExecution target, TestCase testCase) {
        if (target.getCalleeId() != -1 && target.getCallee() instanceof ObjVarDetails)
            prepareCallee((ObjVarDetails) target.getCallee(), testCase);
        return getCalleeVarString(target, testCase);
    }

    private void prepareCallee(ObjVarDetails target, TestCase testCase) {

        Stack<MethodExecution> parentStack = executionTrace.getParentExeStack(target, true);
        if (parentStack == null) throw new IllegalArgumentException("Provided target's callee cannot be created");
        Set<ObjVarDetails> varsToCreate = new HashSet<>();
        // get all vars that should be stored for re-using
        varsToCreate.add(target);
        varsToCreate.addAll(parentStack.stream().filter(e -> e.getCalleeId() != -1).map(MethodExecution::getCallee).filter(c -> c instanceof ObjVarDetails).map(c -> (ObjVarDetails) c).collect(Collectors.toSet()));

        while (!parentStack.isEmpty()) {
            MethodExecution execution = parentStack.pop();
            MethodDetails details = execution.getMethodInvoked();

            String callee = getCalleeVarString(execution, testCase);
            List<Stmt> params = prepareAndGetRequiredParams(execution, testCase);
            setUpMockedParamsAndCalls(execution, testCase);
            Stmt invStmt = new MethodInvStmt(callee, details.getId(), params);
            if (execution.getCalleeId() != -1 && execution.getCallee() instanceof ObjVarDetails) {
                testCase.addOrUpdateVar(execution.getResultThisId(), testCase.getExistingVar(execution.getCallee()));
            }
            if (execution.getReturnValId() != -1 && execution.getReturnValId() != execution.getResultThisId()) {
                VarDetail returnVal = executionTrace.getVarDetailByID(execution.getReturnValId());
                Class<?> valType = getAccessibleSuperType(returnVal.getType(), testCase.getPackageName());
                if (returnVal instanceof ObjVarDetails && varsToCreate.contains(returnVal)) {
                    VarStmt returnValStmt = new VarStmt(valType, testCase.getNewVarID(), returnVal.getID());
                    if (!execution.getMethodInvoked().getReturnType().equals(valType)) {
                        invStmt = new CastStmt(returnVal.getID(), valType, invStmt);
                    }
                    testCase.addStmt(new AssignStmt(returnValStmt, invStmt));
                    testCase.addOrUpdateVar(returnVal.getID(), returnValStmt);
                    continue;
                }
                if(returnVal instanceof ArrVarDetails) {
                    Map<Integer, VarDetail> matches = IntStream.range(0, ((ArrVarDetails) returnVal).getComponents().size())
                            .mapToObj(i -> new AbstractMap.SimpleEntry<Integer, VarDetail>(i, executionTrace.getVarDetailByID(((ArrVarDetails) returnVal).getComponents().get(i))))
                            .filter(e -> e.getValue() instanceof ObjVarDetails && varsToCreate.contains(e.getValue()))
                            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

                    if(matches.size() > 0 ) {
                        VarStmt baseReturnValStmt = new VarStmt(valType, testCase.getNewVarID(), returnVal.getID());
                        if(!execution.getMethodInvoked().getReturnType().equals(valType))
                            invStmt = new CastStmt(returnVal.getID(), valType, invStmt);
                        testCase.addStmt(new AssignStmt(baseReturnValStmt, invStmt));
                        testCase.addOrUpdateVar(returnVal.getID(), baseReturnValStmt);

                        matches.entrySet().stream()
                                .forEach(e -> {
                                    Class<?> elementValType = getAccessibleSuperType(e.getValue().getType(), testCase.getPackageName());
                                    VarStmt elementStmt =  new VarStmt(elementValType, testCase.getNewVarID(), e.getValue().getID());
                                    testCase.addStmt(new AssignStmt(elementStmt, new CastStmt(e.getValue().getID(), elementValType, new ArrAcceessStmt(e.getValue().getID(), baseReturnValStmt, e.getKey(), returnVal.getType()))));
                                    testCase.addOrUpdateVar(e.getValue().getID(), elementStmt);

                                });

                        continue;
                    }
                }


            }
            if (execution.getMethodInvoked().getType().equals(METHOD_TYPE.CONSTRUCTOR)) {
                VarDetail resultThisVal = executionTrace.getVarDetailByID(execution.getResultThisId());
                if (resultThisVal instanceof ObjVarDetails && varsToCreate.contains(resultThisVal)) {
                    VarStmt returnValStmt = new VarStmt(resultThisVal.getType(), testCase.getNewVarID(), resultThisVal.getID());
                    testCase.addStmt(new AssignStmt(returnValStmt, invStmt));
                    testCase.addOrUpdateVar(resultThisVal.getID(), returnValStmt);
                    continue;
                }
                if(resultThisVal instanceof ArrVarDetails) {
                    Map<Integer, VarDetail> matches = IntStream.range(0, ((ArrVarDetails) resultThisVal).getComponents().size())
                            .mapToObj(i -> new AbstractMap.SimpleEntry<Integer, VarDetail>(i, executionTrace.getVarDetailByID(((ArrVarDetails) resultThisVal).getComponents().get(i))))
                            .filter(e -> e.getValue() instanceof ObjVarDetails && varsToCreate.contains(e.getValue()))
                            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                    Class<?> valType = getAccessibleSuperType(resultThisVal.getType(), testCase.getPackageName());

                    if(matches.size() > 0 ) {
                        VarStmt baseReturnValStmt = new VarStmt(valType, testCase.getNewVarID(), resultThisVal.getID());
                        testCase.addStmt(new AssignStmt(baseReturnValStmt, invStmt));
                        testCase.addOrUpdateVar(resultThisVal.getID(), baseReturnValStmt);

                        matches.entrySet().stream()
                                .forEach(e -> {
                                    Class<?> elementValType = getAccessibleSuperType(e.getValue().getType(), testCase.getPackageName());
                                    VarStmt elementStmt =  new VarStmt(elementValType, testCase.getNewVarID(), e.getValue().getID());
                                    testCase.addStmt(new AssignStmt(elementStmt, new CastStmt(e.getValue().getID(), elementValType, new ArrAcceessStmt(e.getValue().getID(), baseReturnValStmt, e.getKey(), resultThisVal.getType()))));
                                    testCase.addOrUpdateVar(e.getValue().getID(), elementStmt);

                                });

                        continue;
                    }
                }
            }
            testCase.addStmt(invStmt);

        }


    }

    private String getCalleeVarString(MethodExecution execution, TestCase testCase) {
        switch (execution.getMethodInvoked().getType()) {
            case CONSTRUCTOR:
                return "";
            case STATIC:
                return execution.getMethodInvoked().getDeclaringClass().getName().replace("$", ".");
            case MEMBER:
                if (execution.getCallee() instanceof ObjVarDetails) {
                    if (testCase.getExistingVar(execution.getCallee()) == null) {
                        logger.error(execution.getCallee().toDetailedString());
                        throw new RuntimeException("Illegal callee creation flow");
                    }
                    VarStmt originalCallee = testCase.getExistingVar(execution.getCallee());
                    String originalCalleeString = originalCallee.getStmt(new HashSet<>());
                    return originalCalleeString;
                }
                if (execution.getCallee() instanceof EnumVarDetails)
                    return (String) execution.getCallee().getGenValue();
            default:
                throw new RuntimeException("Illegal execution to reproduce");
        }
    }


    private List<Stmt> prepareAndGetRequiredParams(MethodExecution execution, TestCase testCase) {
        return execution.getParams().stream()
                .map(executionTrace::getVarDetailByID)
                .map(p -> prepareAndGetRequiredParam(p, testCase)
                ).collect(Collectors.toList());
    }

    private Stmt prepareAndGetRequiredParam(VarDetail p, TestCase testCase) {
        List<Stmt> components;
        if (testCase.getExistingMockedVar(p) != null) return testCase.getExistingMockedVar(p);
        Stmt varStmt = prepareAndGetConstantVar(p, testCase.getPackageName());
        if (varStmt != null) return varStmt;
        if (p instanceof MapVarDetails) {
            components = ((MapVarDetails) p).getKeyValuePairs().stream().map(e -> new PairStmt(prepareAndGetRequiredParam(executionTrace.getVarDetailByID(e.getKey()), testCase), prepareAndGetRequiredParam(executionTrace.getVarDetailByID(e.getValue()), testCase))).collect(Collectors.toList());
            varStmt = new VarStmt(p.getType(), testCase.getNewVarID(), p.getID());
            testCase.addStmt(new AssignStmt(varStmt, new ConstructStmt(p.getID(), null, components)));
            if (components.stream().allMatch(c -> c instanceof ConstantStmt))
                testCase.addOrUpdateVar(p.getID(), (VarStmt) varStmt);
            else testCase.addOrUpdateMockedVar(p, (VarStmt) varStmt);
            return varStmt;
        } else if (p instanceof ArrVarDetails) {
            components = ((ArrVarDetails) p).getComponents().stream().map(e -> prepareAndGetRequiredParam(executionTrace.getVarDetailByID(e), testCase)).collect(Collectors.toList());
            if (((ArrVarDetails) p).getComponents().stream().map(executionTrace::getVarDetailByID).noneMatch(c -> c.getType().isArray()) && ((ArrVarDetails) p).getComponents().size() < 25)
                return new ConstructStmt(p.getID(), null, components);
            else {
                varStmt = new VarStmt(p.getType(), testCase.getNewVarID(), p.getID());
                testCase.addStmt(new AssignStmt(varStmt, new ConstructStmt(p.getID(), null, components)));
                if (components.stream().allMatch(c -> c instanceof ConstantStmt))
                    testCase.addOrUpdateVar(p.getID(), (VarStmt) varStmt);
                else testCase.addOrUpdateMockedVar(p, (VarStmt) varStmt);

                return varStmt;
            }
        } else if(p instanceof ObjVarDetails && Helper.isCannotMockType(p.getType()) && executionTrace.getParentExeStack(p, true)!=null) {
            prepareCallee((ObjVarDetails) p, testCase);
            return getCreatedOrConstantVar(p, testCase);
        }
        else {
            createMockVars(p, testCase);
            return getCreatedOrConstantVar(p, testCase);
        }
    }

    private Stmt prepareAndGetAssertVal(VarDetail p, TestCase testCase) {
        List<Stmt> components;
        if (p instanceof ObjVarDetails && !executionTrace.getNullVar().equals(p))
            throw new IllegalArgumentException("VarDetail " + p.toDetailedString() + " provided cannot be asserted. ");
        Stmt varStmt = prepareAndGetConstantVar(p, testCase.getPackageName());
        if (varStmt != null) return varStmt;
        varStmt = testCase.getExistingVar(p);
        if (varStmt != null) return varStmt;
        if (p instanceof MapVarDetails) {
            components = ((MapVarDetails) p).getKeyValuePairs().stream().map(e -> new PairStmt(prepareAndGetAssertVal(executionTrace.getVarDetailByID(e.getKey()), testCase), prepareAndGetAssertVal(executionTrace.getVarDetailByID(e.getValue()), testCase))).collect(Collectors.toList());
            varStmt = new VarStmt(p.getType(), testCase.getNewVarID(), p.getID());
            testCase.addStmt(new AssignStmt(varStmt, new ConstructStmt(p.getID(), null, components)));
            testCase.addOrUpdateVar(p.getID(), (VarStmt) varStmt);
            return varStmt;
        } else if (p instanceof ArrVarDetails) {
            components = ((ArrVarDetails) p).getComponents().stream().map(e -> prepareAndGetAssertVal(executionTrace.getVarDetailByID(e), testCase)).collect(Collectors.toList());
            if (((ArrVarDetails) p).getComponents().stream().map(executionTrace::getVarDetailByID).noneMatch(c -> c.getType().isArray()) && ((ArrVarDetails) p).getComponents().size() < 25)
                return new ConstructStmt(p.getID(), null, components);
            else {
                varStmt = new VarStmt(p.getType(), testCase.getNewVarID(), p.getID());
                testCase.addStmt(new AssignStmt(varStmt, new ConstructStmt(p.getID(), null, components)));
                testCase.addOrUpdateVar(p.getID(), (VarStmt) varStmt);

                return varStmt;
            }
        }
        throw new IllegalArgumentException("VarDetail " + p.toDetailedString() + " provided cannot be asserted. ");
    }

    private Stmt prepareAndGetConstantVar(VarDetail v, String testPackage) {
        if (v.equals(executionTrace.getNullVar()))
            return new ConstantStmt(v.getID());
        else if (v instanceof PrimitiveVarDetails || v instanceof StringVarDetails || v instanceof WrapperVarDetails)
            return new ConstantStmt(v.getID());
        else if (v instanceof EnumVarDetails) {
            if(v.getType().equals(Class.class)){
                try {
                    Class<?> representingClass = ClassUtils.getClass(((EnumVarDetails) v).getValue());
                    Class<?> closestClass = getAccessibleSuperType(representingClass, testPackage);
                    if (!representingClass.equals(closestClass)) {
                        v = new EnumVarDetails(executionTrace.getNewVarID(), Class.class, closestClass.getName());
                        executionTrace.addNewVarDetail(v);
                    }
                } catch (ClassNotFoundException ignored) {
                }
            }
            return new ConstantStmt(v.getID());
        }
        else if (v instanceof StringBVarDetails)
            return new ConstantStmt(((StringBVarDetails) v).getStringValID());
        return null;
    }

    private String getPackageForTestCase(MethodExecution e) {
        if (!e.getRequiredPackage().isEmpty())
            return e.getRequiredPackage();
        if(e.getCalleeId() == -1 || ! (e.getCallee() instanceof ObjVarDetails))
            return e.getMethodInvoked().getDeclaringClass().getPackageName();
        return executionTrace.getParentExeStack(e.getCallee(), true)
                .stream().filter(ex -> ex.getMethodInvoked().getAccess().equals(ACCESS.PROTECTED))
                .findAny().map(ex -> ex.getMethodInvoked().getDeclaringClass().getPackageName()).orElse(e.getMethodInvoked().getDeclaringClass().getPackageName());
    }

    private static class MockOccurrence {
        private final VarStmt mockedVar;
        private final MethodDetails inovkedMethod;
        private final List<Integer> paramVarID;
        private final List<Integer> returnVars;

        public MockOccurrence(VarStmt mockedVar, MethodDetails inovkedMethod, List<Integer> paramVarID) {
            this.mockedVar = mockedVar;
            this.inovkedMethod = inovkedMethod;
            this.paramVarID = paramVarID;
            this.returnVars = new ArrayList<>();
        }

        public VarStmt getMockedVar() {
            return mockedVar;
        }

        public MethodDetails getInovkedMethod() {
            return inovkedMethod;
        }

        public List<Integer> getParamVarID() {
            return paramVarID;
        }

        public List<Integer> getReturnVars() {
            return returnVars;
        }

        public void addReturnVar(int varDetailID) {
            this.returnVars.add(varDetailID);
        }

        public boolean sameCallInfo(VarStmt mockedVar, MethodExecution execution) {
            return this.mockedVar.equals(mockedVar) && this.inovkedMethod.equals(execution.getMethodInvoked()) && this.paramVarID.equals(execution.getParams());
        }

        @Override
        public String toString() {
            return "MockOccurrence{" +
                    "mockedVar=" + mockedVar +
                    ", inovkedMethod=" + inovkedMethod +
                    ", paramVarID=" + paramVarID +
                    ", returnVars=" + returnVars +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            MockOccurrence that = (MockOccurrence) o;
            return Objects.equals(mockedVar, that.mockedVar) && Objects.equals(inovkedMethod, that.inovkedMethod) && Objects.equals(paramVarID, that.paramVarID) && Objects.equals(returnVars, that.returnVars);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mockedVar, inovkedMethod, paramVarID, returnVars);
        }
    }
}
