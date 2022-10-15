package program.execution.variable;

import entity.CREATION_TYPE;
import helper.Properties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import program.execution.ExecutionTrace;
import soot.Modifier;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ArrVarDetails extends VarDetail{
    private static final CREATION_TYPE createdBy = CREATION_TYPE.CONSTRUCTOR;
    private static final Logger logger = LogManager.getLogger(ArrVarDetails.class);
    Class<?> componentType;
    Class<?> type;
    List<Integer> components;
    String componentValue;
    Object value;

    Integer defaultComponent;

    private int size;

    public ArrVarDetails(int ID, List<Integer> components, Object value, int size, int defaultComponent) {
        super(ID);
        if(value == null || components == null) throw new IllegalArgumentException("Value details not provided");
        if (!availableTypeCheck(value.getClass())) throw new IllegalArgumentException("Non array type value provided");
        this.componentType = value.getClass().getComponentType();
        this.type = value.getClass();
        this.components = components;
        this.componentValue = components.stream().map(String::valueOf).collect(Collectors.joining(Properties.getDELIMITER()));
        this.value = value;
        this.size = size;
        this.defaultComponent = defaultComponent;
    }

    public Class<?> getComponentType() {
        return componentType;
    }

    public List<Integer> getComponents() {
        List<Integer> res = new ArrayList<>(components);
        while(res.size()< size) res.add(defaultComponent);
        return res;
    }

    public Set<Class<?>> getLeaveType() {
        Set<Class<?>> types = new HashSet<>();
        types = components.stream().flatMap(id -> {
            VarDetail component = ExecutionTrace.getSingleton().getVarDetailByID(id);
            if(component instanceof  ArrVarDetails) return ((ArrVarDetails) component).getLeaveType().stream();
            else return Stream.of(component.getType());
        }).collect(Collectors.toSet());

        return types;
    }

    @Override
    public Object getGenValue() {
        return componentValue;
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
                '}';
    }

    @Override
    public String toDetailedString() {
        return "ArrVarDetails{" +
                "ID=" + getID() +
                ", componentType=" + (componentType == null ? "null" : componentType.getSimpleName()) +
                ", type=" + (type == null ? "null" : type.getSimpleName()) +
                ", components=" + (components == null ? "null" : components.stream().filter(c -> c!= -1).map(c -> ExecutionTrace.getSingleton().getVarDetailByID(c).toDetailedString()).collect(Collectors.joining(Properties.getDELIMITER()))) +
                '}';

    }

    public static boolean availableTypeCheck(Class<?> type ){
        return type.isArray() || ((List.class.isAssignableFrom(type)|| Set.class.isAssignableFrom(type)) && type.getName().startsWith("java.") && Modifier.isPublic(type.getModifiers()));
    }

    public CREATION_TYPE getCreatedBy() {
        return createdBy;
    }

}
