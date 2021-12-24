package program.execution.variable;

import org.apache.commons.lang3.ClassUtils;

import java.util.Objects;

public class ObjVarDetails extends VarDetail{
    private final Class<?> type;
    private final String value;

    public ObjVarDetails(int ID, Class<?> type, String value) {
        super(ID);
        this.type = type;
        this.value = value;
    }

    @Override
    public Object getValue() {
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ObjVarDetails that = (ObjVarDetails) o;
        return (Objects.equals(type, that.type) || ClassUtils.isAssignable(type, that.type) || ClassUtils.isAssignable(that.type, type)) && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, value);
    }

    @Override
    public String toString() {
        return "ObjVarDetails{" +
                "ID=" + getID() +
                ", type=" + type +
                ", value=" + value +
                '}';
    }
}
