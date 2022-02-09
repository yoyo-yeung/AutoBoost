package program.generation;

import entity.ACCESS;
import entity.METHOD_TYPE;
import helper.Properties;
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class TestGenerator {
    private static final Logger logger = LogManager.getLogger(TestGenerator.class);
    private static final TestGenerator singleton = new TestGenerator();
    private static final String[] skipMethods = {"equals", "toString", "hashCode"};
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
                    MethodDetails details = instrumentResult.getMethodDetailByID(e.getMethodInvokedId());
                    return Arrays.stream(skipMethods).noneMatch(s -> details.getName().equals(s)) || !details.getType().equals(METHOD_TYPE.MEMBER);
                })
                .filter(e -> e.getTest() != null)
                .filter(e -> e.getReturnValId() != -1 && (executionTrace.getDefExeList(e.getReturnValId()) == null || !executionTrace.getDefExeList(e.getReturnValId()).equals(e.getID())) && exeCanBeTested(e.getID(), 0, -1, new HashSet<>()))// prevent self checking
                .filter(e -> {
                    VarDetail returnVarDetail = executionTrace.getVarDetailByID(e.getReturnValId());
                    return (!(returnVarDetail instanceof ObjVarDetails) || returnVarDetail.equals(executionTrace.getNullVar())) && (!returnVarDetail.getType().isArray() || StringUtils.countMatches(instrumentResult.getMethodDetailByID(e.getMethodInvokedId()).getReturnType(), "[]") == StringUtils.countMatches(returnVarDetail.getType().getSimpleName(), "[]"));
                })
                .map(e ->
                {
                    ValueTestCase testCase = new ValueTestCase();
                    MethodInvStmt invStmt = getMethodInvStmt(e, testCase);
                    VarStmt returnValStmt = new VarStmt(instrumentResult.getMethodDetailByID(e.getMethodInvokedId()).getReturnType(), executionTrace.getVarDetailByID(e.getReturnValId()).getType(), testCase.getNewVarID(), e.getReturnValId());
                    testCase.addStmt(new AssignStmt(returnValStmt, invStmt));
                    testCase.setAssertion(new AssertStmt(generateDefStmt(e.getReturnValId(), testCase, false, true), returnValStmt));
                    this.coveredExecutions.add(e);
                    return testCase;
                }).forEach(testSuite::assignTestCase);

    }

    public void generateExceptionTests(List<MethodExecution> snapshot) {
        snapshot.stream()
                .filter(e -> e.getReturnValId() == -1 && e.getExceptionClass() != null && exeCanBeTested(e.getID(), 0, -1, new HashSet<>()))
                .map(e -> {
                    ExceptionTestCase testCase = new ExceptionTestCase();
                    MethodInvStmt invStmt = getMethodInvStmt(e, testCase);
                    testCase.addStmt(invStmt);
                    testCase.setExceptionClass(e.getExceptionClass());
                    testCase.output();
                    return testCase;
                }).forEach(testSuite::assignTestCase);
    }

    public boolean exeCanBeTested(Integer methodExecutionID, int lv, int defedVar, Set<Integer> exeUnderCheck) {
        MethodExecution execution = executionTrace.getMethodExecutionByID(methodExecutionID);
        MethodDetails details = instrumentResult.getMethodDetailByID(execution.getMethodInvokedId());
        if (passedExe.contains(methodExecutionID))
            return true;

        if (exeUnderCheck.contains(methodExecutionID)) {
            return false;
        }
        if (!execution.isReproducible()) {
            return false;
        }
        if (failedExe.contains(methodExecutionID)) {
            return false;
        }
        if (containsFaultyDef(methodExecutionID)) {
            failedExe.add(methodExecutionID);
            return false;
        }
        if (details.getType().equals(METHOD_TYPE.STATIC_INITIALIZER)) {
            failedExe.add(methodExecutionID);
            return false;
        }
        if (details.getAccess().equals(ACCESS.PRIVATE) || (details.getAccess().equals(ACCESS.PROTECTED) && !details.getDeclaringClass().getPackageName().equals(Properties.getSingleton().getGeneratedPackage()))) {
            failedExe.add(methodExecutionID);
            return false;
        }
        if (details.getName().startsWith("access$")) {
            failedExe.add(methodExecutionID);
            return false;
        }
        if ((details.getType().equals(METHOD_TYPE.CONSTRUCTOR) && details.getDeclaringClass().isAbstract()) || details.getDeclaringClass().isPrivate() || (!details.getDeclaringClass().isPrivate() && !details.getDeclaringClass().isPublic() && !details.getDeclaringClass().getPackageName().equals(Properties.getSingleton().getGeneratedPackage()))) {
            failedExe.add(methodExecutionID);
            return false;
        }
        if (execution.getCalleeId() != -1 && execution.getCalleeId() == defedVar) {
            failedExe.add(methodExecutionID);
            return false;
        }

        if (execution.getParams().contains(defedVar)) {
            failedExe.add(methodExecutionID);
            return false;
        }
        exeUnderCheck.add(methodExecutionID);
        if (execution.getCalleeId() != -1 && !varCanBeTested(execution.getCalleeId(), lv + 1, exeUnderCheck)) {
            failedExe.add(methodExecutionID);
            exeUnderCheck.remove(methodExecutionID);
            return false;
        }
        if (execution.getParams().stream().anyMatch(p -> !varCanBeTested(p, lv + 1, exeUnderCheck))) {
            failedExe.add(methodExecutionID);
            exeUnderCheck.remove(methodExecutionID);
            return false;
        }
        if (execution.getReturnValId() != -1 && execution.getReturnValId() != defedVar && !varCanBeTested(execution.getReturnValId(), lv + 1, exeUnderCheck)) {
            failedExe.add(methodExecutionID);
            exeUnderCheck.remove(methodExecutionID);
            return false;
        }
        passedExe.add(methodExecutionID);
        exeUnderCheck.remove(methodExecutionID);
        return true;
    }

    private boolean memberClassCheck(Class<?> memberClass) {
        int modifier = memberClass.getModifiers();
        if (Modifier.isPrivate(modifier) || (!Modifier.isPrivate(modifier) && !Modifier.isPublic(modifier) && !memberClass.getPackage().getName().equals(Properties.getSingleton().getGeneratedPackage())) || ((memberClass.isMemberClass() || memberClass.isLocalClass()) && !Modifier.isStatic(modifier)))
            return false;
        if (memberClass.isAnonymousClass()) return false;
        if (memberClass.isMemberClass() || memberClass.isLocalClass())
            return memberClass.getEnclosingClass().getPackage().getName().startsWith("java") || memberClassCheck(memberClass.getEnclosingClass());
        else return true;

    }

    private boolean varCanBeTested(Integer varID, int lv, Set<Integer> exeUnderCheck) {
        VarDetail varDetail = executionTrace.getVarDetailByID(varID);

        if (passedVar.contains(varID)) return true;
        if (varDetail instanceof PrimitiveVarDetails || varDetail instanceof WrapperVarDetails || varDetail instanceof StringVarDetails || varDetail instanceof EnumVarDetails || varDetail.equals(executionTrace.getNullVar())) {
            passedVar.add(varID);
            return true;
        } else if (varDetail instanceof MapVarDetails) {
            if (((MapVarDetails) varDetail).getKeyValuePairs().entrySet().stream().allMatch(e -> varCanBeTested(e.getKey(), lv + 1, exeUnderCheck) && varCanBeTested(e.getValue(), lv + 1, exeUnderCheck))) {
                passedVar.add(varID);
                passedVar.addAll(((MapVarDetails) varDetail).getKeyValuePairs().keySet());
                passedVar.addAll(((MapVarDetails) varDetail).getKeyValuePairs().values());
                return true;
            }
            return false;
        } else if (varDetail instanceof ArrVarDetails) {
            if (((ArrVarDetails) varDetail).getComponents().stream().allMatch(varID1 -> varCanBeTested(varID1, lv + 1, exeUnderCheck))) {
                passedVar.add(varID);
                passedVar.addAll(((ArrVarDetails) varDetail).getComponents());
                return true;
            }
            return false;
        } else if (!varDetail.equals(ExecutionTrace.getSingleton().getNullVar()) && (((varDetail.getType().isLocalClass() || varDetail.getType().isMemberClass()) && !memberClassCheck(varDetail.getType())) || varDetail.getType().isAnonymousClass() || varDetail.getType().getName().contains("$") || executionTrace.getDefExeList(varID) == null || !exeCanBeTested(executionTrace.getDefExeList(varID), lv + 1, varID, exeUnderCheck))) {
            return false;
        }
        passedVar.add(varID);
        return true;
    }

    private boolean containsFaultyDef(Integer exeID) {
        MethodExecution execution = executionTrace.getMethodExecutionByID(exeID);
        return Properties.getSingleton().getFaultyFunc().stream().anyMatch(s -> s.equals(instrumentResult.getMethodDetailByID(execution.getMethodInvokedId()).getSignature()) || executionTrace.getAllChildren(exeID).stream().anyMatch(e -> instrumentResult.getMethodDetailByID(executionTrace.getMethodExecutionByID(e).getMethodInvokedId()).getSignature().equals(s)));
    }

    public Stmt generateDefStmt(Integer varDetailsID, TestCase testCase, boolean checkExisting, boolean store) throws IllegalArgumentException {
        VarDetail varDetail = executionTrace.getVarDetailByID(varDetailsID);
        if (checkExisting && testCase.getExistingVar(varDetailsID) != null && testCase.getExistingVar(varDetailsID).size() > 0)
            return testCase.getExistingVar(varDetailsID).get(0);
        if (varDetail.equals(executionTrace.getNullVar()))
            return new ConstantStmt(varDetailsID);
        if (varDetail instanceof PrimitiveVarDetails || varDetail instanceof WrapperVarDetails || varDetail instanceof StringVarDetails || varDetail instanceof MapVarDetails || varDetail instanceof ArrVarDetails || varDetail instanceof EnumVarDetails)
            return getSingleDefStmt(varDetail, testCase);
        else if (executionTrace.getDefExeList(varDetail.getID()) == null) {
            logger.debug(varDetail.toDetailedString());
            throw new IllegalArgumentException("Missing def list");
        } else return getComplexDefStmt(varDetail, testCase);
    }

    private Stmt getSingleDefStmt(VarDetail varDetail, TestCase testCase) {
        if (executionTrace.getDefExeList(varDetail.getID()) != null || !(varDetail instanceof PrimitiveVarDetails || varDetail instanceof WrapperVarDetails || varDetail instanceof StringVarDetails || varDetail instanceof MapVarDetails || varDetail instanceof ArrVarDetails || varDetail instanceof EnumVarDetails))
            throw new IllegalArgumentException("Provided VarDetail cannot be assigned with single stmt");
        if (varDetail instanceof PrimitiveVarDetails || varDetail instanceof WrapperVarDetails || varDetail instanceof StringVarDetails || varDetail instanceof EnumVarDetails)
            return new ConstantStmt(varDetail.getID());
        List<Stmt> params;
        if (varDetail instanceof MapVarDetails) {
            params = ((MapVarDetails) varDetail).getKeyValuePairs().entrySet().stream().map(e -> new PairStmt(generateDefStmt(e.getKey(), testCase, true, true), generateDefStmt(e.getValue(), testCase, true, true))).collect(Collectors.toList());
        } else {
            params = ((ArrVarDetails) varDetail).getComponents().stream().map(e -> generateDefStmt(e, testCase, true, true)).collect(Collectors.toList());
            if (((ArrVarDetails) varDetail).getComponents().stream().map(executionTrace::getVarDetailByID).noneMatch(e -> e.getType().isArray()) && ((ArrVarDetails) varDetail).getComponents().size() <= 25)
                return new ConstructStmt(varDetail.getID(), null, params);
            else {
                VarStmt varStmt = new VarStmt(varDetail.getTypeSimpleName(), varDetail.getType(), testCase.getNewVarID(), varDetail.getID());
                testCase.addStmt(new AssignStmt(varStmt, new ConstructStmt(varDetail.getID(), null, params)));
                testCase.addOrUpdateVar(varDetail.getID(), varStmt);
                return varStmt;
            }
        }
        VarStmt varStmt = new VarStmt(varDetail.getType().toString(), varDetail.getType(), testCase.getNewVarID(), varDetail.getID());
        testCase.addStmt(new AssignStmt(varStmt, new ConstructStmt(varDetail.getID(), null, params)));
        testCase.addOrUpdateVar(varDetail.getID(), varStmt);
        return varStmt;
    }

    private MethodInvStmt getMethodInvStmt(MethodExecution execution, TestCase testCase) {
        MethodDetails details = instrumentResult.getMethodDetailByID(execution.getMethodInvokedId());
        MethodInvStmt invokeStmt = null;
        VarStmt varStmt;
        switch (details.getType()) {
            case STATIC:
                invokeStmt = new MethodInvStmt(details.getDeclaringClass().getShortName(), details.getId(), execution.getParams().stream().map(p -> generateDefStmt(p, testCase, true, true)).collect(Collectors.toList()));
                break;
            case CONSTRUCTOR:
                invokeStmt = new MethodInvStmt("", details.getId(), execution.getParams().stream().map(p -> generateDefStmt(p, testCase, true, true)).collect(Collectors.toList()));
                break;
            case MEMBER:
                varStmt = (VarStmt) generateDefStmt(execution.getCalleeId(), testCase, true, true);
                invokeStmt = new MethodInvStmt(varStmt.getStmt(), details.getId(), execution.getParams().stream().map(p -> generateDefStmt(p, testCase, true, true)).collect(Collectors.toList()));

                testCase.removeVar(execution.getCalleeId(), varStmt);
                testCase.addOrUpdateVar(execution.getResultThisId(), varStmt);
                break;
        }
        return invokeStmt;
    }

    private VarStmt getComplexDefStmt(VarDetail varDetail, TestCase testCase) {
        int varDetailID = varDetail.getID();
        int e = executionTrace.getDefExeList(varDetailID);
        MethodExecution execution = executionTrace.getMethodExecutionByID(e);
        MethodDetails details = instrumentResult.getMethodDetailByID(execution.getMethodInvokedId());
        MethodInvStmt invokeStmt = getMethodInvStmt(execution, testCase);
        VarStmt varStmt = null;
        switch (details.getType()) {
            case STATIC: // must be return value
                if (details.getReturnType().equalsIgnoreCase("void"))
                    testCase.addStmt(invokeStmt);
                else {
                    List<VarStmt> availableStmts = testCase.getExistingVar(execution.getReturnValId());
                    varStmt = availableStmts == null || availableStmts.size() == 0 ? null : availableStmts.get(0);
                    if (varStmt == null)
                        varStmt = new VarStmt(details.getReturnType(), executionTrace.getVarDetailByID(execution.getReturnValId()).getType(), testCase.getNewVarID(), execution.getReturnValId());
                    testCase.addStmt(new AssignStmt(varStmt, invokeStmt));
                    testCase.addOrUpdateVar(execution.getReturnValId(), varStmt);
                }
                break;
            case CONSTRUCTOR:
                varStmt = new VarStmt(details.getDeclaringClass().getName(), executionTrace.getVarDetailByID(execution.getResultThisId()).getType(), testCase.getNewVarID(), execution.getResultThisId());
                testCase.addStmt(new AssignStmt(varStmt, invokeStmt));
                testCase.addOrUpdateVar(execution.getResultThisId(), varStmt);
                break;
            case MEMBER:
                varStmt = (VarStmt) generateDefStmt(execution.getCalleeId(), testCase, true, true);
                if (!details.getReturnType().equalsIgnoreCase("void")) {
                    VarStmt varStmt1 = new VarStmt(details.getReturnType(), executionTrace.getVarDetailByID(execution.getReturnValId()).getType(), testCase.getNewVarID(), execution.getReturnValId());
                    testCase.addStmt(new AssignStmt(varStmt1, invokeStmt));
                    testCase.addOrUpdateVar(execution.getReturnValId(), varStmt1);
                } else
                    testCase.addStmt(invokeStmt);
                break;
        }
        return testCase.getExistingVar(varDetailID).get(0);
    }


    public void output() throws IOException {
        Properties properties = Properties.getSingleton();
        File packageDir = new File(properties.getTestSourceDir(), properties.getGeneratedPackage().replace(".", File.separator));
        if (!packageDir.exists() || !packageDir.isDirectory())
            packageDir.mkdirs();
        File file;
        FileOutputStream writeStream;
        if (Properties.getSingleton().getJunitVer() != 3) {
            file = new File(packageDir, testSuite.getTestSuiteName() + ".java");
            file.createNewFile();
            writeStream = new FileOutputStream(file);
            writeStream.write(testSuite.output().getBytes(StandardCharsets.UTF_8));
            writeStream.close();
        }
        for (TestClass tc : testSuite.getTestClasses()) {
            file = new File(packageDir, tc.getClassName() + ".java");
            file.createNewFile();
            writeStream = new FileOutputStream(file);
            writeStream.write(tc.output().getBytes(StandardCharsets.UTF_8));
            writeStream.close();
        }
        file = new File(properties.getTestSourceDir(), "ab-tests.stdout");
        file.createNewFile();
        writeStream = new FileOutputStream(file);
        writeStream.write(testSuite.getTestClasses().stream().map(c -> properties.getGeneratedPackage() + "." + c.getClassName()).collect(Collectors.joining(",")).getBytes(StandardCharsets.UTF_8));
        writeStream.close();
    }
}
