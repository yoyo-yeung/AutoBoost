package program.generation;

import entity.ACCESS;
import entity.METHOD_TYPE;
import helper.Helper;
import helper.Properties;
import helper.xml.XMLParser;
import org.apache.commons.lang3.ClassUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mockito.ArgumentMatchers;
import org.mockito.MockedStatic;
import program.analysis.MethodDetails;
import program.execution.ExecutionTrace;
import program.execution.MethodExecution;
import program.execution.stmt.*;
import program.execution.variable.*;
import program.generation.test.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static helper.Helper.getAccessibleMockableSuperType;
import static helper.Helper.getAccessibleSuperType;

public class TestGenerator {
    private static final Logger logger = LogManager.getLogger(TestGenerator.class);
    private static final TestGenerator singleton = new TestGenerator();
    private static final ExecutionTrace executionTrace = ExecutionTrace.getSingleton();
    private final ExecutionProcessor executionProcessor = new ExecutionProcessor();
    private final TestSuite testSuite = new TestSuite();
    private final ArgumentMatcherStmt argumentMatcherStmt = new ArgumentMatcherStmt(-1);

    public TestGenerator() {
    }

    public static TestGenerator getSingleton() {
        return singleton;
    }

    public void generateTestCases(List<MethodExecution> snapshot) {
        logger.info("Start generating test cases");
        snapshot.stream()
                .distinct()
                .filter(executionProcessor::testSetUp)
                .map(e -> {
                    if (executionProcessor.normalTestSetUp(e)) {
                        return generateResultCheckingTests(e);
                    } else if (executionProcessor.exceptionalTestSetUp(e)) {
                        return generateExceptionTests(e);
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .forEach(testSuite::assignTestCase);
    }

    public TestCase generateResultCheckingTests(MethodExecution e) {

        ValueTestCase testCase = new ValueTestCase();
        testCase.setPackageName(getPackageForTestCase(e));
        try {
            logger.info("Generating test case for execution " + e.toSimpleString());

            String callee = prepareAndGetCallee(e, testCase);
            testCase.keepOnlyTargetCalleeVar(e.getCalleeId());
            List<Stmt> params = prepareAndGetRequiredParams(e, testCase);
            setUpMockedParamsAndCalls(e, testCase);
            ExecutionChecker.constructObj(testCase, e, null, params.stream().map(Stmt::getResultVarDetailID).map(testCase::getObjForVar).toArray());
            testCase.setRecreated(executionProcessor.checkRecreationResult(testCase, e));
            if (!testCase.isRecreated()) {
                logger.error("Cannot recreate " + e.toSimpleString());
                return null;
            }
            Stmt expected = prepareAndGetAssertVal(executionTrace.getVarDetailByID(e.getReturnValId()), testCase);
            Stmt actual = new MethodInvStmt(callee, e.getMethodInvoked().getId(), params);
            Class<?> returnType = executionTrace.getVarDetailByID(e.getReturnValId()).getType();
            if (ClassUtils.isPrimitiveWrapper(returnType) && !ClassUtils.isPrimitiveWrapper(e.getMethodInvoked().getReturnType()))
                actual = new CastStmt(actual.getResultVarDetailID(), returnType, actual);
            if (ClassUtils.isPrimitiveWrapper(returnType)) {
                expected = new CastStmt(expected.getResultVarDetailID(), ClassUtils.wrapperToPrimitive(returnType), expected);
                actual = new CastStmt(actual.getResultVarDetailID(), ClassUtils.wrapperToPrimitive(returnType), actual);
            }
            testCase.setAssertion(new AssertStmt(expected, actual));
            return testCase;
        } catch (Exception exception) {
            logger.error(exception.getMessage());
            exception.printStackTrace();
            return null;
        }

    }

    public TestCase generateExceptionTests(MethodExecution e) {
        ExceptionTestCase testCase = new ExceptionTestCase();
        logger.info("Generating exception checking test case for execution " + e.toSimpleString());
        testCase.setPackageName(getPackageForTestCase(e));
        String callee = prepareAndGetCallee(e, testCase);
        testCase.keepOnlyTargetCalleeVar(e.getCalleeId());
        List<Stmt> params = prepareAndGetRequiredParams(e, testCase);
        setUpMockedParamsAndCalls(e, testCase);
        testCase.addStmt(new MethodInvStmt(callee, e.getMethodInvoked().getId(), params));
        testCase.setExceptionClass(e.getExceptionClass());
        return testCase;
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


    private void setUpMockedParamsAndCalls(MethodExecution execution, TestCase testCase) {
        Set<MockOccurrence> mockOccurrences = new HashSet<>();
        setUpMockedParams(execution, testCase, mockOccurrences);
        createMockedParamCalls(testCase, mockOccurrences);
    }

    private void setUpMockedParams(MethodExecution execution, TestCase testCase, Set<MockOccurrence> mockOccurrences) {
        setUpMockedParams(execution, testCase, mockOccurrences, new HashSet<>());
    }

    private void setUpMockedParams(MethodExecution execution, TestCase testCase, Set<MockOccurrence> mockOccurrences, Set<MethodExecution> covered) {
        if (covered.contains(execution)) return;
        covered.add(execution);
        executionTrace.getChildren(execution.getID()).stream().map(executionTrace::getMethodExecutionByID)
                .filter(e -> !e.getMethodInvoked().isFieldAccess())
                .forEach(e -> {
                    if (e.getCalleeId() == -1 && e.getReturnValId() != -1) {
                        MockOccurrence occurrence = mockOccurrences.stream().filter(o -> o.sameCallInfo(null, e)).findAny().orElse(new MockOccurrence(null, e.getMethodInvoked(), e.getParams()));
                        mockOccurrences.add(occurrence);
                        VarDetail returnVal = executionTrace.getVarDetailByID(e.getReturnValId());
                        if (isVarToMock(returnVal))
                            createMockVars(returnVal, testCase);
                        occurrence.addReturnVar(e.getReturnValId());
                    } else if (e.getCalleeId() != -1 && e.getCallee() instanceof ObjVarDetails && testCase.getExistingMockedVar(e.getCallee()) != null) {
                        VarStmt mockedVar = testCase.getExistingMockedVar(e.getCallee());
                        if (e.getReturnValId() != -1) {
                            MockOccurrence occurrence = mockOccurrences.stream().filter(o -> o.sameCallInfo(mockedVar, e)).findAny().orElse(new MockOccurrence(mockedVar, e.getMethodInvoked(), e.getParams()));
                            mockOccurrences.add(occurrence);
                            occurrence.addReturnVar(e.getReturnValId());
                            VarDetail returnVal = executionTrace.getVarDetailByID(e.getReturnValId());
                            Object[] paramsForChecking = e.getParams().stream().map(executionTrace::getVarDetailByID).map(p -> {
                                if (!isVarToMock(p))
                                    return testCase.getObjForVar(prepareConcreteValue(p, testCase).getResultVarDetailID());
                                else return ArgumentMatchers.any(p.getType());
                            }).toArray();
                            Object returnValToCheck = null;
                            if (isVarToMock(returnVal)) {
                                createMockVars(returnVal, testCase);
                                returnValToCheck = testCase.getObjForVar(returnVal.getID());
                            } else {
                                returnValToCheck = testCase.getObjForVar(prepareConcreteValue(returnVal, testCase).getResultVarDetailID());
                            }

                            ExecutionChecker.setMock(testCase.getObjForVar(e.getCalleeId()), e, paramsForChecking, returnValToCheck, testCase);
                        }
                        testCase.addOrUpdateMockedVar(executionTrace.getVarDetailByID(e.getResultThisId()), mockedVar);
                    } else {
                        setUpMockedParams(e, testCase, mockOccurrences, covered);
                    }
                });
    }

    private boolean isVarToMock(VarDetail v) {
        return (v instanceof ArrVarDetails || v instanceof MapVarDetails || v instanceof ObjVarDetails) && !(v instanceof ObjVarDetails && v.equals(executionTrace.getNullVar())) && !(v instanceof ArrVarDetails && ((ArrVarDetails) v).getComponents().stream().map(executionTrace::getVarDetailByID).noneMatch(this::isVarToMock)) && !(v instanceof MapVarDetails && ((MapVarDetails) v).getKeyValuePairs().stream().flatMap(e -> Stream.of(e.getKey(), e.getValue())).map(executionTrace::getVarDetailByID).noneMatch(this::isVarToMock));
    }

    private void createMockVars(VarDetail v, TestCase testCase) {
        Stmt res = prepareAndGetConstantVar(v, testCase.getPackageName(), testCase);
        if (res != null) return;
        res = testCase.getExistingCreatedOrMockedVar(v);
        if (res != null) return;

        if (v instanceof ArrVarDetails) {
            ((ArrVarDetails) v).getComponents().stream().map(executionTrace::getVarDetailByID).forEach(p -> createMockVars(p, testCase));

            ExecutionChecker.constructArr((ArrVarDetails) v, testCase);
        } else if (v instanceof MapVarDetails) {
            ((MapVarDetails) v).getKeyValuePairs().stream().flatMap(e -> Stream.of(e.getKey(), e.getValue())).map(executionTrace::getVarDetailByID).forEach(p -> createMockVars(p, testCase));

            ExecutionChecker.constructMap((MapVarDetails) v, testCase);
        } else {
            Class<?> valType = getAccessibleMockableSuperType(v.getType(), testCase.getPackageName());
            res = new VarStmt(valType, testCase.getNewVarID(), v.getID());
            testCase.addStmt(new AssignStmt(res, new MockInstanceInitStmt(valType)));
            testCase.addOrUpdateMockedVar(v, (VarStmt) res);
            ExecutionChecker.constructMock(v, testCase);
        }
    }

    private void createMockedParamCalls(TestCase testCase, Set<MockOccurrence> mockOccurrences) {
        mockOccurrences.forEach(m -> {
            List<Stmt> paramStmts = m.getParamVarID().stream().map(executionTrace::getVarDetailByID)
                    .map(p -> isVarToMock(p) ? argumentMatcherStmt : getCreatedOrConstantVar(p, testCase)
                    )
                    .collect(Collectors.toList());
            if (m.getInovkedMethod().getType().equals(METHOD_TYPE.STATIC)) {
                VarStmt varStmt = new VarStmt(MockedStatic.class, testCase.getNewVarID(), -1);
                AssignStmt assignStmt = new AssignStmt(varStmt, new MockStaticInitStmt(m.getInovkedMethod().getdClass()));
                boolean skipChecking = m.getReturnVars().stream().map(executionTrace::getVarDetailByID).allMatch(p -> p instanceof EnumVarDetails && p.getType().equals(Class.class));
                MethodInvStmt methodInvStmt = new MethodInvStmt(m.getInovkedMethod().getDeclaringClass().getName().replace("$", "."), m.getInovkedMethod().getId(), paramStmts);
                testCase.addStmt(new MockStaticCallStmts(varStmt, assignStmt, new MockCallRetStmt(methodInvStmt, m.getReturnVars().stream().map(executionTrace::getVarDetailByID).map(p -> getCreatedOrConstantVar(p, testCase)).collect(Collectors.toList()), skipChecking)));
                return;
            }
            MethodInvStmt methodInvStmt = new MethodInvStmt(m.getMockedVar().getStmt(new HashSet<>()), m.getInovkedMethod().getId(), paramStmts);
            boolean skipChecking = m.getReturnVars().stream().map(executionTrace::getVarDetailByID).allMatch(p -> p instanceof EnumVarDetails && p.getType().equals(Class.class));
            testCase.addStmt(new MockCallRetStmt(methodInvStmt, m.getReturnVars().stream().map(executionTrace::getVarDetailByID).map(p -> getCreatedOrConstantVar(p, testCase)).collect(Collectors.toList()), skipChecking));
//            ExecutionChecker.setMock(testCase.getObjForVar(m.));
        });
    }

    private Stmt getCreatedOrConstantVar(VarDetail p, TestCase testCase) {
        Stmt varStmt = testCase.getExistingCreatedOrMockedVar(p);
        if (varStmt != null) return varStmt;
        varStmt = prepareAndGetConstantVar(p, testCase.getPackageName(), testCase);
        if (varStmt != null) return varStmt;
        List<Stmt> components;
        if (p instanceof MapVarDetails) {
            components = ((MapVarDetails) p).getKeyValuePairs().stream().map(e -> new PairStmt(getCreatedOrConstantVar(executionTrace.getVarDetailByID(e.getKey()), testCase), getCreatedOrConstantVar(executionTrace.getVarDetailByID(e.getValue()), testCase))).collect(Collectors.toList());
            varStmt = new VarStmt(p.getType(), testCase.getNewVarID(), p.getID());
            testCase.addStmt(new AssignStmt(varStmt, new ConstructStmt(p.getID(), null, components)));
            testCase.addOrUpdateVar(p.getID(), (VarStmt) varStmt);
            ExecutionChecker.constructMap((MapVarDetails) p, testCase);
            return varStmt;
        } else if (p instanceof ArrVarDetails) {
            components = ((ArrVarDetails) p).getComponents().stream().map(e -> getCreatedOrConstantVar(executionTrace.getVarDetailByID(e), testCase)).collect(Collectors.toList());
            ExecutionChecker.constructArr((ArrVarDetails) p, testCase);
            if (((ArrVarDetails) p).getComponents().stream().map(executionTrace::getVarDetailByID).noneMatch(c -> c.getType().isArray()) && ((ArrVarDetails) p).getComponents().size() < 25)
                return new ConstructStmt(p.getID(), null, components);
            else {
                varStmt = new VarStmt(p.getType(), testCase.getNewVarID(), p.getID());
                testCase.addStmt(new AssignStmt(varStmt, new ConstructStmt(p.getID(), null, components)));
                testCase.addOrUpdateVar(p.getID(), (VarStmt) varStmt);
                return varStmt;
            }
        }
        return null;
    }

    private String prepareAndGetCallee(MethodExecution target, TestCase testCase) {
        if (target.getCalleeId() != -1 && target.getCallee() instanceof ObjVarDetails)
            preparePUTObj((ObjVarDetails) target.getCallee(), testCase);
        else if (target.getCalleeId() != -1)
            prepareAndGetConstantVar(target.getCallee(), testCase.getPackageName(), testCase);
        return getCalleeVarString(target, testCase);
    }

    private void preparePUTObj(ObjVarDetails target, TestCase testCase) {
        try {
            MethodExecution defExe = executionProcessor.getExeConstructingClass(target.getType(), true);
            if (defExe == null)
                throw new RuntimeException("Cannot create callee as def does not exist " + target.getID());
            MethodDetails details = defExe.getMethodInvoked();
            String callee = getCalleeVarString(defExe, testCase);
            List<Stmt> params = prepareAndGetRequiredParams(defExe, testCase);
            setUpMockedParamsAndCalls(defExe, testCase);
            Stmt invStmt = new MethodInvStmt(callee, details.getId(), params);
            VarStmt calleeVarStmt = new VarStmt(target.getType(), testCase.getNewVarID(), target.getID());
            testCase.addStmt(new AssignStmt(calleeVarStmt, invStmt));
            testCase.addOrUpdateVar(target.getID(), calleeVarStmt);
            ExecutionChecker.constructObj(testCase, defExe, target, params.stream().map(Stmt::getResultVarDetailID).map(testCase::getObjForVar).toArray());
            XMLParser.fromXMLtoContentMap(target, (String) target.getValue()).entrySet().stream().forEach(e -> {
                if (!canCreateField(e.getValue())) return;
                Stmt fieldVal = prepareConcreteValue(e.getValue(), testCase);
                FieldSetStmt setStmt = new FieldSetStmt(calleeVarStmt, e.getKey().getKey(), e.getKey().getValue(), fieldVal);
                testCase.addStmt(setStmt);
                ExecutionChecker.setField(testCase.getObjForVar(target.getID()), e.getKey().getKey(), e.getKey().getValue(), testCase.getObjForVar(fieldVal.getResultVarDetailID()));
            });

        } catch (Exception e) {
            logger.error(e.getMessage());
            e.printStackTrace();
        }

    }

    private boolean canCreateField(VarDetail p) {
        if (p instanceof StringVarDetails || p instanceof StringBVarDetails || p instanceof WrapperVarDetails || p instanceof PrimitiveVarDetails || p.equals(ExecutionTrace.getSingleton().getNullVar()) || p instanceof MockVarDetails || p instanceof EnumVarDetails)
            return true;
        boolean res = false;
        if (p instanceof ArrVarDetails)
            res = ((ArrVarDetails) p).getComponents().stream().map(executionTrace::getVarDetailByID).allMatch(this::canCreateField);
        if (p instanceof MapVarDetails)
            res = ((MapVarDetails) p).getKeyValuePairs().stream().flatMap(e -> Stream.of(e.getKey(), e.getValue())).map(executionTrace::getVarDetailByID).allMatch(this::canCreateField);
        if (p instanceof ObjVarDetails)
            res = executionTrace.getParentExeStack(p, true) != null || executionProcessor.getExeConstructingClass(p.getType(), true) != null;
        return res;
    }

    private Stmt prepareConcreteValue(VarDetail p, TestCase testCase) {
        Stmt varStmt = prepareAndGetConstantVar(p, testCase.getPackageName(), testCase);
        if (varStmt != null) return varStmt;
        varStmt = testCase.getExistingVar(p);
        if (varStmt != null) return varStmt;
        if (p instanceof MockVarDetails) {
            createMockVars(p, testCase);
            varStmt = getCreatedOrConstantVar(p, testCase);
        }
        if (varStmt != null) return varStmt;
        if (p instanceof ObjVarDetails) {
            prepareObjVar((ObjVarDetails) p, testCase);
            varStmt = testCase.getExistingVar(p);
        } else if (p instanceof MapVarDetails) {
            varStmt = new VarStmt(p.getType(), testCase.getNewVarID(), p.getID());

            testCase.addStmt(new AssignStmt(varStmt, new ConstructStmt(p.getID(), null, ((MapVarDetails) p).getKeyValuePairs().stream().map(e -> new PairStmt(prepareConcreteValue(executionTrace.getVarDetailByID(e.getKey()), testCase), prepareConcreteValue(executionTrace.getVarDetailByID(e.getValue()), testCase))).collect(Collectors.toList()))));
            testCase.addOrUpdateVar(p.getID(), (VarStmt) varStmt);
            ExecutionChecker.constructMap((MapVarDetails) p, testCase);
        } else if (p instanceof ArrVarDetails) {
            varStmt = new VarStmt(p.getType(), testCase.getNewVarID(), p.getID());
            testCase.addStmt(new AssignStmt(varStmt, new ConstructStmt(p.getID(), null, ((ArrVarDetails) p).getComponents().stream().map(e -> prepareConcreteValue(executionTrace.getVarDetailByID(e), testCase)).collect(Collectors.toList()))));
            testCase.addOrUpdateVar(p.getID(), (VarStmt) varStmt);
            ExecutionChecker.constructArr((ArrVarDetails) p, testCase);
        } else if (p instanceof MockVarDetails) {
            varStmt = new VarStmt(p.getType(), testCase.getNewVarID(), p.getID());
            testCase.addStmt(new AssignStmt(varStmt, new MockInstanceInitStmt(p.getType())));
            testCase.addOrUpdateMockedVar(p, (VarStmt) varStmt);
            ExecutionChecker.constructMock(p, testCase);
        } else logger.debug(p);
        return varStmt;
    }

    private void prepareObjVar(ObjVarDetails target, TestCase testCase) {
//        if(Helper.isCannotMockType(target.getType())) throw new RuntimeException("Callee is not from PUT, generation stopped");
        if (executionTrace.getParentExeStack(target, true) != null) prepareNonPUTObj(target, testCase);
        else preparePUTObj(target, testCase);
    }

    private void prepareNonPUTObj(ObjVarDetails target, TestCase testCase) {

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
                if (returnVal instanceof ArrVarDetails) {
                    Map<Integer, VarDetail> matches = IntStream.range(0, ((ArrVarDetails) returnVal).getComponents().size())
                            .mapToObj(i -> new AbstractMap.SimpleEntry<Integer, VarDetail>(i, executionTrace.getVarDetailByID(((ArrVarDetails) returnVal).getComponents().get(i))))
                            .filter(e -> e.getValue() instanceof ObjVarDetails && varsToCreate.contains(e.getValue()))
                            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

                    if (matches.size() > 0) {
                        VarStmt baseReturnValStmt = new VarStmt(valType, testCase.getNewVarID(), returnVal.getID());
                        if (!execution.getMethodInvoked().getReturnType().equals(valType))
                            invStmt = new CastStmt(returnVal.getID(), valType, invStmt);
                        testCase.addStmt(new AssignStmt(baseReturnValStmt, invStmt));
                        testCase.addOrUpdateVar(returnVal.getID(), baseReturnValStmt);

                        matches.entrySet().stream()
                                .forEach(e -> {
                                    Class<?> elementValType = getAccessibleSuperType(e.getValue().getType(), testCase.getPackageName());
                                    VarStmt elementStmt = new VarStmt(elementValType, testCase.getNewVarID(), e.getValue().getID());
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
                if (resultThisVal instanceof ArrVarDetails) {
                    Map<Integer, VarDetail> matches = IntStream.range(0, ((ArrVarDetails) resultThisVal).getComponents().size())
                            .mapToObj(i -> new AbstractMap.SimpleEntry<Integer, VarDetail>(i, executionTrace.getVarDetailByID(((ArrVarDetails) resultThisVal).getComponents().get(i))))
                            .filter(e -> e.getValue() instanceof ObjVarDetails && varsToCreate.contains(e.getValue()))
                            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                    Class<?> valType = getAccessibleSuperType(resultThisVal.getType(), testCase.getPackageName());

                    if (matches.size() > 0) {
                        VarStmt baseReturnValStmt = new VarStmt(valType, testCase.getNewVarID(), resultThisVal.getID());
                        testCase.addStmt(new AssignStmt(baseReturnValStmt, invStmt));
                        testCase.addOrUpdateVar(resultThisVal.getID(), baseReturnValStmt);

                        matches.entrySet().stream()
                                .forEach(e -> {
                                    Class<?> elementValType = getAccessibleSuperType(e.getValue().getType(), testCase.getPackageName());
                                    VarStmt elementStmt = new VarStmt(elementValType, testCase.getNewVarID(), e.getValue().getID());
                                    testCase.addStmt(new AssignStmt(elementStmt, new CastStmt(e.getValue().getID(), elementValType, new ArrAcceessStmt(e.getValue().getID(), baseReturnValStmt, e.getKey(), resultThisVal.getType()))));
                                    testCase.addOrUpdateVar(e.getValue().getID(), elementStmt);

                                });

                        continue;
                    }
                }
            }
            testCase.addStmt(invStmt);
            ExecutionChecker.constructObj(testCase, execution, executionTrace.getVarDetailByID(execution.getResultThisId()), params.stream().map(Stmt::getResultVarDetailID).map(testCase::getObjForVar).toArray());
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
        Stmt varStmt = prepareAndGetConstantVar(p, testCase.getPackageName(), testCase);
        if (varStmt != null) return varStmt;
        if (p instanceof MapVarDetails) {
            components = ((MapVarDetails) p).getKeyValuePairs().stream().map(e -> new PairStmt(prepareAndGetRequiredParam(executionTrace.getVarDetailByID(e.getKey()), testCase), prepareAndGetRequiredParam(executionTrace.getVarDetailByID(e.getValue()), testCase))).collect(Collectors.toList());
            varStmt = new VarStmt(p.getType(), testCase.getNewVarID(), p.getID());
            testCase.addStmt(new AssignStmt(varStmt, new ConstructStmt(p.getID(), null, components)));
            if (components.stream().allMatch(c -> c instanceof ConstantStmt))
                testCase.addOrUpdateVar(p.getID(), (VarStmt) varStmt);
            else testCase.addOrUpdateMockedVar(p, (VarStmt) varStmt);
            ExecutionChecker.constructMap((MapVarDetails) p, testCase);
            return varStmt;
        } else if (p instanceof ArrVarDetails) {
            components = ((ArrVarDetails) p).getComponents().stream().map(e -> prepareAndGetRequiredParam(executionTrace.getVarDetailByID(e), testCase)).collect(Collectors.toList());
            ExecutionChecker.constructArr((ArrVarDetails) p, testCase);
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
        } else if (p instanceof ObjVarDetails && Helper.isCannotMockType(p.getType()) && executionTrace.getParentExeStack(p, true) != null) {
            prepareConcreteValue(p, testCase);
            return getCreatedOrConstantVar(p, testCase);
        } else {
            createMockVars(p, testCase);
            return getCreatedOrConstantVar(p, testCase);
        }
    }

    private Stmt prepareAndGetAssertVal(VarDetail p, TestCase testCase) {
        List<Stmt> components;
        if (p instanceof ObjVarDetails && !executionTrace.getNullVar().equals(p))
            throw new IllegalArgumentException("VarDetail " + p.toDetailedString() + " provided cannot be asserted. ");
        Stmt varStmt = prepareAndGetConstantVar(p, testCase.getPackageName(), testCase);
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

    private Stmt prepareAndGetConstantVar(VarDetail v, String testPackage, TestCase testCase) {
        if (v.equals(executionTrace.getNullVar())) {
            testCase.addObjForVar(v.getID(), null);
            return new ConstantStmt(v.getID());
        } else if (v instanceof PrimitiveVarDetails || v instanceof StringVarDetails || v instanceof WrapperVarDetails) {
            ExecutionChecker.constructPrimWrapOrString(v, testCase);
            return new ConstantStmt(v.getID());
        } else if (v instanceof EnumVarDetails) {
            if (v.getType().equals(Class.class)) {
                try {
                    Class<?> representingClass = ClassUtils.getClass(((EnumVarDetails) v).getValue());
                    Class<?> closestClass = getAccessibleSuperType(representingClass, testPackage);
                    if (!representingClass.equals(closestClass)) {
                        v = new EnumVarDetails(ExecutionTrace.getNewVarID(), Class.class, closestClass.getName());
                        executionTrace.addNewVarDetail(v);
                    }
                } catch (ClassNotFoundException ignored) {
                    logger.error(ignored.getMessage());
                }
            }

            ExecutionChecker.constructEnum((EnumVarDetails) v, testCase);
            return new ConstantStmt(v.getID());
        } else if (v instanceof StringBVarDetails) {
            ExecutionChecker.constructStringB((StringBVarDetails) v, testCase);
            return new ConstructStmt(v.getID(), null, Collections.singletonList(new ConstantStmt(((StringBVarDetails) v).getStringValID())));
        }
        return null;
    }

    private String getPackageForTestCase(MethodExecution e) {
        if (!e.getRequiredPackage().isEmpty())
            return e.getRequiredPackage();
        if (e.getCalleeId() == -1 || !(e.getCallee() instanceof ObjVarDetails))
            return e.getMethodInvoked().getDeclaringClass().getPackageName();
        return executionProcessor.getExeConstructingClass(e.getCallee().getType(), true).getMethodInvoked().getAccess().equals(ACCESS.PROTECTED) ? executionProcessor.getExeConstructingClass(e.getCallee().getType(), true).getMethodInvoked().getDeclaringClass().getPackageName() : e.getMethodInvoked().getDeclaringClass().getPackageName();
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
            List<VarDetail> concreteParams = this.paramVarID.stream().map(executionTrace::getVarDetailByID).map(p -> singleton.isVarToMock(p) ? null : p).collect(Collectors.toList());
            List<VarDetail> concreteExeParams = execution.getParams().stream().map(executionTrace::getVarDetailByID).map(p -> singleton.isVarToMock(p) ? null : p).collect(Collectors.toList());
            return (Objects.equals(this.mockedVar, mockedVar)) && this.inovkedMethod.equals(execution.getMethodInvoked()) && concreteParams.equals(concreteExeParams);
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
