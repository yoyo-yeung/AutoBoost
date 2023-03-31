package kwyyeung.autoboost.program.generation;

import kwyyeung.autoboost.program.analysis.MethodDetails;
import kwyyeung.autoboost.program.execution.ExecutionTrace;
import kwyyeung.autoboost.program.execution.MethodExecution;
import kwyyeung.autoboost.program.execution.stmt.VarStmt;
import kwyyeung.autoboost.program.execution.variable.VarDetail;

import java.util.*;
import java.util.stream.Collectors;

public class MockOccurrence {
    private final ExecutionTrace executionTrace = ExecutionTrace.getSingleton();
    private VarStmt mockedVar;
    private int callee ;
    private final MethodDetails inovkedMethod;
    private List<MethodExecution> mockingExes;
    private final List<Integer> paramVarID;
    private final List<Integer> returnVars;

    public MockOccurrence(VarStmt mockedVar, MethodDetails inovkedMethod, List<Integer> paramVarID) {
        this.mockedVar = mockedVar;
        this.inovkedMethod = inovkedMethod;
        this.paramVarID = paramVarID;
        this.returnVars = new ArrayList<>();
    }

    public MockOccurrence(int callee, MethodDetails inovkedMethod, List<Integer> paramVarID) {
        this.callee =  callee;
        this.inovkedMethod = inovkedMethod;
        this.mockingExes = new ArrayList<>();
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

    public List<MethodExecution> getMockingExes() {
        return mockingExes;
    }

    public void setMockedVar(VarStmt mockedVar) {
        this.mockedVar = mockedVar;
    }

    public int getCallee() {
        return callee;
    }

    public void setCallee(int callee) {
        this.callee = callee;
    }

    public boolean sameCallInfo(VarStmt mockedVar, MethodExecution execution) {
        List<VarDetail> concreteParams = this.paramVarID.stream().map(executionTrace::getVarDetailByID).map(p -> TestGenerator.isVarToMock(p) ? null : p).collect(Collectors.toList());
        List<VarDetail> concreteExeParams = execution.getParams().stream().map(executionTrace::getVarDetailByID).map(p -> TestGenerator.isVarToMock(p) ? null : p).collect(Collectors.toList());
        return (Objects.equals(this.mockedVar, mockedVar)) && this.inovkedMethod.equals(execution.getMethodInvoked()) && concreteParams.equals(concreteExeParams);
    }
    public boolean sameCallInfo(int callee, MethodExecution execution) {
        List<VarDetail> concreteParams = this.paramVarID.stream().map(executionTrace::getVarDetailByID).map(p -> TestGenerator.isVarToMock(p) ? null : p).collect(Collectors.toList());
        List<VarDetail> concreteExeParams = execution.getParams().stream().map(executionTrace::getVarDetailByID).map(p -> TestGenerator.isVarToMock(p) ? null : p).collect(Collectors.toList());
        return (this.callee == callee) && this.inovkedMethod.equals(execution.getMethodInvoked()) && concreteParams.equals(concreteExeParams);
    }

    @Override
    public String toString() {
        return "MockOccurrence{" +
                "executionTrace=" + executionTrace +
                ", mockedVar=" + mockedVar +
                ", callee=" + callee +
                ", inovkedMethod=" + inovkedMethod +
//                ", mockingExes=" + mockingExes +
                ", paramVarID=" + paramVarID +
                ", returnVars=" + returnVars +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MockOccurrence that = (MockOccurrence) o;
        return callee == that.callee && Objects.equals(executionTrace, that.executionTrace) && Objects.equals(mockedVar, that.mockedVar) && Objects.equals(inovkedMethod, that.inovkedMethod) && Objects.equals(mockingExes, that.mockingExes) && Objects.equals(paramVarID, that.paramVarID) && Objects.equals(returnVars, that.returnVars);
    }

    @Override
    public int hashCode() {
        return Objects.hash(executionTrace, mockedVar, callee, inovkedMethod, mockingExes, paramVarID, returnVars);
    }
}
