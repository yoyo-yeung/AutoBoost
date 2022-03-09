package program.execution.variable;

import entity.CREATION_TYPE;
import org.apache.commons.lang3.ClassUtils;

import java.lang.reflect.Field;
import java.util.Objects;

public class PrimitiveVarDetails extends VarDetail{
    private static final CREATION_TYPE createdBy = CREATION_TYPE.DIRECT_ASSIGN;
    private final Class<?> type;
    private byte byteValue;
    private short shortValue;
    private int intValue;
    private long longValue;
    private float floatValue;
    private double doubleValue;
    private char charValue;
    private boolean booleanValue;

    // must provide all details when constructed, no updating allowed
    public PrimitiveVarDetails(int ID, Object wrapperValue) throws NoSuchFieldException, IllegalAccessException {
        this(ID, wrapperValue.getClass(), wrapperValue);
    }
    public PrimitiveVarDetails(int ID, Class<?>type, Object wrappedValue) throws NoSuchFieldException, IllegalAccessException {
        super(ID);
        if(!type.isPrimitive())
            throw new IllegalArgumentException("Non primitive type value are being stored as primitive var");
        if(!ClassUtils.isPrimitiveWrapper(wrappedValue.getClass()))
            throw new IllegalArgumentException("Non wrapper type value provided. Cannot be cast to primitive value");
        if(!ClassUtils.wrapperToPrimitive(wrappedValue.getClass()).equals(type))
            throw new IllegalArgumentException("Type specified and value provided do not match");
        this.type = type;
        Field field = getClass().getDeclaredField(type.getName()+"Value");
        field.set(this, wrappedValue);
    }


    @Override
    public Class<?> getType() {
        return type;
    }

    @Override
    public String getTypeSimpleName() {return type.getSimpleName();}

    @Override
    public Object getValue()  {
        switch(type.getName()) {
            case "byte":
                return this.byteValue;
            case "short":
                return this.shortValue;
            case "int":
                return this.intValue;
            case "long":
                return this.longValue+"L";
            case "float":
                return this.floatValue;
            case "double":
                return this.doubleValue;
            case "char":
                return "'" + this.charValue + "'";
            case "boolean":
                return this.booleanValue;
            default:
                return null;
        }
    }

    public CREATION_TYPE getCreatedBy() {
        return createdBy;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PrimitiveVarDetails that = (PrimitiveVarDetails) o;
        return that.type.equals(this.type) && that.getValue().equals(this.getValue());
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, byteValue, shortValue, intValue, longValue, floatValue, doubleValue, charValue, booleanValue);
    }

    @Override
    public String toString() {
        return "PrimitiveVarDetails{" +
                "ID=" + getID() +
                ", type=" + (type == null ? "null" : type.getSimpleName()) +
                ", byteValue=" + byteValue +
                ", shortValue=" + shortValue +
                ", intValue=" + intValue +
                ", longValue=" + longValue +
                ", floatValue=" + floatValue +
                ", doubleValue=" + doubleValue +
                ", charValue=" + charValue +
                ", booleanValue=" + booleanValue +
                '}';
    }

    @Override
    public String toDetailedString() {
        return this.toString();
    }
}
