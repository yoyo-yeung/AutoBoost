package kwyyeung.autoboost.program.execution;


import kwyyeung.autoboost.application.AutoBoost;
import kwyyeung.autoboost.entity.METHOD_TYPE;
import kwyyeung.autoboost.helper.Properties;
import kwyyeung.autoboost.program.generation.MockOccurrence;
import kwyyeung.autoboost.program.instrumentation.InstrumentResult;
import kwyyeung.autoboost.program.analysis.MethodDetails;
import kwyyeung.autoboost.program.execution.variable.VarDetail;
import org.apache.commons.lang3.tuple.Pair;
import soot.VoidType;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class MethodExecution {
    private final int ID;
    private final MethodDetails methodInvoked;
    private final List<Integer> params;
    private VarDetail callee = null;
    private int returnValId; // if non void
    private int resultThisId; // if member function, object after executing its member function
    private Class<?> exceptionClass;
    private boolean canTest = true;
    private String test = null;
    private String requiredPackage = "";
    private final AtomicInteger childExeCount = new AtomicInteger(0);
    private Set<Pair> accessedCalleeFields = null;
    private Set<Integer> varToMock = null;
    private boolean isDefExe = false;
    private final Set<MockOccurrence> mockOccurrences = new HashSet<>();

    public MethodExecution(int ID, MethodDetails methodInvoked) {
        this.ID = ID;
        this.methodInvoked = methodInvoked;
        this.params = new ArrayList<>();
        this.returnValId = -1;
        this.resultThisId = -1;
        this.exceptionClass = null;
        this.test = AutoBoost.getExecutingTest();
    }


    private boolean relationshipCheck(int methodInvokedId, int calleeId, List<Integer> params, int returnValId, int resultThisId) {
        MethodDetails methodInvoked = InstrumentResult.getSingleton().getMethodDetailByID(methodInvokedId);
        ExecutionTrace trace = ExecutionTrace.getSingleton();
        VarDetail callee = calleeId == -1 ? null : trace.getAllVars().get(calleeId);
        VarDetail returnVal = returnValId == -1 ? null : trace.getAllVars().get(returnValId);
        VarDetail resultThis = resultThisId == -1 ? null : trace.getAllVars().get(resultThisId);
        if (methodInvoked == null) return false;
        if (methodInvoked.getType() == null) return false;
        if (methodInvoked.getType().equals(METHOD_TYPE.MEMBER)) {
            if (callee == null || (resultThis == null && exceptionClass == null))
                return false;
            if (resultThis != null && !callee.getType().equals(resultThis.getType()))
                return false;
        }
        if (methodInvoked.getType().equals(METHOD_TYPE.STATIC) && (callee != null || resultThis != null))
            return false;

        if (params.size() != methodInvoked.getParameterCount()) {
            return false;
        }
        return methodInvoked.getReturnSootType() == null || methodInvoked.getReturnSootType().equals(VoidType.v()) || (returnVal != null) || exceptionClass != null;
    }

    public MethodDetails getMethodInvoked() {
        return methodInvoked;
    }


    public int getID() {
        return ID;
    }


    public VarDetail getCallee() {
        return callee;
    }


    public void setCallee(VarDetail callee) {
        this.callee = callee;
    }

    public List<Integer> getParams() {
        return params;
    }

    public int getCalleeId() {
        return this.callee == null ? -1 : this.callee.getID();
    }


    public int getReturnValId() {
        return returnValId;
    }

    // must be the last item called
    public void setReturnValId(int returnValId) {
        if (this.returnValId != -1)
            throw new IllegalArgumentException("Return value cannot be set twice");
        this.returnValId = returnValId;
    }

    public int getResultThisId() {
        return resultThisId;
    }

    public void setResultThisId(int resultThisId) {
        if (this.resultThisId != -1)
            throw new IllegalArgumentException("Resulting callee cannot be set twice");
        this.resultThisId = resultThisId;
    }

    public void addParam(int param) {
        if (this.params.size() == this.methodInvoked.getParameterTypes().size())
            throw new IllegalArgumentException("Params cannot be set twice");
        this.params.add(param);
    }

    public Class<?> getExceptionClass() {
        return exceptionClass;
    }

    public void setExceptionClass(Class<?> exceptionClass) {
        this.exceptionClass = exceptionClass;
    }

    @Override
    public String toString() {
        return "MethodExecution{" +
                "ID=" + ID +
                ", methodInvokedId=" + methodInvoked.getId() +
                ", calleeId=" + this.getCalleeId() +
                ", params=" + params +
                ", returnValId=" + returnValId +
                ", resultThisId=" + resultThisId +
                ", exceptionClass=" + exceptionClass +
                ", canTest=" + canTest +
                ", requiredPackage=" + requiredPackage +
                ", test='" + test + '\'' +
                '}';
    }

    public String toSimpleString() {
        return "MethodExecution{" +
                "ID=" + ID +
                ", methodInvokedId=" + methodInvoked.toString() +
                ", calleeId=" + this.getCalleeId() +
                ", params=" + params +
                ", returnValId=" + returnValId +
                ", resultThisId=" + resultThisId +
                ", exceptionClass=" + (exceptionClass == null ? "null" : exceptionClass.getName()) +
                ", canTest=" + canTest +
                ", requiredPackage=" + requiredPackage +
                ", test='" + (test == null ? "null" : test) +
                '}';
    }

    public String toDetailedString() {
        ExecutionTrace trace = ExecutionTrace.getSingleton();
        return "MethodExecution{" +
                "ID=" + ID +
                ", methodInvokedId=" + methodInvoked.toString() +
                ", calleeId=" + (callee == null ? "null" : callee.toDetailedString()) +
                ", params=" + params.stream().map(p -> p == -1 ? "null" : trace.getVarDetailByID(p).toString()).collect(Collectors.joining(Properties.getDELIMITER())) +
                ", returnValId=" + (returnValId == -1 ? "null" : trace.getVarDetailByID(returnValId).toString()) +
                ", resultThisId=" + (resultThisId == -1 ? "null" : trace.getVarDetailByID(resultThisId).toString()) +
                ", e=" + (exceptionClass == null ? "null" : exceptionClass.getName()) +
                ", canTest=" + canTest +
                ", requiredPackage=" + requiredPackage +
                ", test=" + (test == null ? "null" : test) +
                '}';
    }

    public boolean sameCalleeParamNMethod(MethodExecution ex) {
        return this.methodInvoked.equals(ex.getMethodInvoked()) && this.getCalleeId() == ex.getCalleeId() && this.params.equals(ex.params);
    }

    public boolean sameContent(MethodExecution ex) {
        return sameCalleeParamNMethod(ex) && this.returnValId == ex.returnValId && this.resultThisId == ex.resultThisId;
    }

    public boolean isCanTest() {
        return canTest;
    }

    public void setCanTest(boolean canTest) {
        this.canTest = canTest;
    }

    public String getTest() {
        return test;
    }

    public void setTest(String test) {
        this.test = test;
    }

    public int getNextChildOrder() {
        return childExeCount.incrementAndGet();
    }

    public int getChildExeCount() {
        return childExeCount.get();
    }

    public String getRequiredPackage() {
        return requiredPackage;
    }

    public void setRequiredPackage(String requiredPackage) {
        this.requiredPackage = requiredPackage;
    }

    public Set<Pair> getAccessedCalleeFields() {
        return accessedCalleeFields;
    }

    public void setAccessedCalleeFields(Set<Pair> accessedCalleeFields) {
        this.accessedCalleeFields = accessedCalleeFields;
    }

    public Set<Integer> getVarToMock() {
        return varToMock;
    }

    public void setVarToMock(Set<Integer> varToMock) {
        this.varToMock = varToMock;
    }

    public boolean isDefExe() {
        return isDefExe;
    }

    public void setDefExe(boolean defExe) {
        isDefExe = defExe;
    }

    public Set<MockOccurrence> getMockOccurrences() {
        return mockOccurrences;
    }

    @Override
    public int hashCode() {
        return Objects.hash(callee, params, returnValId, resultThisId, exceptionClass);
    }

}
