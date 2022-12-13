package program.execution.stmt;

import helper.Helper;

import java.util.HashSet;
import java.util.Set;

public class VarStmt extends Stmt {
    String varName;
    Class<?> varType;

    public VarStmt(Class<?> actualType, int varID, int resultVarDetailID) {
        this.varName = "var" + varID;
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
    public String getStmt(Set<Class<?>> fullCNameNeeded) {
        return varName;
    }

    public String getDeclarationStmt(Set<Class<?>> fullCNameNeeded) {
        return "final " + Helper.getClassNameToOutput(fullCNameNeeded, varType) + " " + varName;
    }

    @Override
    public Set<Class<?>> getImports(String packageName) {
        Set<Class<?>> imports = new HashSet<>();
        imports.add(getTypeToImport(varType, packageName));
        imports.remove(null);
        return imports;
    }
}
