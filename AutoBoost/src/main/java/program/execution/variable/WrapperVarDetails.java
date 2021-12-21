package program.execution.variable;

// store it separately from other object var as they can be directly assigned
public class WrapperVarDetails extends VarDetail{
    Class<?> type;
    Object value;

    public WrapperVarDetails() {
    }

    public WrapperVarDetails(Class<?> type) {
        this.type = type;
    }

    public WrapperVarDetails(String typeName) throws ClassNotFoundException {
        this(Class.forName(typeName));
    }

    public WrapperVarDetails(Class<?> type, Object value) {
        this.type = type;
        this.value = value;
    }
    public WrapperVarDetails(String typeName, Object value) throws ClassNotFoundException {
        this(Class.forName(typeName), value);
    }


    public void setType(Class<?> type) {
        this.type = type;
    }

    public void setType(String typeName) throws ClassNotFoundException {
        this.setType(Class.forName(typeName));
    }


    public void setValue(Object value) {
        this.value = value;
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
