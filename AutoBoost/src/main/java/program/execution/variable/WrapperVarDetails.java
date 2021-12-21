package program.execution.variable;

// store it separately from other object var as they can be directly assigned
public class WrapperVarDetails extends VarDetail{
    Class<?> type;
    Object value;

    public WrapperVarDetails(int ID, Class<?> type, Object value) {
        this.setID(ID);
        this.type = type;
        this.value = value;
    }
    public WrapperVarDetails(int ID, String typeName, Object value) throws ClassNotFoundException {
        this(ID, Class.forName(typeName), value);
    }

    @Override
    public Class<?> getType() {
        return type;
    }

    @Override
    public String getTypeSimpleName() {
        return this.type.getSimpleName();
    }

    @Override
    public Object getValue() {
        return value.toString();
    }
}
