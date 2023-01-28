package kwyyeung.autoboost.program.execution.stmt;

import kwyyeung.autoboost.helper.Helper;
import kwyyeung.autoboost.program.execution.ExecutionTrace;
import org.mockito.ArgumentMatchers;

import java.util.Collections;
import java.util.Set;

public class ArgumentMatcherStmt extends Stmt {

    public ArgumentMatcherStmt(int resultVarDetailID) {
        super(resultVarDetailID);
    }

    @Override
    public String getStmt(Set<Class<?>> fullCNameNeeded) {
        return "ArgumentMatchers.any(" + Helper.getClassNameToOutput(fullCNameNeeded, ExecutionTrace.getSingleton().getVarDetailByID(resultVarDetailID).getType()) + ".class)";
    }

    @Override
    public Set<Class<?>> getImports(String packageName) {
        return Collections.singleton(ArgumentMatchers.class);
    }
}
