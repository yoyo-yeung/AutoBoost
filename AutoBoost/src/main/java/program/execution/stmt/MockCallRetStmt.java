package program.execution.stmt;

import java.util.*;
import java.util.stream.Collectors;

public class MockCallRetStmt extends Stmt{
    private final MethodInvStmt methodInvStmt;
    private List<Stmt> returnStmts;

    public MockCallRetStmt(MethodInvStmt methodInvStmt, List<Stmt> returnStmts) {
        this.methodInvStmt = methodInvStmt;
        this.returnStmts = returnStmts;
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
        return "PowerMockito.when("+methodInvStmt.getStmt(fullCNameNeeded)+ ")" + returnStmts.stream().map(s -> ".thenReturn("+s.getStmt(fullCNameNeeded)+")").collect(Collectors.joining("\n")) +";";
    }

    @Override
    public Set<Class<?>> getImports(String packageName) {
        Set<Class<?>> imports = methodInvStmt.getImports(packageName);
        imports.addAll(this.returnStmts.stream().map(s -> s.getImports(packageName)).flatMap(Collection::stream).collect(Collectors.toSet()));
        return imports;
    }
}
