package program.generation;

import entity.ACCESS;
import entity.METHOD_TYPE;
import helper.Properties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import program.analysis.MethodDetails;
import program.execution.ExecutionTrace;
import program.execution.MethodExecution;
import program.execution.stmt.*;
import program.execution.variable.*;
import program.generation.testcase.DefaultTestCase;
import program.instrumentation.InstrumentResult;

import java.util.*;
import java.util.stream.Collectors;

public class TestGenerator {
    private static final Logger logger = LogManager.getLogger(TestGenerator.class);
    private static final TestGenerator singleton = new TestGenerator();
    private final ExecutionTrace executionTrace = ExecutionTrace.getSingleton();
    private final InstrumentResult instrumentResult = InstrumentResult.getSingleton();
    private final Set<MethodExecution> coveredExecutions = new HashSet<>();
    private final List<DefaultTestCase> testCases = new ArrayList<>();

    public TestGenerator() {
    }

    public static TestGenerator getSingleton() {
        return singleton;
    }

    public void generateResultCheckingTests() {
        executionTrace.getAllMethodExecs().values().stream()
                .filter(e -> exeCanBeTested(e.getID()) && e.getReturnValId()!=-1)
                .filter(e -> !executionTrace.getDefExeList(e.getReturnValId()).contains(e.getID()) ) // prevent self checking
                .forEach(e ->
        {
            DefaultTestCase testCase = new DefaultTestCase();
            MethodInvStmt invStmt = getMethodInvStmt(e, testCase);
            VarStmt returnValStmt = new VarStmt(executionTrace.getVarDetailByID(e.getReturnValId()).getType(), testCase.getNewVarID());
            testCase.addStmt(new AssignStmt(returnValStmt, invStmt));
            testCase.setAssertion(new AssertStmt(generateDefStmt(e.getReturnValId(), testCase, false, true), returnValStmt));
            testCases.add(testCase);
        });
    }


