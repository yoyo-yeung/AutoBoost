package program.execution.variable;

import helper.Properties;

import java.lang.reflect.Field;
public class PrimitiveVarDetails extends VarDetailImpl {
    Class<?> type;
    byte byteValue;
    short shortValue;
    int intValue;
    long longValue;
    float floatValue;
    double doubleValue;
    char charValue;
    boolean booleanValue;
    public PrimitiveVarDetails() {
    }

    public PrimitiveVarDetails(Class<?> type) {
        this.type = type;
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
    public String getValue() throws IllegalAccessException, NoSuchFieldException {
        Field field = getClass().getDeclaredField(type.getName()+"Value");
        return field.get(this).toString();
    }
    public Class<?> getType() {
        return type;
    }

    @Override
    public String getStmt(String varName) throws NoSuchFieldException, IllegalAccessException {
        return this.getType().getSimpleName() + " " + varName + " = " + getValue() + ";" + Properties.getNewLine();
    }
}
