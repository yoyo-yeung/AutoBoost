package program.execution;


import entity.METHOD_TYPE;
import program.analysis.MethodDetails;
import program.execution.variable.VarDetail;
import program.instrumentation.InstrumentResult;
import soot.VoidType;

import java.util.ArrayList;
import java.util.List;

public class MethodExecution {
    private final int ID;
    private final int methodInvokedId;
    private int calleeId; // if member function
    private final List<Integer> params;
    private int returnValId; // if non void
    private int resultThisId; // if member function, object after executing its member function

    public MethodExecution(int ID, int methodInvokedId) {
        this.ID = ID;
        this.methodInvokedId = methodInvokedId;
        this.calleeId = -1;
        this.params = new ArrayList<>();
        this.returnValId = -1;
        this.resultThisId = -1;
    }

    public MethodExecution(int ID, int methodInvokedId, int calleeId, List<Integer> params, int returnValId, int resultThisId) {
        if(!relationshipCheck(methodInvokedId, calleeId, params, returnValId, resultThisId))
            throw new IllegalArgumentException("Arguments not matched");
        this.ID = ID;
        this.methodInvokedId = methodInvokedId;
        this.calleeId = calleeId;
        this.params = params == null ? new ArrayList<>() : params;
        this.returnValId = returnValId;
        this.resultThisId = resultThisId;
    }
    public boolean relationshipCheck() {
        return relationshipCheck(this.methodInvokedId, this.calleeId, this.params, this.returnValId, this.resultThisId);
    }
    private boolean relationshipCheck(int methodInvokedId, int calleeId, List<Integer> params, int returnValId, int resultThisId) {
        MethodDetails methodInvoked = InstrumentResult.getSingleton().getMethodDetailsMap().get(methodInvokedId);
        ExecutionTrace trace = ExecutionTrace.getSingleton();
        VarDetail callee = calleeId == -1 ? null : trace.getAllVars().get(calleeId);
        VarDetail returnVal = returnValId == -1 ? null : trace.getAllVars().get(returnValId);
        VarDetail resultThis = resultThisId == -1 ? null : trace.getAllVars().get(resultThisId);
        if(methodInvoked == null ) return false;
        if(methodInvoked.getType() == null) return false;
        if(methodInvoked.getType().equals(METHOD_TYPE.MEMBER)) {
            if(callee == null || resultThis == null)
                return false;
            if(!callee.getType().equals(resultThis.getType()))
                return false;
        }
        if(methodInvoked.getType().equals(METHOD_TYPE.STATIC) && (callee != null || resultThis != null))
            return false;
        if(methodInvoked.getParameterTypes() != null && methodInvoked.getParameterTypes().size() > 0 && (params == null || params.size() != methodInvoked.getParameterTypes().size()))
            return false;
        if(methodInvoked.getReturnType() != null && !methodInvoked.getReturnType().getClass().equals(VoidType.class) && (returnVal == null || !methodInvoked.getReturnType().getClass().equals(returnVal.getType())))
            return false;
        return true;
    }

    public int getID() {
        return ID;
    }

    public int getMethodInvokedId() {
        return methodInvokedId;
    }

    public int getCalleeId() {
        return calleeId;
    }

    public List<Integer> getParams() {
        return params;
    }

    public int getReturnValId() {
        return returnValId;
    }

    public int getResultThisId() {
        return resultThisId;
    }

    public void setCalleeId(int calleeId) {
        if(this.calleeId != -1)
            throw new IllegalArgumentException("Callee cannot be set twice");
        this.calleeId = calleeId;
    }

    public void addParam(int param) {
        if(this.params.size() == InstrumentResult.getSingleton().getMethodDetailsMap().get(this.methodInvokedId).getParameterTypes().size())
            throw new IllegalArgumentException("Params cannot be set twice");
        this.params.add(param);
    }

    // must be the last item called
    public void setReturnValId(int returnValId) {
        if(this.returnValId != -1 )
            throw new IllegalArgumentException("Return value cannot be set twice");
        this.returnValId = returnValId;
    }

    public void setResultThisId(int resultThisId) {
        if(this.resultThisId != -1)
            throw new IllegalArgumentException("Resulting callee cannot be set twice");
        this.resultThisId = resultThisId;
    }

    @Override
    public String toString() {
        return "MethodExecution{" +
                "ID=" + ID +
                ", methodInvokedId=" + methodInvokedId +
                ", calleeId=" + calleeId +
                ", params=" + params +
                ", returnValId=" + returnValId +
                ", resultThisId=" + resultThisId +
                '}';
    }
}