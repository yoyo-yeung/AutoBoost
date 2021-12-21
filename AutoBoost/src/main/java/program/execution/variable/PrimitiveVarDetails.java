package program.execution.variable;

import org.apache.commons.lang3.ClassUtils;

import java.lang.reflect.Field;
public class PrimitiveVarDetails extends VarDetail {
    Class<?> type = null;
    byte byteValue;
    short shortValue;
    int intValue;
    long longValue;
    float floatValue;
    double doubleValue;
    char charValue;
    boolean booleanValue;

    // must provide all details when constructed, no updating allowed
    public PrimitiveVarDetails(Object wrapperValue) throws NoSuchFieldException, IllegalAccessException {
        this(wrapperValue.getClass(), wrapperValue);
    }
    public PrimitiveVarDetails(Class<?>type, Object wrappedValue) throws NoSuchFieldException, IllegalAccessException {
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

    public PrimitiveVarDetails(String type, Object wrappedValue) throws ClassNotFoundException, NoSuchFieldException, IllegalAccessException {
        this(Class.forName(type), wrappedValue);
    }

    @Override
    public Class<?> getType() {
        return type;
    }

    @Override
    public String getTypeSimpleName() {return type.getSimpleName();}

    @Override
    public Object getValue() throws Exception {
        Field field = getClass().getDeclaredField(type.getName() + "Value");
        return field.get(this).toString();

    }

}
