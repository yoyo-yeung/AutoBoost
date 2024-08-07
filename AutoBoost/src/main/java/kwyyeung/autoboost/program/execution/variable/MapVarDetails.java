package kwyyeung.autoboost.program.execution.variable;

import kwyyeung.autoboost.entity.CREATION_TYPE;
import kwyyeung.autoboost.helper.xml.XMLParser;
import kwyyeung.autoboost.program.execution.ExecutionTrace;
import soot.Modifier;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class MapVarDetails extends VarDetail {
    private static final CREATION_TYPE createdBy = CREATION_TYPE.CONSTRUCTOR;
    private final Class<? extends Map> type;
    private Set<Map.Entry<Integer, Integer>> keyValuePairs;
    private final Object value;

    public MapVarDetails(int ID, Class<? extends Map> type, Set<Map.Entry<Integer, Integer>> keyValuePairs, Object value) {
        super(ID);
        this.type = type;
        this.keyValuePairs = keyValuePairs;
        this.value = value;
    }


    public Set<Map.Entry<Integer, Integer>> getKeyValuePairs() {
        if(keyValuePairs == null) {
            keyValuePairs = XMLParser.fromXMLtoVarDetailIDMap(this, String.valueOf(value));
        }
        return keyValuePairs;
    }

    @Override
    public Object getGenValue() {
        return this.value;
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
    public CREATION_TYPE getCreatedBy() {
        return createdBy;
    }

    @Override
    public String toString() {
        return "MapVarDetails{" +
                "type=" + (type == null ? "null" : type.getName()) +
                ", keyValuePairs=" + (keyValuePairs == null ? "null" : keyValuePairs.stream().map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining(","))) +
                '}';
    }

    @Override
    public String toDetailedString() {
        ExecutionTrace trace = ExecutionTrace.getSingleton();
        return "MapVarDetails{" +
                "type=" + (type == null ? "null" : type.getName()) +
                ", keyValuePairs=" + (keyValuePairs == null ? "null" : keyValuePairs.stream().map(e -> trace.getVarDetailByID(e.getKey()).toDetailedString() + "=" + trace.getVarDetailByID(e.getValue()).toDetailedString()).collect(Collectors.joining(","))) +
                '}';
    }

    public static boolean availableTypeCheck(Class<?> type) {
        return Map.class.isAssignableFrom(type) && type.getName().startsWith("java.") && Modifier.isPublic(type.getModifiers());
    }

}
