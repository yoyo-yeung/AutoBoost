package program.execution.stmt;

import entity.CREATION_TYPE;
import program.execution.ExecutionTrace;
import program.execution.variable.VarDetail;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ConstantStmt extends Stmt{
    public ConstantStmt(int resultVarDetailID) {
        VarDetail varDetail = ExecutionTrace.getSingleton().getVarDetailByID(resultVarDetailID);
        if(!varDetail.getCreatedBy().equals(CREATION_TYPE.DIRECT_ASSIGN) && !varDetail.equals(ExecutionTrace.getSingleton().getNullVar()))
            throw new IllegalArgumentException("Invalid type");
        this.resultVarDetailID = resultVarDetailID;
    }

    @Override
    public String getStmt(Set<Class<?>>fullCNameNeeded) {
        return ExecutionTrace.getSingleton().getVarDetailByID(resultVarDetailID).getValue().toString();
    }

    @Override
    public Set<Class<?>> getImports() {
        return Stream.of(ExecutionTrace.getSingleton().getVarDetailByID(resultVarDetailID).getType()).map(Stmt::getTypeToImport).filter(Objects::nonNull).collect(Collectors.toSet());
    }
}
