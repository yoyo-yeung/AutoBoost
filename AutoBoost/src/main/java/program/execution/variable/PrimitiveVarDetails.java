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

    public PrimitiveVarDetails(int ID, String type, Object wrappedValue) throws ClassNotFoundException, NoSuchFieldException, IllegalAccessException {
        this(ID, Class.forName(type), wrappedValue);
    }

    @Override
    public Class<?> getType() {
        return type;
    }

    @Override
    public String getTypeSimpleName() {return type.getSimpleName();}

    @Override
    public Object getValue()  {
        Field field = null;
        try {
            field = getClass().getDeclaredField(type.getName() + "Value");
            return ClassUtils.primitiveToWrapper(this.type).cast(field.get(this));
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
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
        return byteValue == that.byteValue && shortValue == that.shortValue && intValue == that.intValue && longValue == that.longValue && Float.compare(that.floatValue, floatValue) == 0 && Double.compare(that.doubleValue, doubleValue) == 0 && charValue == that.charValue && booleanValue == that.booleanValue && Objects.equals(type, that.type);
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
