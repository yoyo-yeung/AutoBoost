package program.execution.stmt;

import helper.Properties;

import java.util.List;

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
    public List<Class<?>> getImports() {
        return null;
    }
}
