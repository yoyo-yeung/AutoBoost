package program.execution.stmt;

import program.execution.ExecutionTrace;

import java.util.Collection;
import java.util.Collections;
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
    public String getStmt(Set<Class<?>>fullCNameNeeded) {
        return "(" + (fullCNameNeeded.contains(newType) ? newType.getName() : newType.getSimpleName())+")" + enclosedStmt.getStmt(fullCNameNeeded);
    }

    @Override
    public Set<Class<?>> getImports() {
        return Stream.of(Collections.singleton(newType), enclosedStmt.getImports()).flatMap(Collection::stream).map(Stmt::getTypeToImport).filter(Objects::nonNull).collect(Collectors.toSet());
    }
}