    public void generateSameThisCheckingTests() {
        executionTrace.getAllMethodExecs().values().stream()
                .filter(e -> exeCanBeTested(e.getID()) && e.getCalleeId()!= -1 && e.getCalleeId() == e.getResultThisId() && !executionTrace.getDefExeList(e.getReturnValId()).contains(e.getID()) )
                .map(e -> {
                    DefaultTestCase testCase = new DefaultTestCase();
                    MethodInvStmt invStmt = getMethodInvStmt(e, testCase);
                    testCase.addStmt(invStmt);
                    generateDefStmt(e.getCalleeId(), testCase, false, true);
                    testCase.setAssertion(new AssertStmt(testCase.getExistingVar(e.getCalleeId()).get(0),  testCase.getExistingVar(e.getCalleeId()).get(1)));
                    return testCase;
                }).forEach(t-> testCases.add(t));
    }
    public boolean exeCanBeTested(Integer methodExecutionID) {
        MethodExecution execution = executionTrace.getMethodExecutionByID(methodExecutionID);
        MethodDetails details = instrumentResult.getMethodDetailByID(execution.getMethodInvokedId());
        if(containsFaultyDef(methodExecutionID)) {
            return false;
        }
        if(details.getType().equals(METHOD_TYPE.STATIC_INITIALIZER)) return false;
        if(details.getAccess().equals(ACCESS.PRIVATE) || (details.getAccess().equals(ACCESS.PROTECTED) && !details.getDeclaringClass().getPackageName().equals(Properties.getSingleton().getGeneratedPackage()))) {
            return false;
        }
        if(execution.getCalleeId()!=-1 && !varCanBeTested(execution.getCalleeId())) {
            return false;
        }
        if(execution.getParams().stream().anyMatch(p -> !varCanBeTested(p))) {
            return false;
        }
        if(execution.getReturnValId()!=-1 && !varCanBeTested(execution.getReturnValId())) {
            return false;
        }
        return true;
    }
    private boolean varCanBeTested(Integer varID) {
        VarDetail varDetail = executionTrace.getVarDetailByID(varID);
        if(executionTrace.getDefExeList(varID).size() == 0 && (varDetail instanceof PrimitiveVarDetails || varDetail instanceof WrapperVarDetails || varDetail instanceof StringVarDetails || varDetail instanceof MapVarDetails || varDetail instanceof ArrVarDetails || varDetail instanceof EnumVarDetails || varDetail.equals(executionTrace.getNullVar())))
            return true;
        if(executionTrace.getDefExeList(varID).size() == 0 || executionTrace.getDefExeList(varID).stream().anyMatch(d -> !exeCanBeTested(d))) return false;
        return true;
    }
    private boolean containsFaultyDef(Integer exeID) {
        MethodExecution execution = executionTrace.getMethodExecutionByID(exeID);
        if(Arrays.stream(Properties.getSingleton().getFaultyFunc()).anyMatch(s -> s.equals(instrumentResult.getMethodDetailByID(execution.getMethodInvokedId()).getSignature()) || executionTrace.getAllChildern(exeID).stream().anyMatch(e -> instrumentResult.getMethodDetailByID(executionTrace.getMethodExecutionByID(e).getMethodInvokedId()).getSignature().equals(s))))
            return true;
        return false;
    }
    public Stmt generateDefStmt(Integer varDetailsID, DefaultTestCase testCase, boolean checkExisting, boolean store) throws IllegalArgumentException {
        VarDetail varDetail = executionTrace.getVarDetailByID(varDetailsID);
        if(checkExisting && testCase.getExistingVar(varDetail)!=null && testCase.getExistingVar(varDetail).size()>0)
            return testCase.getExistingVar(varDetail).get(0);
        if(varDetail.equals(executionTrace.getNullVar()))
            return new ConstantStmt(varDetailsID);
        if(executionTrace.getDefExeList(varDetail.getID()).size() == 0 && (varDetail instanceof PrimitiveVarDetails || varDetail instanceof WrapperVarDetails || varDetail instanceof StringVarDetails || varDetail instanceof MapVarDetails || varDetail instanceof ArrVarDetails || varDetail instanceof EnumVarDetails))
            return getSingleDefStmt(varDetail, testCase);
        else if(executionTrace.getDefExeList(varDetail.getID()).size()==0)
            throw new IllegalArgumentException("Missing def list");
        else return getComplexDefStmt(varDetail, testCase);
    }

    private Stmt getSingleDefStmt(VarDetail varDetail, DefaultTestCase testCase) {
        if(executionTrace.getDefExeList(varDetail.getID()).size() != 0 || !(varDetail instanceof PrimitiveVarDetails || varDetail instanceof WrapperVarDetails || varDetail instanceof StringVarDetails || varDetail instanceof MapVarDetails || varDetail instanceof ArrVarDetails || varDetail instanceof EnumVarDetails))
            throw new IllegalArgumentException("Provided VarDetail cannot be assigned with single stmt");
        if(varDetail instanceof PrimitiveVarDetails || varDetail instanceof WrapperVarDetails || varDetail instanceof StringVarDetails || varDetail instanceof EnumVarDetails)
            return new ConstantStmt(varDetail.getID());
        List<Stmt> params;
        if(varDetail instanceof MapVarDetails) {
            params = ((MapVarDetails)varDetail).getKeyValuePairs().entrySet().stream().map(e -> new PairStmt(generateDefStmt(e.getKey(), testCase, true, true), generateDefStmt(e.getValue(), testCase, true, true))).collect(Collectors.toList());
        }
        else {
            params = ((ArrVarDetails)varDetail).getComponents().stream().map(e -> generateDefStmt(e, testCase, true, true)).collect(Collectors.toList());
            return new ConstructStmt(varDetail.getID(), null, params);
        }
        VarStmt varStmt = new VarStmt(varDetail.getType(), testCase.getNewVarID());
        testCase.addStmt(new AssignStmt(varStmt, new ConstructStmt(varDetail.getID(), null, params)));
        testCase.addOrUpdateVar(varStmt, varDetail);
        return varStmt;
    }

