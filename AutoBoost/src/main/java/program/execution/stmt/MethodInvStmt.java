package program.execution.stmt;

import entity.METHOD_TYPE;
import helper.Properties;
import program.analysis.MethodDetails;
import program.instrumentation.InstrumentResult;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class MethodInvStmt extends Stmt{
    private final String callee;
    private final int methodInvID;
    private final List<Stmt> paramStmts;

    public MethodInvStmt(String callee, int methodInvID, List<Stmt> paramStmts) {
        this.callee = callee;
        this.methodInvID = methodInvID;
        this.paramStmts = paramStmts;
    }

    @Override
    public String getStmt(Set<Class<?>> fullCNameNeeded) {
        MethodDetails details  = InstrumentResult.getSingleton().getMethodDetailByID(methodInvID);
        return (callee == null ? "" : callee) + (details.getType().equals(METHOD_TYPE.CONSTRUCTOR) ? ("new " + (fullCNameNeeded.contains(details.getdClass()) ? details.getdClass().getName().replace("$", ".") : details.getdClass().getSimpleName().replace("$", "."))): (callee == null ? "" : ".") + details.getName().replace("$", ".")) + "(" + paramStmts.stream().map(s-> s.getStmt(fullCNameNeeded)).collect(Collectors.joining(Properties.getDELIMITER())) + ")";
    }

    @Override
    public Set<Class<?>> getImports(String packageName) {
        Set<Class<?>> results = new HashSet<>();
        METHOD_TYPE methodType = InstrumentResult.getSingleton().getMethodDetailByID(methodInvID).getType();
        if(methodType.equals(METHOD_TYPE.CONSTRUCTOR) || methodType.equals(METHOD_TYPE.STATIC) || callee.contains(".")) // callee contains . -> enum type
            results.add(getTypeToImport(InstrumentResult.getSingleton().getMethodDetailByID(methodInvID).getdClass(), packageName));
        this.paramStmts.forEach(stmt -> results.addAll(stmt.getImports(packageName)));
        results.remove(null);
        return results;
    }
}
