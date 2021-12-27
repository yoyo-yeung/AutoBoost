package program.execution.variable;

import org.apache.commons.lang3.ClassUtils;
import program.execution.ExecutionTrace;

import java.lang.reflect.Array;
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
        if (!value.getClass().isArray() && !ClassUtils.getAllInterfaces(value.getClass()).contains(List.class)) throw new IllegalArgumentException("Non array type value provided");
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
        if(components == null)
            return null;
        return components.stream().map(String::valueOf).collect(Collectors.joining(","));
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
                "ID=" + getID() +
                ", componentType=" + (componentType == null ? "null" : componentType.getSimpleName()) +
                ", type=" + (type == null ? "null" : type.getSimpleName())  +
                ", components=" + (components == null ? "null" : components.toString()) +
                ", value=" + (value == null ? "null" : value.toString()) +
                '}';
    }

    @Override
    public String toDetailedString() {
        return "ArrVarDetails{" +
                "ID=" + getID() +
                ", componentType=" + (componentType == null ? "null" : componentType.getSimpleName()) +
                ", type=" + (type == null ? "null" : type.getSimpleName()) +
                ", components=" + (components == null ? "null" : components.stream().filter(c -> c!= -1).map(c -> ExecutionTrace.getSingleton().getVarDetailByID(c).toString()).collect(Collectors.joining(","))) +
                ", value=" + (value == null ? "null" : value.toString()) +
                '}';

    }
}
