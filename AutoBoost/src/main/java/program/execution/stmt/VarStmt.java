package program.execution.stmt;

import java.util.HashSet;
import java.util.Set;

public class VarStmt extends Stmt{
    String varName;
    Class<?> varType;

    public VarStmt(Class<?> actualType, int varID, int resultVarDetailID) {
        this.varName = "var"+ varID;
        this.varType = actualType;
        this.resultVarDetailID = resultVarDetailID;
    }

    public String getVarName() {
        return varName;
    }

    public Class<?> getVarType() {
        return varType;
    }

    @Override
    public String getStmt() {
        return varName;
    }

    public String getDeclarationStmt() {
        return "final "+ varType.getSimpleName() + " " + varName;
    }

    @Override
    public Set<Class<?>> getImports() {
        Set<Class<?>> imports = new HashSet<>();
        imports.add(getTypeToImport(varType));
        imports.remove(null);
        return imports;
    }
}
