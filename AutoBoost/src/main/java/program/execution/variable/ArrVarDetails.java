package program.execution.variable;

import java.util.List;

public class ArrVarDetails extends VarDetail{
    Class<?> componentType;
    Class<?> type;
    List<Integer> components;
    Object value;

    public ArrVarDetails(int ID, List<Integer> components, Object value) {
        super(ID);
        if(value == null || components == null) throw new IllegalArgumentException("Value details not provided");
        if (!value.getClass().isArray()) throw new IllegalArgumentException("Non array type value provided");
        this.componentType = value.getClass().getComponentType();
        this.components = components;
        this.value = value;
    }

    public Class<?> getComponentType() {
        return componentType;
    }

    public List<Integer> getComponents() {
        return components;
    }

    @Override
    public Object getValue() {
        return value; // change later (test case generation related)
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
