package program.execution.variable;

import entity.CREATION_TYPE;
import helper.Properties;
import program.execution.ExecutionTrace;

import java.util.Map;
import java.util.stream.Collectors;

public class MapVarDetails extends VarDetail{
    private static final CREATION_TYPE createdBy = CREATION_TYPE.CONSTRUCTOR;
    private Class<?> type;
    private Map<Integer, Integer> keyValuePairs;
    private Object value;

    public MapVarDetails(int ID, Class<?> type, Map<Integer, Integer> keyValuePairs, Object value) {
        super(ID);
        this.type = type;
        this.keyValuePairs = keyValuePairs;
        this.value = value;
    }


    public Map<Integer, Integer> getKeyValuePairs() {
        return keyValuePairs;
    }

    @Override
    public Object getValue() {
        if(keyValuePairs == null) return null;
        else return keyValuePairs.entrySet().stream().map(e -> e.getKey()+"="+e.getValue()).sorted().collect(Collectors.joining(Properties.getDELIMITER()));

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
                ", keyValuePairs=" + (keyValuePairs == null ? "null" : keyValuePairs.entrySet().stream().map(e -> e.getKey()+"=" +e.getValue()).collect(Collectors.joining(","))) +
                ", value=" + (value == null ? "null" : value) +
                '}';
    }

    @Override
    public String toDetailedString() {
        ExecutionTrace trace = ExecutionTrace.getSingleton();
        return "MapVarDetails{" +
                "type=" + (type == null ? "null" : type.getName()) +
                ", keyValuePairs=" + (keyValuePairs == null ? "null" : keyValuePairs.entrySet().stream().map(e -> trace.getVarDetailByID(e.getKey()).toDetailedString()+"=" +trace.getVarDetailByID(e.getValue()).toDetailedString()).collect(Collectors.joining(","))) +
                ", value=" + (value == null ? "null" : value) +
                '}';
    }
}
