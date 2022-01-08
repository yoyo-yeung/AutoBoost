package program.execution.stmt;

import org.junit.Assert;
import program.execution.ExecutionTrace;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class AssertStmt extends Stmt{
    private final Stmt expected;
    private final Stmt actual;
    private final String MARGIN = "1e-16";

    public AssertStmt(Stmt expected, Stmt actual) {
        this.expected = expected;
        this.actual = actual;
        this.addImports(Assert.class);
    }

    @Override
    public String getStmt() {
        Class<?> assertType = ExecutionTrace.getSingleton().getVarDetailByID(expected.resultVarDetailID).getType();
        return "Assert.assertEquals(" + expected.getStmt() + "," + actual.getStmt() + (assertType.equals(Double.class) || assertType.equals(double.class) ?  ", " + MARGIN : "")+")";
    }

    @Override
    public Set<Class<?>> getImports() {
        return Stream.of(this.imports, expected.getImports(), actual.getImports()).flatMap(Collection::stream).collect(Collectors.toSet());
    }
}
