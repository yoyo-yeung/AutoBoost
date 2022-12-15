package kwyyeung.autoboost.program.execution.variable;

import kwyyeung.autoboost.entity.CREATION_TYPE;
import kwyyeung.autoboost.program.execution.ExecutionTrace;

import java.util.Objects;

public class StringBVarDetails extends VarDetail {
    private static final CREATION_TYPE createdBy = CREATION_TYPE.CONSTRUCTOR;
    private final Class<?> type;
    private final int stringValID;

    public StringBVarDetails(int ID, Class<?> type, int stringValID) {
        super(ID);
        this.type = type;
        this.stringValID = stringValID;
    }

    @Override
    public Object getValue() {
        return ExecutionTrace.getSingleton().getVarDetailByID(stringValID).getValue();
    }

    public int getStringValID() {
        return stringValID;
    }

    @Override
    public CREATION_TYPE getCreatedBy() {
        return createdBy;
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
    public Object getGenValue() {
        return ExecutionTrace.getSingleton().getVarDetailByID(stringValID).getGenValue();
    }

    @Override
    public boolean sameTypeNValue(Class<?> type, Object v) {
        return this.type.equals(type) && v instanceof Integer && v.equals(stringValID);
    }

    @Override
    public boolean sameValue(Object v) {
        return v instanceof Integer && v.equals(stringValID);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        StringBVarDetails that = (StringBVarDetails) o;
        return Objects.equals(type, that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), type);
    }

    @Override
    public String toString() {
        return "StringBVarDetails{" +
                "type=" + type +
                ", stringValID=" + stringValID +
                '}';
    }

    @Override
    public String toDetailedString() {
        return this.toString();
    }

    public static boolean availableTypeCheck(Class<?> type) {
        return type.equals(StringBuffer.class) || type.equals(StringBuilder.class);
    }
}
