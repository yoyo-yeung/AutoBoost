package program.execution.stmt;

import helper.Helper;
import program.generation.TestGenerator;

import java.util.Set;
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

    public abstract String getStmt(Set<Class<?>> fullCNameNeeded);

    public abstract Set<Class<?>> getImports(String packageName);

    protected static int getNewStmtID(){
        return stmtIDGenerator.incrementAndGet();
    }

    protected static Class<?> getTypeToImport(Class<?> type, String packageName) {
        if (type.isArray()) {
            while (type.isArray())
                type = type.getComponentType();
        }
        if(type.isPrimitive() || type.equals(Object.class))
            return null;
        else return Helper.getAccessibleSuperType(type, packageName);
    }
}
