package program.execution.variable;

import java.util.HashMap;
import java.util.List;

public class ArrVarDetails extends VarDetail{
    Class<?> componentType;
    Class<?> type;
    int dimension;
    List<VarDetail> components;
    Object value;

    public ArrVarDetails(int ID, Object value) {
        super(ID);
        this.value = value;
    }

    @Override
    public Object getValue() throws Exception {
        return value;
    }

    @Override
    public Class<?> getType() {
        return this.type;
    }

    @Override
    public String getTypeSimpleName() {
        return this.type.getSimpleName();
    }
}
