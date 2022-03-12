package program.execution.variable;

import entity.CREATION_TYPE;
import org.apache.commons.lang3.ClassUtils;

import java.util.Objects;

public class ObjVarDetails extends VarDetail{
    private static final CREATION_TYPE createdBy = CREATION_TYPE.CONSTRUCTOR;
    private final Class<?> type;
    private final String value;

    public ObjVarDetails(int ID, Class<?> type, String value) {
        super(ID);
        this.type = type;
        this.value = value;
    }

    @Override
    public Object getGenValue() {
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

    public CREATION_TYPE getCreatedBy() {
        return createdBy;
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
                ", type=" + (type == null ? "null" : type.getSimpleName()) +
                ", value=" + (value == null ? "null" : value) +
                '}';
    }

    @Override
    public String toDetailedString() {
        return this.toString();
    }
}
