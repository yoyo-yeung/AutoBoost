package program.execution.stmt;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class Stmt {
    private static final AtomicInteger stmtIDGenerator = new AtomicInteger(0);
    private final int ID;
    protected int resultVarDetailID;
    protected Set<Class<?>> imports = new HashSet<>();

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

    public Set<Class<?>> getImports() {return this.imports;}

    public void addImports(Class<?> importClass) {
        this.imports.add(importClass);
    }

    public void addImports(Set<Class<?>> importClass) {
        this.imports.addAll(importClass);
    }

    protected static int getNewStmtID(){
        return stmtIDGenerator.incrementAndGet();
    }
}
