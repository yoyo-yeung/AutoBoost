package program.execution.variable;

import entity.CREATION_TYPE;
import helper.Properties;
import org.apache.commons.lang3.ClassUtils;
import program.execution.ExecutionTrace;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ArrVarDetails extends VarDetail{
    private static final CREATION_TYPE createdBy = CREATION_TYPE.CONSTRUCTOR;
    Class<?> componentType;
    Class<?> type;
    List<Integer> components;
    Object value;

    public ArrVarDetails(int ID, List<Integer> components, Object value) {
        super(ID);
        if(value == null || components == null) throw new IllegalArgumentException("Value details not provided");
        if (!availableTypeCheck(value.getClass())) throw new IllegalArgumentException("Non array type value provided");
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
        return components.stream().map(String::valueOf).collect(Collectors.joining(Properties.getDELIMITER()));
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
                ", components=" + (components == null ? "null" : components.stream().filter(c -> c!= -1).map(c -> ExecutionTrace.getSingleton().getVarDetailByID(c).toDetailedString()).collect(Collectors.joining(Properties.getDELIMITER()))) +
                ", value=" + (value == null ? "null" : value.toString()) +
                '}';

    }

    public static boolean availableTypeCheck(Class<?> type ){
        return type.isArray() || ClassUtils.getAllInterfaces(type).contains(List.class)|| ClassUtils.getAllInterfaces(type).contains(Set.class);
    }

    public CREATION_TYPE getCreatedBy() {
        return createdBy;
    }

}
