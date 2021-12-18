package program.execution.variable;

import java.util.List;

public interface VarDetail {
    public String getStmt(String varName) throws NoSuchFieldException, IllegalAccessException;
    public List<String> getUsedMethodIds();

}
