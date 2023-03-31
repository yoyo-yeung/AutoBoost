package kwyyeung.autoboost.program.execution.stmt;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class AssignStmt extends Stmt {
    private final Stmt leftStmt;
    private final Stmt rightStmt;

    public AssignStmt(Stmt leftStmt, Stmt rightStmt) {
        this.leftStmt = leftStmt;
        this.rightStmt = rightStmt;
    }

    @Override
    public String getStmt(Set<Class<?>> fullCNameNeeded) {
        return (leftStmt instanceof VarStmt ? ((VarStmt) leftStmt).getDeclarationStmt(fullCNameNeeded) : leftStmt.getStmt(fullCNameNeeded)) + " = " + rightStmt.getStmt(fullCNameNeeded);
    }

    @Override
    public Set<Class<?>> getImports(String packageName) {
        return Stream.of(leftStmt.getImports(packageName), rightStmt.getImports(packageName)).flatMap(Collection::stream).collect(Collectors.toSet());
    }
}
