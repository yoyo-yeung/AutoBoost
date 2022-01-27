package program.execution.stmt;

import helper.Properties;
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
        if(Properties.getSingleton().getJunitVer() == 4)
            this.addImports(Assert.class);
        if(ExecutionTrace.getSingleton().getVarDetailByID(expected.resultVarDetailID).getType().isArray())
            this.addImports(Arrays.class);
    }

    @Override
    public String getStmt() {
        Class<?> assertType = ExecutionTrace.getSingleton().getVarDetailByID(expected.resultVarDetailID).getType();
        if(assertType.isArray()) {
            return  "Assert.assertTrue(" + (assertType.getComponentType().isArray()? "Arrays.deepEquals(" : "Arrays.equals(") + expected.getStmt() + "," + actual.getStmt() + "));";
        }
//        if(assertType.isArray() && ExecutionTrace.getSingleton().getVarDetailByID(actual.resultVarDetailID).getType().isArray() && (!(expected instanceof VarStmt) || ((((VarStmt)expected).actualType.isArray()) && ((VarStmt)expected).varType.contains("[]"))) && (!(actual instanceof VarStmt) || (((VarStmt)actual).actualType.isArray())&& ((VarStmt)actual).varType.contains("[]")) )
//            return getArrStmt(expected.getStmt(), actual.getStmt(), expected.getResultVarDetailID(), assertType);
        return (Properties.getSingleton().getJunitVer()==4? "Assert." : "") + "assertEquals(" + expected.getStmt() + ", " + actual.getStmt() + (doubleChecking(assertType) && doubleChecking(ExecutionTrace.getSingleton().getVarDetailByID(actual.resultVarDetailID).getType())  && ((! (actual instanceof VarStmt) || ((VarStmt)actual).varType.equalsIgnoreCase("double")) ) && ((! (expected instanceof VarStmt) || ((VarStmt)expected).varType.equalsIgnoreCase("double")) )? ", " + MARGIN : "") + ")";
    }


    private boolean doubleChecking(Class<?> type){
        return type.equals(Double.class) || type.equals(double.class);
    }
    @Override
    public Set<Class<?>> getImports() {
        return Stream.of(this.imports, expected.getImports(), actual.getImports()).flatMap(Collection::stream).collect(Collectors.toSet());
    }
}
