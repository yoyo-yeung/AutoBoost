package program.execution.variable;

import program.execution.ExecutionTrace;

import java.util.List;
import java.util.stream.Collectors;

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
        this.type = value.getClass();
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

    @Override
    public String toString() {
        return "ArrVarDetails{" +
                "componentType=" + componentType +
                ", type=" + type +
                ", components=" + components +
                ", value=" + value +
                '}';
    }

    @Override
    public String toDetailedString() {
        return "ArrVarDetails{" +
                "componentType=" + (componentType == null ? "null" : componentType.getSimpleName()) +
                ", type=" + (type == null ? "null" : type.getSimpleName()) +
                ", components=" + (components == null ? "null" : components.stream().filter(c -> c!= -1).map(c -> ExecutionTrace.getSingleton().getVarDetailByID(c).toString()).collect(Collectors.joining(","))) +
                ", value=" + (value == null ? "null" : value.toString()) +
                '}';

    }
}
