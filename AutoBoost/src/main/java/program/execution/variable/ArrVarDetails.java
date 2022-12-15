package program.execution.variable;

import entity.CREATION_TYPE;
import helper.Properties;
import helper.xml.XMLParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import program.execution.ExecutionTrace;
import soot.Modifier;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ArrVarDetails extends VarDetail {
    private static final CREATION_TYPE createdBy = CREATION_TYPE.CONSTRUCTOR;
    private static final Logger logger = LogManager.getLogger(ArrVarDetails.class);
    Class<?> type;
    List<Integer> components;
    Object value;

    public ArrVarDetails(int ID, Class<?> type, List<Integer> components, Object value) {
        super(ID);
        if (value == null) throw new IllegalArgumentException("Value details not provided");
        this.type = type;
        this.components = components;
        this.value = value;
    }


    public List<Integer> getComponents() {
        if(components == null) {
            components = XMLParser.fromXMLtoVarDetailIDList(this, (String) value);
        }
        return components;
    }

    public Set<Class<?>> getLeaveType() {
        Set<Class<?>> types = new HashSet<>();
        types = components.stream().flatMap(id -> {
            VarDetail component = ExecutionTrace.getSingleton().getVarDetailByID(id);
            if (component instanceof ArrVarDetails) return ((ArrVarDetails) component).getLeaveType().stream();
            else return Stream.of(component.getType());
        }).collect(Collectors.toSet());

        return types;
    }

    @Override
    public Object getGenValue() {
        return this.value;
    }

    @Override
    public boolean sameTypeNValue(Class<?> type, Object v) {
        return this.getType().equals(type) && Objects.equals(value, v);
    }

    @Override
    public boolean sameValue(Object v) {
        return Objects.equals(this.value, v);
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
                ", type=" + (type == null ? "null" : type.getSimpleName()) +
                ", components=" + (components == null ? "null" : components.toString()) +
                ", value=" + value +
                '}';
    }

    @Override
    public String toDetailedString() {
        return "ArrVarDetails{" +
                "ID=" + getID() +
                ", type=" + (type == null ? "null" : type.getSimpleName()) +
                ", components=" + (components == null ? "null" : components.stream().filter(c -> c != -1).map(c -> ExecutionTrace.getSingleton().getVarDetailByID(c).toDetailedString()).collect(Collectors.joining(Properties.getDELIMITER()))) +
                ", value=" + value +
                '}';

    }

    public static boolean availableTypeCheck(Class<?> type) {
        return type.isArray() || ((List.class.isAssignableFrom(type) || Set.class.isAssignableFrom(type)) && type.getName().startsWith("java.") && Modifier.isPublic(type.getModifiers()));
    }

    public CREATION_TYPE getCreatedBy() {
        return createdBy;
    }

}
