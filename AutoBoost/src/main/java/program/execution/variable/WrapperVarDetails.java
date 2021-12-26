package program.execution.variable;

import java.util.Objects;

// store it separately from other object var as they can be directly assigned
public class WrapperVarDetails extends VarDetail{
    private final Class<?> type;
    private final Object value;

    public WrapperVarDetails(int ID, Class<?> type, Object value) {
        super(ID);
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WrapperVarDetails that = (WrapperVarDetails) o;
        return Objects.equals(type, that.type) && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, value);
    }

    @Override
    public String toString() {
        return "WrapperVarDetails{" +
                "ID=" + getID() +
                ", type=" + (type == null ? "null" : type.getSimpleName() )+
                ", value=" + (value == null ? "null" : value) +
                '}';
    }

    @Override
    public String toDetailedString() {
        return this.toString();
    }
}
