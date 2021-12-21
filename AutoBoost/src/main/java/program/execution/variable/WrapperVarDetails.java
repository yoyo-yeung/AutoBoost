package program.execution.variable;

// store it separately from other object var as they can be directly assigned
public class WrapperVarDetails extends VarDetail{
    Class<?> type;
    Object value;

    public WrapperVarDetails(Class<?> type, Object value) {
        this.type = type;
        this.value = value;
    }
    public WrapperVarDetails(String typeName, Object value) throws ClassNotFoundException {
        this(Class.forName(typeName), value);
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
