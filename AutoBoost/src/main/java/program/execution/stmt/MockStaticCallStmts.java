package program.execution.stmt;

import java.util.Set;
import java.util.stream.Collectors;

public class MockStaticCallStmts extends Stmt {
    VarStmt varStmt;
    AssignStmt mockVarAssignStmt;
    MockCallRetStmt returnStmts;

    public MockStaticCallStmts(VarStmt varStmt, AssignStmt mockVarAssignStmt, MockCallRetStmt returnStmts) {
        this.varStmt = varStmt;
        this.mockVarAssignStmt = mockVarAssignStmt;
        this.returnStmts = returnStmts;
    }

    @Override
    public String getStmt(Set<Class<?>> fullCNameNeeded) {
        return String.format("try(%s){\n%s.when(()->%s)%s;\n}\n", mockVarAssignStmt.getStmt(fullCNameNeeded), varStmt.getStmt(fullCNameNeeded), returnStmts.getMethodInvStmt().getStmt(fullCNameNeeded), returnStmts.getReturnStmts().stream().map(s -> ".thenReturn(" + s.getStmt(fullCNameNeeded) + ")").collect(Collectors.joining("\n")));
    }

    @Override
    public Set<Class<?>> getImports(String packageName) {
        Set<Class<?>> imports = varStmt.getImports(packageName);
        imports.addAll(mockVarAssignStmt.getImports(packageName));
        imports.addAll(returnStmts.getImports(packageName));
        return imports;
    }
}
