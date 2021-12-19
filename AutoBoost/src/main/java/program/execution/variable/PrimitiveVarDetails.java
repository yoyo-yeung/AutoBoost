package program.execution.variable;

import org.apache.commons.cli.MissingArgumentException;

import java.lang.reflect.Field;
public class PrimitiveVarDetails extends VarDetailImpl {
    Class<?> type = null;
    byte byteValue;
    short shortValue;
    int intValue;
    long longValue;
    float floatValue;
    double doubleValue;
    char charValue;
    boolean booleanValue;
    public PrimitiveVarDetails(){
    }

    public PrimitiveVarDetails(Class<?> type) {
        this.type = type;
    }

    public PrimitiveVarDetails(String typeName) throws ClassNotFoundException {
        this(Class.forName(typeName));
    }

    public PrimitiveVarDetails(Class<?>type, Object wrappedValue) throws NoSuchFieldException, IllegalAccessException {
        if(!type.isPrimitive())
            throw new IllegalArgumentException("Non primitive type value are being stored as primitive var");
        this.type = type;
        Field field = getClass().getDeclaredField(type.getName()+"Value");
        field.set(this, wrappedValue);
    }

    public PrimitiveVarDetails(String type, Object wrappedValue) throws ClassNotFoundException, NoSuchFieldException, IllegalAccessException {
        this(Class.forName(type), wrappedValue);
    }
    public Class<?> getType() {
        return type;
    }

    public String getTypeSimpleName() {return type.getSimpleName();}

    public void setType(Class<?> type) {
        if(!type.isPrimitive())
            throw new IllegalArgumentException("Primitive type expected");
        this.type = type;
    }

    public void setType(String typeName) throws ClassNotFoundException {
        setType(Class.forName(typeName));
    }

    public void setValue(Object wrappedValue) throws NoSuchFieldException, IllegalAccessException {
        if(this.type == null)
            throw new IllegalArgumentException("Type should be set before assigning value");
        Field field = getClass().getDeclaredField(type.getName()+"Value");
        field.set(this, wrappedValue);
    }

    public String getValue() throws IllegalAccessException, NoSuchFieldException {
        Field field = getClass().getDeclaredField(type.getName()+"Value");
        return field.get(this).toString();
    }

}
