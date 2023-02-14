package kwyyeung.autoboost.program.execution.stmt;

import kwyyeung.autoboost.program.execution.ExecutionTrace;
import org.junit.Assert;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class AssertStmt extends Stmt {
    private final Stmt expected;
    private final Stmt actual;
    private final String MARGIN = "1e-16";

    public AssertStmt(Stmt expected, Stmt actual) {
        this.expected = expected;
        this.actual = actual;
    }

    @Override
    public String getStmt(Set<Class<?>> fullCNameNeeded) {
        Class<?> assertType = ExecutionTrace.getSingleton().getVarDetailByID(expected.resultVarDetailID).getType();

        return "Assert." +
                (assertType.isArray() ? "assertTrue(" + (assertType.getComponentType().isArray() ? "Arrays.deepEquals(" : "Arrays.equals(") + expected.getStmt(fullCNameNeeded) + "," + actual.getStmt(fullCNameNeeded) + "))" : "assertEquals(" + expected.getStmt(fullCNameNeeded) + ", " + actual.getStmt(fullCNameNeeded) +
                        (deltaChecking(assertType)
                                ? ", " + MARGIN : "") + ")");

    }

    private boolean deltaChecking(Class<?> type) {
        return (type.equals(Double.class) || type.equals(double.class) || type.equals(Float.class) || type.equals(float.class));
    }

    @Override
    public Set<Class<?>> getImports(String packageName) {
        Set<Class<?>> imports = new HashSet<>();
//        if(Properties.getSingleton().getJunitVer() == 4)
        imports.add(Assert.class);
        if (ExecutionTrace.getSingleton().getVarDetailByID(expected.resultVarDetailID).getType().isArray())
            imports.add(Arrays.class);
        imports.addAll(expected.getImports(packageName));
        imports.addAll(actual.getImports(packageName));
        return imports;
    }
}
