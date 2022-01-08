package program.execution;


import entity.METHOD_TYPE;
import helper.Properties;
import program.analysis.MethodDetails;
import program.execution.variable.VarDetail;
import program.instrumentation.InstrumentResult;
import soot.VoidType;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class MethodExecution {
    private final int ID;
    private final int methodInvokedId;
    private int calleeId; // if member function
    private final List<Integer> params;
    private int returnValId; // if non void
    private int resultThisId; // if member function, object after executing its member function
    private Class<?> exceptionClass;

    public MethodExecution(int ID, int methodInvokedId) {
        this.ID = ID;
        this.methodInvokedId = methodInvokedId;
        this.calleeId = -1;
        this.params = new ArrayList<>();
        this.returnValId = -1;
        this.resultThisId = -1;
        this.exceptionClass = null;
    }

    public MethodExecution(int ID, int methodInvokedId, int calleeId, List<Integer> params, int returnValId, int resultThisId, Class<?> e) {
        if(!relationshipCheck(methodInvokedId, calleeId, params, returnValId, resultThisId))
            throw new IllegalArgumentException("Arguments not matched");
        this.ID = ID;
        this.methodInvokedId = methodInvokedId;
        this.calleeId = calleeId;
        this.params = params == null ? new ArrayList<>() : params;
        this.returnValId = returnValId;
        this.resultThisId = resultThisId;
        this.exceptionClass = e;
    }

    public boolean relationshipCheck() {
        return relationshipCheck(this.methodInvokedId, this.calleeId, this.params, this.returnValId, this.resultThisId);
    }

    private boolean relationshipCheck(int methodInvokedId, int calleeId, List<Integer> params, int returnValId, int resultThisId) {
        MethodDetails methodInvoked = InstrumentResult.getSingleton().getMethodDetailByID(methodInvokedId);
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

        if(params.size()!=methodInvoked.getParameterCount()) {
            return false;
        }
        if(methodInvoked.getReturnType() != null && !methodInvoked.getReturnType().equalsIgnoreCase("void") && (returnVal == null))
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
        if(this.params.size() == InstrumentResult.getSingleton().getMethodDetailByID(this.methodInvokedId).getParameterTypes().size())
            throw new IllegalArgumentException("Params cannot be set twice");
        this.params.add(param);
    }

    // must be the last item called
    public void setReturnValId(int returnValId) {
        if(this.returnValId != -1)
            throw new IllegalArgumentException("Return value cannot be set twice");
        this.returnValId = returnValId;
    }

    public void setResultThisId(int resultThisId) {
        if(this.resultThisId != -1)
            throw new IllegalArgumentException("Resulting callee cannot be set twice");
        this.resultThisId = resultThisId;
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
                ", methodInvokedId=" + methodInvokedId +
                ", calleeId=" + calleeId +
                ", params=" + params +
                ", returnValId=" + returnValId +
                ", resultThisId=" + resultThisId +
                ", e=" + (exceptionClass == null ? "null" : exceptionClass.getName()) +
                '}';
    }

    public String toDetailedString(){
        ExecutionTrace trace = ExecutionTrace.getSingleton();
        return "MethodExecution{" +
                "ID=" + ID +
                ", methodInvokedId=" + InstrumentResult.getSingleton().getMethodDetailByID(methodInvokedId).toString() +
                ", calleeId=" + (calleeId == -1 ? "null" : trace.getVarDetailByID(calleeId).toString() )+
                ", params=" + params.stream().map(p -> p == -1 ? "null" : trace.getVarDetailByID(p).toString()).collect(Collectors.joining(Properties.getDELIMITER())) +
                ", returnValId=" + (returnValId == -1 ? "null" : trace.getVarDetailByID(returnValId).toString()) +
                ", resultThisId=" + (resultThisId == -1 ? "null" : trace.getVarDetailByID(resultThisId).toString()) +
                ", e=" + (exceptionClass == null ? "null" : exceptionClass.getName()) +
                '}';
    }

    public boolean sameCalleeParamNMethod(MethodExecution ex) {
        if(this.getMethodInvokedId() != ex.getMethodInvokedId() || this.calleeId != ex.calleeId || (this.params.size() != ex.params.size()))
            return false;

        for (int i = 0; i < this.params.size(); i++) {
            if(!this.params.get(i).equals(ex.getParams().get(i)))
                return false;
        }
        return true;
    }
    
}
