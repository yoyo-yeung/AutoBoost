package program.execution.stmt;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class Stmt {
    private static final AtomicInteger stmtIDGenerator = new AtomicInteger(0);
    private final int ID;
    protected int resultVarDetailID;

    public Stmt() {
        ID = getNewStmtID();
    }

    public Stmt(int resultVarDetailID) {
        this();
        this.resultVarDetailID = resultVarDetailID;
    }

    public int getID() {
        return ID;
    }

    public void setResultVarDetailID(int resultVarDetailID) {
        this.resultVarDetailID = resultVarDetailID;
    }

    public int getResultVarDetailID() {
        return resultVarDetailID;
    }

    public abstract String getStmt();

    public abstract List<Class<?>> getImports();

    protected static int getNewStmtID(){
        return stmtIDGenerator.incrementAndGet();
    }
}
