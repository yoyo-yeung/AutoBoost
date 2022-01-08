package program.execution.stmt;

import org.junit.Assert;
import program.execution.ExecutionTrace;

import java.util.HashSet;
import java.util.Set;

public class AssertStmt extends Stmt{
    private Stmt expected;
    private Stmt actual;
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
        Set<Class<?>> results = new HashSet<>(imports);
        results.addAll(expected.getImports());
        results.addAll(actual.getImports());
        return results;
    }
}
