package program.execution.stmt;

import entity.CREATION_TYPE;
import program.execution.ExecutionTrace;
import program.execution.variable.VarDetail;

public class ConstantStmt extends Stmt{
    public ConstantStmt(int resultVarDetailID) {
        VarDetail varDetail = ExecutionTrace.getSingleton().getVarDetailByID(resultVarDetailID);
        if(!varDetail.getCreatedBy().equals(CREATION_TYPE.DIRECT_ASSIGN))
            throw new IllegalArgumentException("Invalid type");
        this.resultVarDetailID = resultVarDetailID;
        if(!varDetail.getType().isPrimitive() && !varDetail.getType().isArray())
            this.imports.add(varDetail.getType());
    }

    @Override
    public String getStmt() {
        return ExecutionTrace.getSingleton().getVarDetailByID(resultVarDetailID).getValue().toString();
    }

}
