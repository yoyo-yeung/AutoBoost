package kwyyeung.autoboost.program.execution.stmt;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class MockCallRetStmt extends Stmt {
    private final MethodInvStmt methodInvStmt;
    private List<Stmt> returnStmts;
    private boolean skipChecking = false;

    public MockCallRetStmt(MethodInvStmt methodInvStmt, List<Stmt> returnStmts, boolean skipChecking) {
        this.methodInvStmt = methodInvStmt;
        this.returnStmts = returnStmts;
        this.skipChecking = skipChecking;
    }

    public MethodInvStmt getMethodInvStmt() {
        return methodInvStmt;
    }

    public List<Stmt> getReturnStmts() {
        return returnStmts;
    }

    public void setReturnStmts(List<Stmt> returnStmts) {
        this.returnStmts = returnStmts;
    }

    @Override
    public String getStmt(Set<Class<?>> fullCNameNeeded) {
        if (skipChecking)
            return "Mockito" + returnStmts.stream().map(s -> ".doReturn(" + s.getStmt(fullCNameNeeded) +")").collect(Collectors.joining("")) + ".when(" + (methodInvStmt.getCallee() + ")." + methodInvStmt.getInstanceCallString(fullCNameNeeded));
        return "Mockito.when(" + methodInvStmt.getStmt(fullCNameNeeded) + ")" + returnStmts.stream().map(s -> ".thenReturn(" + s.getStmt(fullCNameNeeded) + ")").collect(Collectors.joining(""));
    }

    @Override
    public Set<Class<?>> getImports(String packageName) {
        Set<Class<?>> imports = methodInvStmt.getImports(packageName);
        imports.addAll(this.returnStmts.stream().map(s -> s.getImports(packageName)).flatMap(Collection::stream).collect(Collectors.toSet()));
        return imports;
    }
}
