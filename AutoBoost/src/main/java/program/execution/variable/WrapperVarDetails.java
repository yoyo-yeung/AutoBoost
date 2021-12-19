package program.execution.variable;

import helper.Properties;
import org.apache.commons.cli.MissingArgumentException;

// store it separately from other object var as they can be directly assigned
public class WrapperVarDetails extends VarDetailImpl{
    Class<?> type;
    Object value;

    public WrapperVarDetails() {
    }

    public WrapperVarDetails(Class<?> type) {
        this.type = type;
    }

    public WrapperVarDetails(String typeName) throws ClassNotFoundException {
        this(Class.forName(typeName));
    }

    public WrapperVarDetails(Class<?> type, Object value) {
        this.type = type;
        this.value = value;
    }
    public WrapperVarDetails(String typeName, Object value) throws ClassNotFoundException {
        this(Class.forName(typeName), value);
    }

    public Class<?> getType() {
        return type;
    }

    public String getTypeSimpleName() {
        return this.type.getSimpleName();
    }

    public void setType(Class<?> type) {
        this.type = type;
    }

    public void setType(String typeName) throws ClassNotFoundException {
        this.setType(Class.forName(typeName));
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

}
