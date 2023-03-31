package kwyyeung.autoboost.program.execution.stmt;

import kwyyeung.autoboost.entity.CREATION_TYPE;
import kwyyeung.autoboost.helper.Properties;
import kwyyeung.autoboost.program.instrumentation.InstrumentResult;
import kwyyeung.autoboost.program.execution.ExecutionTrace;
import kwyyeung.autoboost.program.execution.variable.ArrVarDetails;
import kwyyeung.autoboost.program.execution.variable.MapVarDetails;
import kwyyeung.autoboost.program.execution.variable.ObjVarDetails;
import kwyyeung.autoboost.program.execution.variable.VarDetail;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ConstructStmt extends Stmt {
    private final Integer methodID;
    private final Integer methodExecutionID;
    private final List<Stmt> paramStmts;

    public ConstructStmt(int resultVarDetailID, Integer methodExecutionID, List<Stmt> paramStmtIDs) {
        super(resultVarDetailID);
        ExecutionTrace trace = ExecutionTrace.getSingleton();
        VarDetail varDetail = trace.getVarDetailByID(resultVarDetailID);
        if (!varDetail.getCreatedBy().equals(CREATION_TYPE.CONSTRUCTOR))
            throw new IllegalArgumentException("VarDetail provided should not be created by construction");
        if (varDetail instanceof ObjVarDetails && methodExecutionID == null)
            throw new IllegalArgumentException("Missing execution details");
        if (methodExecutionID != null && methodExecutionID != -1) {
            this.methodExecutionID = methodExecutionID;
            this.methodID = trace.getMethodExecutionByID(methodExecutionID).getMethodInvoked().getId();
            if (InstrumentResult.getSingleton().getMethodDetailByID(this.methodID).getParameterTypes().size() < paramStmtIDs.size())
                throw new IllegalArgumentException("No. of params provided does not match provided constructor");
        } else {
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
    public String getStmt(Set<Class<?>> fullCNameNeeded) {
        ExecutionTrace trace = ExecutionTrace.getSingleton();
        VarDetail resultVarDetail = trace.getVarDetailByID(resultVarDetailID);
        Class<?> varDetailClass = resultVarDetail.getClass();

        StringBuilder result = new StringBuilder();
        result.append("new ").append(trace.getVarDetailByID(resultVarDetailID).getTypeSimpleName());
        if (varDetailClass.equals(ArrVarDetails.class)) {
            if (resultVarDetail.getType().isArray())
                result.append(getArrString(fullCNameNeeded));
            else if (List.class.isAssignableFrom(resultVarDetail.getType()) || Set.class.isAssignableFrom(resultVarDetail.getType())) {
                result.append(getListSetString(fullCNameNeeded));
            }
        } else if (varDetailClass.equals(MapVarDetails.class)) {
            result.append(getMapStmtString(fullCNameNeeded));
        } else {
            result.append("(").append(paramStmts.stream().map(s -> s.getStmt(fullCNameNeeded)).collect(Collectors.joining(Properties.getDELIMITER()))).append(")");
        }
        return result.toString();
    }

    private String getArrString(Set<Class<?>> fullCNameNeeded) {
        return "{" + paramStmts.stream().map(s -> s.getStmt(fullCNameNeeded)).collect(Collectors.joining(Properties.getDELIMITER())) + "}";
    }

    private String getListSetString(Set<Class<?>> fullCNameNeeded) {
        return "()" + (paramStmts.size() > 0 ? "{{" + paramStmts.stream().map(s -> "add(" + s.getStmt(fullCNameNeeded) + ")").collect(Collectors.joining(";" + Properties.getNewLine())) + ";}}" : "");
    }

    private String getMapStmtString(Set<Class<?>> fullCNameNeeded) {
        return "()" + (paramStmts.size() > 0 ? "{{" + paramStmts.stream().map(s -> "put(" + s.getStmt(fullCNameNeeded) + ")").collect(Collectors.joining(";" + Properties.getNewLine())) + ";}}" : "");
    }

    @Override
    public Set<Class<?>> getImports(String packageName) {
        Set<Class<?>> results = new HashSet<>();
        results.add(getTypeToImport(ExecutionTrace.getSingleton().getVarDetailByID(resultVarDetailID).getType(), packageName));
        results.remove(null);
        this.paramStmts.forEach(stmt -> results.addAll(stmt.getImports(packageName)));
        return results;
    }
}
