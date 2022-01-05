package program.execution.stmt;

import entity.CREATION_TYPE;
import program.execution.ExecutionTrace;
import program.execution.variable.PrimitiveVarDetails;
import program.execution.variable.VarDetail;
import program.execution.variable.WrapperVarDetails;

import java.util.List;

public class ConstantStmt extends Stmt{
    public ConstantStmt(int resultVarDetailID) {
        this.resultVarDetailID = resultVarDetailID;
    }

    @Override
    public String getStmt() {
        return ExecutionTrace.getSingleton().getVarDetailByID(resultVarDetailID).getValue().toString();
    }

    @Override
    public List<Class<?>> getImports() {
        return null;
    }
}
