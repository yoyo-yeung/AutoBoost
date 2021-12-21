package program.execution.variable;

import java.util.HashMap;
import java.util.List;

public class ArrVarDetails extends VarDetail{
    Class<?> componentType;
    int dimension;
    HashMap<List<Integer>, Object> componentValueMap;
    Object value;


    @Override
    public Object getValue() throws Exception {
        return null;
    }

    @Override
    public Class<?> getType() {
        return null;
    }

    @Override
    public String getTypeSimpleName() {
        return null;
    }
}
