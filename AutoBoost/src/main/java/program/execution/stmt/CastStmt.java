package program.execution.stmt;

import program.execution.ExecutionTrace;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    @Override
    public Set<Class<?>> getImports() {
        return Stream.of(newType, ExecutionTrace.getSingleton().getVarDetailByID(resultVarDetailID).getType()).map(Stmt::getTypeToImport).filter(Objects::nonNull).collect(Collectors.toSet());
    }
}
