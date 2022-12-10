package program.execution.stmt;

import program.execution.ExecutionTrace;
import program.execution.variable.PrimitiveVarDetails;
import program.execution.variable.VarDetail;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CastStmt extends Stmt {
    private final Class<?> newType;
    private final Stmt enclosedStmt;


    public CastStmt(int resultVarDetailID, Class<?> newType, Stmt enclosedStmt) {
        super(resultVarDetailID);
        this.newType = newType;
        this.enclosedStmt = enclosedStmt;
    }

    @Override
    public String getStmt(Set<Class<?>> fullCNameNeeded) {
        VarDetail result = ExecutionTrace.getSingleton().getVarDetailByID(enclosedStmt.getResultVarDetailID());
        return "(" + (fullCNameNeeded.contains(newType) ? newType.getName().replace("$", ".") : newType.getSimpleName()) + ")" + ((result instanceof PrimitiveVarDetails ? "(" : "") + enclosedStmt.getStmt(fullCNameNeeded) + (result instanceof PrimitiveVarDetails ? ")" : ""));
    }

    @Override
    public Set<Class<?>> getImports(String packageName) {
        return Stream.of(Collections.singleton(newType), enclosedStmt.getImports(packageName)).flatMap(Collection::stream).map(c -> getTypeToImport(c, packageName)).filter(Objects::nonNull).collect(Collectors.toSet());
    }
}
