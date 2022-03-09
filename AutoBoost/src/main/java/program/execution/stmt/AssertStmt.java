package program.execution.stmt;

import helper.Properties;
import org.junit.Assert;
import program.execution.ExecutionTrace;
import java.util.*;

public class AssertStmt extends Stmt{
    private final Stmt expected;
    private final Stmt actual;
    private final String MARGIN = "1e-16";

    public AssertStmt(Stmt expected, Stmt actual) {
        this.expected = expected;
        this.actual = actual;
    }

    @Override
    public String getStmt() {
        Class<?> assertType = ExecutionTrace.getSingleton().getVarDetailByID(expected.resultVarDetailID).getType();
        return (Properties.getSingleton().getJunitVer()==4? "Assert." : "") +
                (assertType.isArray() ? "assertTrue(" + (assertType.getComponentType().isArray()? "Arrays.deepEquals(" : "Arrays.equals(") + expected.getStmt() + "," + actual.getStmt() + "));" :  "assertEquals(" + expected.getStmt() + ", " + actual.getStmt() +
                        (deltaChecking(assertType)
                                ? ", " + MARGIN : "") + ")") ;

    }

    private boolean deltaChecking(Class<?> type){
        return type.equals(Double.class) || type.equals(double.class) || type.equals(Float.class) || type.equals(float.class);
    }
    @Override
    public Set<Class<?>> getImports() {
        Set<Class<?>> imports = new HashSet<>();
        if(Properties.getSingleton().getJunitVer() == 4)
            imports.add(Assert.class);
        if(ExecutionTrace.getSingleton().getVarDetailByID(expected.resultVarDetailID).getType().isArray())
            imports.add(Arrays.class);
        imports.addAll(expected.getImports());
        imports.addAll(actual.getImports());
        return imports;
    }
}
