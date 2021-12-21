package program.execution.variable;


import org.apache.commons.text.StringEscapeUtils;

import java.util.Objects;

public class StringVarDetails extends VarDetail{
    String value;

    public StringVarDetails(int ID, String value) {
        this.setID(ID);
        this.value = value;
    }

    @Override
    public Class<?> getType() {
        return String.class;
    }

    public String getTypeSimpleName() {return String.class.getSimpleName();}

    @Override
    public Object getValue() {
        return "\""+ StringEscapeUtils.escapeJava(value) + "\""; // get escaped text WITH ""
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StringVarDetails that = (StringVarDetails) o;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
}
