package program.execution.stmt;

import java.util.HashSet;
import java.util.Set;

public class AssignStmt extends Stmt{
    private Stmt leftStmt;
    private Stmt rightStmt;

    public AssignStmt(Stmt leftStmt, Stmt rightStmt) {
        this.leftStmt = leftStmt;
        this.rightStmt = rightStmt;
    }

    public Stmt getLeftStmt() {
        return leftStmt;
    }

    public void setLeftStmt(Stmt leftStmt) {
        this.leftStmt = leftStmt;
    }

    public Stmt getRightStmt() {
        return rightStmt;
    }

    public void setRightStmt(Stmt rightStmt) {
        this.rightStmt = rightStmt;
    }

    @Override
    public String getStmt() {
        if(leftStmt instanceof VarStmt)
            return ((VarStmt) leftStmt).getDeclarationStmt() + "=" + rightStmt.getStmt();
        return leftStmt.getStmt() + "=" + rightStmt.getStmt();
    }

    @Override
    public Set<Class<?>> getImports() {
        Set<Class<?>> results = new HashSet<>(this.imports);
        results.addAll(leftStmt.getImports());
        results.addAll(rightStmt.getImports());
        return results;
    }
}