    private MethodInvStmt getMethodInvStmt(MethodExecution execution, DefaultTestCase testCase) {
        MethodDetails details = instrumentResult.getMethodDetailByID(execution.getMethodInvokedId());
        MethodInvStmt invokeStmt = null;
        VarStmt varStmt;
        switch(details.getType()) {
            case STATIC:
                invokeStmt = new MethodInvStmt(details.getDeclaringClass().getName(), details.getId(), execution.getParams().stream().map(p -> generateDefStmt(p, testCase, true, true)).collect(Collectors.toList()));
                break;
            case CONSTRUCTOR:
                invokeStmt = new MethodInvStmt("", details.getId(), execution.getParams().stream().map(p-> generateDefStmt(p, testCase, true, true)).collect(Collectors.toList()));
                break;
            case MEMBER:
                varStmt = (VarStmt) generateDefStmt(execution.getCalleeId(), testCase, true, true);
                invokeStmt = new MethodInvStmt(varStmt.getStmt(), details.getId(), execution.getParams().stream().map(p -> generateDefStmt(p, testCase, true, true)).collect(Collectors.toList()));
                break;
        }
        return invokeStmt;
    }
    private VarStmt getComplexDefStmt(VarDetail varDetail, DefaultTestCase testCase) {
        int varDetailID = varDetail.getID();
        executionTrace.getDefExeList(varDetailID).forEach(e -> {
            MethodExecution execution = executionTrace.getMethodExecutionByID(e);
            MethodDetails details = instrumentResult.getMethodDetailByID(execution.getMethodInvokedId());
            MethodInvStmt invokeStmt = getMethodInvStmt(execution, testCase);
            VarStmt varStmt = null;
            switch(details.getType()) {
                case STATIC: // must be return value
                    if(details.getReturnType().equalsIgnoreCase("void"))
                        testCase.addStmt(invokeStmt);
                    else {
                        List<VarStmt> availableStmts = testCase.getExistingVar(execution.getReturnValId());
                        varStmt = availableStmts == null || availableStmts.size() == 0? null : availableStmts.get(0);
                        if(varStmt == null)
                            varStmt = new VarStmt(executionTrace.getVarDetailByID(execution.getReturnValId()).getType(), testCase.getNewVarID());
                        testCase.addStmt(new AssignStmt(varStmt, invokeStmt));
                        testCase.addOrUpdateVar(varStmt, executionTrace.getVarDetailByID(execution.getReturnValId()));
                    }
                    break;
                case CONSTRUCTOR:
                    varStmt = new VarStmt(executionTrace.getVarDetailByID(execution.getResultThisId()).getType(), testCase.getNewVarID());
                    testCase.addStmt(new AssignStmt(varStmt, invokeStmt));
                    testCase.addOrUpdateVar(varStmt, executionTrace.getVarDetailByID(execution.getResultThisId()));
                    break;
                case MEMBER:
                    varStmt = (VarStmt) generateDefStmt(execution.getCalleeId(), testCase, true, true);
                    if(!details.getReturnType().equalsIgnoreCase("void")){
                        VarStmt varStmt1 = new VarStmt(executionTrace.getVarDetailByID(execution.getReturnValId()).getType(), testCase.getNewVarID());
                        testCase.addStmt(new AssignStmt(varStmt1, invokeStmt));
                        testCase.addOrUpdateVar(varStmt1, executionTrace.getVarDetailByID(execution.getReturnValId()));
                    }
                    else
                        testCase.addStmt(invokeStmt);
                    testCase.addOrUpdateVar(varStmt, executionTrace.getVarDetailByID(execution.getResultThisId()));
                break;
            }
        });
        return testCase.getExistingVar(varDetail).get(0);
    }

    public List<DefaultTestCase> getTestCases() {
        return testCases;
    }
}
