package program.execution.variable;

public class ObjVarDetails extends VarDetail{
    Class<?> type;
    Object value;

    public ObjVarDetails(int ID, Object value) {
        this(ID, value.getClass(), value);
    }

    public ObjVarDetails(int ID, Class<?> type, Object value) {
        this.setID(ID);
        this.type = type;
        this.value = value;
    }

    @Override
    public Object getValue() throws Exception {
        return value;
    }

    @Override
    public Class<?> getType() {
        return type;
    }

    @Override
    public String getTypeSimpleName() {
        return type.getSimpleName();
    }
}
