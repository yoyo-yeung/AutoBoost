package program.execution.stmt;

import java.util.List;

public class VarStmt extends Stmt{
    String varName;
    Class<?> varType;


    public VarStmt(Class<?>varType) {
        this.varName = "var"+this.getID();
        this.varType = varType;
    }

    @Override
    public String getStmt() {
        return varName;
    }

    public String getDeclarationStmt() {
        return "final "+ varType.getSimpleName() + " " + varName;
    }

    @Override
    public List<Class<?>> getImports() {
        return null;
    }
}
