package program.execution.stmt;

import entity.METHOD_TYPE;
import helper.Properties;
import org.apache.commons.lang3.ClassUtils;
import program.analysis.MethodDetails;
import program.execution.ExecutionTrace;
import program.instrumentation.InstrumentResult;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class MethodInvStmt extends Stmt {
    private final String callee;
    private final int methodInvID;
    private final List<Stmt> paramStmts;

    public MethodInvStmt(String callee, int methodInvID, List<Stmt> paramStmts) {
        this.callee = callee;
        this.methodInvID = methodInvID;
        this.paramStmts = paramStmts;
        MethodDetails details = InstrumentResult.getSingleton().getMethodDetailByID(methodInvID);
        IntStream.range(0, details.getParameterCount()).filter(i -> !(paramStmts.get(i) instanceof CastStmt) && paramStmts.get(i).getResultVarDetailID() == ExecutionTrace.getSingleton().getNullVar().getID())
                .forEach(i -> {
                    try {
                        paramStmts.set(i, new CastStmt(paramStmts.get(i).getResultVarDetailID(), ClassUtils.getClass(details.getParameterTypes().get(i).toQuotedString()), paramStmts.get(i)));
                    } catch (ClassNotFoundException e) {
                        e.printStackTrace();
                    }
                });
    }

    public String getCallee() {
        return callee;
    }

    public String getInstanceCallString(Set<Class<?>> fullCNameNeeded) {
        MethodDetails details = InstrumentResult.getSingleton().getMethodDetailByID(methodInvID);
        if (!details.getType().equals(METHOD_TYPE.MEMBER)) return "";
        return details.getName().replace("$", ".") + "(" + paramStmts.stream().map(s -> s.getStmt(fullCNameNeeded)).collect(Collectors.joining(Properties.getDELIMITER())) + ")";
    }

    @Override
    public String getStmt(Set<Class<?>> fullCNameNeeded) {
        MethodDetails details = InstrumentResult.getSingleton().getMethodDetailByID(methodInvID);
        return (callee == null ? "" : callee) + (details.getType().equals(METHOD_TYPE.CONSTRUCTOR) ? ("new " + (fullCNameNeeded.contains(details.getdClass()) ? details.getdClass().getName().replace("$", ".") : details.getdClass().getSimpleName().replace("$", "."))) : (callee == null ? "" : ".") + details.getName().replace("$", ".")) + "(" + paramStmts.stream().map(s -> s.getStmt(fullCNameNeeded)).collect(Collectors.joining(Properties.getDELIMITER())) + ")";
    }

    @Override
    public Set<Class<?>> getImports(String packageName) {
        Set<Class<?>> results = new HashSet<>();
        METHOD_TYPE methodType = InstrumentResult.getSingleton().getMethodDetailByID(methodInvID).getType();
        if (methodType.equals(METHOD_TYPE.CONSTRUCTOR) || methodType.equals(METHOD_TYPE.STATIC) || callee.contains(".")) // callee contains . -> enum type
            results.add(getTypeToImport(InstrumentResult.getSingleton().getMethodDetailByID(methodInvID).getdClass(), packageName));
        this.paramStmts.forEach(stmt -> results.addAll(stmt.getImports(packageName)));
        results.remove(null);
        return results;
    }
}
