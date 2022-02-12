package program.execution.stmt;

public class CastStmt extends Stmt{
    private final Class<?> newType;
    private final Stmt enclosedStmt;


    public CastStmt(int resultVarDetailID, Class<?> newType, Stmt enclosedStmt) {
        super(resultVarDetailID);
        this.newType = newType;
        this.enclosedStmt = enclosedStmt;
    }

    @Override
    public String getStmt() {
        return "(" + newType.getSimpleName()+")" + enclosedStmt.getStmt();
    }
}
