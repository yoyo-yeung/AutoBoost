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
    public String getStmt() {
        MethodDetails details  = InstrumentResult.getSingleton().getMethodDetailByID(methodInvID);
        return (callee == null ? "" : callee) + (details.getType().equals(METHOD_TYPE.CONSTRUCTOR) ? ("new " + details.getDeclaringClass().getShortName().replaceAll("\\$", ".")): (callee == null ? "" : ".") + details.getName()) + "(" + paramStmts.stream().map(Stmt::getStmt).collect(Collectors.joining(Properties.getDELIMITER())) + ")";
    }

    @Override
    public Set<Class<?>> getImports() {
        Set<Class<?>> results = new HashSet<>();
        try {
            Class<?> declaringClass = Class.forName(InstrumentResult.getSingleton().getMethodDetailByID(methodInvID).getDeclaringClass().getName());
            results.add(getTypeToImport(declaringClass));
            results.remove(null);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }

        this.paramStmts.forEach(stmt -> results.addAll(stmt.getImports()));
        return results;
    }
}
