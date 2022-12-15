package kwyyeung.autoboost.program.execution.stmt;

import kwyyeung.autoboost.entity.METHOD_TYPE;
import kwyyeung.autoboost.helper.Helper;
import kwyyeung.autoboost.helper.Properties;
import kwyyeung.autoboost.program.instrumentation.InstrumentResult;
import kwyyeung.autoboost.program.analysis.MethodDetails;
import kwyyeung.autoboost.program.execution.ExecutionTrace;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static kwyyeung.autoboost.helper.Helper.sootTypeToClass;

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
                    Class<?> castType = sootTypeToClass(details.getParameterTypes().get(i));
                    if (castType != null)
                        paramStmts.set(i, new CastStmt(paramStmts.get(i).getResultVarDetailID(), castType, paramStmts.get(i)));
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
        return (callee == null ? "" : callee) + (details.getType().equals(METHOD_TYPE.CONSTRUCTOR) ? ("new " + Helper.getClassNameToOutput(fullCNameNeeded, details.getdClass())) : (callee == null ? "" : ".") + details.getName().replace("$", ".")) + "(" + paramStmts.stream().map(s -> s.getStmt(fullCNameNeeded)).collect(Collectors.joining(Properties.getDELIMITER())) + ")";
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
