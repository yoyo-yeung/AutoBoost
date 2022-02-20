package program.execution.stmt;

import helper.Properties;
import org.apache.commons.lang3.ClassUtils;
import org.junit.Assert;
import program.execution.ExecutionTrace;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
                        (doubleChecking(assertType)
                                ? ", " + MARGIN : "") + ")") ;

    }

    private boolean doubleChecking(Class<?> type){
        return type.equals(Double.class) || type.equals(double.class);
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
