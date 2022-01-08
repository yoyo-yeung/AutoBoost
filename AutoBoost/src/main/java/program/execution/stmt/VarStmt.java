package program.execution.stmt;

public class VarStmt extends Stmt{
    String varName;
    Class<?> varType;


    public VarStmt(Class<?> varType, int varID) {
        this.varName = "var"+ varID;
        this.varType = varType;
        this.addImports(varType);
    }

    @Override
    public String getStmt() {
        return varName;
    }

    public String getDeclarationStmt() {
        return "final "+ varType.getSimpleName() + " " + varName;
    }

}
