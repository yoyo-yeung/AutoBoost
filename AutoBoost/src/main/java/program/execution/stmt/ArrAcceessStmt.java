package program.execution.stmt;

import java.util.List;
import java.util.Set;

public class ArrAcceessStmt extends Stmt {
    private final Stmt baseStmt;
    private final int accessKey;
    private final ACCESS_TYPE accessType;

    public ArrAcceessStmt(int resultVarDetailID, Stmt baseStmt, int accessKey, Class<?> baseClass) {
        super(resultVarDetailID);
        this.baseStmt = baseStmt;
        this.accessKey = accessKey;
        if (baseClass.isArray()) accessType = ACCESS_TYPE.ARRAY_ACCESS;
        else if (List.class.isAssignableFrom(baseClass)) accessType = ACCESS_TYPE.LIST_ACCESS;
        else throw new IllegalArgumentException("The variable involved cannot be accessed by using .get or []");
    }

    @Override
    public String getStmt(Set<Class<?>> fullCNameNeeded) {
        return this.baseStmt.getStmt(fullCNameNeeded) + String.format(this.accessType.getAccessStringFormat(), accessKey);
    }

    @Override
    public Set<Class<?>> getImports(String packageName) {
        return baseStmt.getImports(packageName);
    }

    private enum ACCESS_TYPE {
        ARRAY_ACCESS("ARRAY", "[%d]"),
        LIST_ACCESS("LIST", ".get(%d)");

        private final String type;
        private final String accessStringFormat;

        ACCESS_TYPE(String type, String accessStringFormat) {
            this.type = type;
            this.accessStringFormat = accessStringFormat;
        }

        public String getType() {
            return type;
        }

        public String getAccessStringFormat() {
            return accessStringFormat;
        }
    }
}
