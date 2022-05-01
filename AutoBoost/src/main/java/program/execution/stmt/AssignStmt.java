package program.execution.stmt;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class AssignStmt extends Stmt{
    private final Stmt leftStmt;
    private final Stmt rightStmt;

    public AssignStmt(Stmt leftStmt, Stmt rightStmt) {
        this.leftStmt = leftStmt;
        this.rightStmt = rightStmt;
    }

    @Override
    public String getStmt(Set<Class<?>>fullCNameNeeded) {
        return (leftStmt instanceof VarStmt ? ((VarStmt) leftStmt).getDeclarationStmt(fullCNameNeeded) : leftStmt.getStmt(fullCNameNeeded)) + " = " + rightStmt.getStmt(fullCNameNeeded);
    }

    @Override
    public Set<Class<?>> getImports() {
        return Stream.of(leftStmt.getImports(), rightStmt.getImports()).flatMap(Collection::stream).collect(Collectors.toSet());
    }
}
