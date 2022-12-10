package program.execution.variable;

import entity.CREATION_TYPE;
import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.text.StringEscapeUtils;

public class PrimitiveVarDetails extends VarDetail{
    private static final CREATION_TYPE createdBy = CREATION_TYPE.DIRECT_ASSIGN;
    private final Class<?> type;
    private final Object value;

    // must provide all details when constructed, no updating allowed
    public PrimitiveVarDetails(int ID, Object wrapperValue)  {
        this(ID, wrapperValue.getClass(), wrapperValue);
    }
    public PrimitiveVarDetails(int ID, Class<?>type, Object wrappedValue) {
        super(ID);
        if(!type.isPrimitive())
            throw new IllegalArgumentException("Non primitive type value are being stored as primitive var");
        if(!ClassUtils.isPrimitiveWrapper(wrappedValue.getClass()))
            throw new IllegalArgumentException("Non wrapper type value provided. Cannot be cast to primitive value");
        if(!wrappedValue.getClass().isPrimitive() && !wrappedValue.getClass().equals(type) && !ClassUtils.wrapperToPrimitive(wrappedValue.getClass()).equals(type))
            throw new IllegalArgumentException("Type specified and value provided do not match");
        this.type = type;
        this.value = wrappedValue;
    }


    @Override
    public Class<?> getType() {
        return type;
    }

    @Override
    public String getTypeSimpleName() {return type.getSimpleName();}

    @Override
    public Object getGenValue()  {
        switch(type.getName()) {
            case "long":
                return this.value+"L";
            case "char":
                return "'" + StringEscapeUtils.escapeJava(this.value.toString())  + "'";
            case "float":
                return this.value + "f";
            default:
                return this.value;
        }
    }

    public Object getValue() {
        return value;
    }

    @Override
    public boolean sameTypeNValue(Class<?> type, Object v) {
        return this.type.equals(type) && this.value.equals(v);
    }

    @Override
    public boolean sameValue(Object v) {
        return this.value.equals(v);
    }
    public CREATION_TYPE getCreatedBy() {
        return createdBy;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PrimitiveVarDetails that = (PrimitiveVarDetails) o;
        return that.type.equals(this.type) && that.getGenValue().equals(this.getGenValue());
    }

    @Override
    public String toString() {
        return "PrimitiveVarDetails{" +
                "type=" + type +
                ", value=" + value +
                '}';
    }

    @Override
    public String toDetailedString() {
        return this.toString();
    }
}
