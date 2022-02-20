package program.execution.stmt;

import entity.CREATION_TYPE;
import helper.Properties;
import program.execution.ExecutionTrace;
import program.execution.variable.ArrVarDetails;
import program.execution.variable.MapVarDetails;
import program.execution.variable.ObjVarDetails;
import program.execution.variable.VarDetail;
import program.instrumentation.InstrumentResult;

import java.util.*;
import java.util.stream.Collectors;

public class ConstructStmt extends Stmt{
    private final Integer methodID;
    private final Integer methodExecutionID;
    private final List<Stmt> paramStmts;

    public ConstructStmt(int resultVarDetailID, Integer methodExecutionID, List<Stmt> paramStmtIDs) {
        super(resultVarDetailID);
        ExecutionTrace trace = ExecutionTrace.getSingleton();
        VarDetail varDetail = trace.getVarDetailByID(resultVarDetailID);
        if(!varDetail.getCreatedBy().equals(CREATION_TYPE.CONSTRUCTOR))
            throw new IllegalArgumentException("VarDetail provided should not be created by construction");
        if(varDetail instanceof ObjVarDetails && methodExecutionID == null)
            throw new IllegalArgumentException("Missing execution details");
        if(methodExecutionID !=null && methodExecutionID != -1 ) {
            this.methodExecutionID = methodExecutionID;
            this.methodID = trace.getMethodExecutionByID(methodExecutionID).getMethodInvokedId();
            if(InstrumentResult.getSingleton().getMethodDetailByID(this.methodID).getParameterTypes().size()<paramStmtIDs.size())
                throw new IllegalArgumentException("No. of params provided does not match provided constructor");
        }
        else {
            this.methodExecutionID = null;
            this.methodID = null;
        }
        this.paramStmts = paramStmtIDs == null ? new ArrayList<>() : paramStmtIDs;
    }

    public Integer getMethodID() {
        return methodID;
    }

    public List<Stmt> getParamStmts() {
        return paramStmts;
    }

    @Override
    public String getStmt() {
        ExecutionTrace trace = ExecutionTrace.getSingleton();
        VarDetail resultVarDetail = trace.getVarDetailByID(resultVarDetailID);

        StringBuilder result = new StringBuilder();
        result.append("new ").append(trace.getVarDetailByID(resultVarDetailID).getTypeSimpleName());
        if(resultVarDetail instanceof ArrVarDetails) {
            if(resultVarDetail.getType().isArray())
                result.append(getArrString());
            else if(List.class.isAssignableFrom(resultVarDetail.getType()) || Set.class.isAssignableFrom(resultVarDetail.getType())){
                result.append(getListSetString());
            }
        }
        else if (resultVarDetail instanceof MapVarDetails) {
            result.append(getMapStmtString());
        }
        else {
            result.append("(").append(paramStmts.stream().map(Stmt::getStmt).collect(Collectors.joining(Properties.getDELIMITER()))).append(")");
        }
        return result.toString();
    }

    private String getArrString(){
        return "{" + paramStmts.stream().map(Stmt::getStmt).collect(Collectors.joining(Properties.getDELIMITER())) + "}";
    }
    private String getListSetString() {
        return "(){{" + paramStmts.stream().map(s -> "add("+s.getStmt()+")").collect(Collectors.joining(";"+Properties.getNewLine())) + ";}}";
    }
    private String getMapStmtString(){
        return "(){{" + paramStmts.stream().map(s-> "put("+s.getStmt()+")").collect(Collectors.joining(";" + Properties.getNewLine())) + ";}}";
    }

    @Override
    public Set<Class<?>> getImports() {
        Set<Class<?>> results = new HashSet<>();
        results.add(getTypeToImport(ExecutionTrace.getSingleton().getVarDetailByID(resultVarDetailID).getType()));
        results.remove(null);
        this.paramStmts.forEach(stmt -> results.addAll(stmt.getImports()));
        return results;
    }
}
