package program.execution.stmt;

import helper.Properties;
import org.junit.Assert;
import program.execution.ExecutionTrace;
import java.util.*;
import program.execution.variable.EnumVarDetails;

public class AssertStmt extends Stmt{
    private final Stmt expected;
    private final Stmt actual;
    private final String MARGIN = "1e-16";

    public AssertStmt(Stmt expected, Stmt actual) {
        this.expected = expected;
        this.actual = actual;
    }

    @Override
    public String getStmt(Set<Class<?>>fullCNameNeeded) {
        Class<?> assertType = ExecutionTrace.getSingleton().getVarDetailByID(expected.resultVarDetailID).getType();

        return "Assert."  +
                (assertType.isArray() ? "assertTrue(" + (assertType.getComponentType().isArray()? "Arrays.deepEquals(" : "Arrays.equals(") + expected.getStmt(fullCNameNeeded) + "," + actual.getStmt(fullCNameNeeded) + "))" :  "assertEquals(" + expected.getStmt(fullCNameNeeded) + ", " + actual.getStmt(fullCNameNeeded) +
                        (deltaChecking(assertType)
                                ? ", " + MARGIN : "") + ")") ;

    }

    private boolean deltaChecking(Class<?> type){
        return (type.equals(Double.class) || type.equals(double.class) || type.equals(Float.class) || type.equals(float.class)) && !(ExecutionTrace.getSingleton().getVarDetailByID(expected.getResultVarDetailID()) instanceof EnumVarDetails);
    }
    @Override
    public Set<Class<?>> getImports(String packageName) {
        Set<Class<?>> imports = new HashSet<>();
//        if(Properties.getSingleton().getJunitVer() == 4)
        imports.add(Assert.class);
        if(ExecutionTrace.getSingleton().getVarDetailByID(expected.resultVarDetailID).getType().isArray())
            imports.add(Arrays.class);
        imports.addAll(expected.getImports(packageName));
        imports.addAll(actual.getImports(packageName));
        return imports;
    }
}
