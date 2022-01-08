package program.execution.stmt;

import helper.Properties;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PairStmt extends Stmt{
    private final Stmt keyStmt;
    private final Stmt valueStmt;

    public PairStmt(Stmt keyStmt, Stmt valueStmt) {
        this.keyStmt = keyStmt;
        this.valueStmt = valueStmt;

    }

    @Override
    public String getStmt() {
        return keyStmt.getStmt() + Properties.getDELIMITER() + valueStmt.getStmt();
    }

    @Override
    public Set<Class<?>> getImports() {
        Set<Class<?>> results = new HashSet<>(this.imports);
        results.addAll(keyStmt.getImports());
        results.addAll(valueStmt.getImports());
        return results;
    }
}
