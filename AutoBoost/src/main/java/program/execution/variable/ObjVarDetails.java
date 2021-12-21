package program.execution.variable;

import org.apache.commons.lang3.ClassUtils;

import java.util.Objects;

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
}
