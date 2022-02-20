package program.execution.stmt;

import org.apache.commons.lang3.ClassUtils;

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

    public abstract String getStmt();

    public abstract Set<Class<?>> getImports();

    protected static int getNewStmtID(){
        return stmtIDGenerator.incrementAndGet();
    }

    protected static Class<?> getTypeToImport(Class<?> type) {
        if (type.getName().contains("$")) return null;
        if (type.isArray()) {
            while (type.isArray())
                type = type.getComponentType();
        }
        if (!ClassUtils.isPrimitiveOrWrapper(type))
            return type;
        return null;
    }
}
