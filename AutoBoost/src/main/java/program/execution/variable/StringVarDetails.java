package program.execution.variable;


import entity.CREATION_TYPE;
import org.apache.commons.text.StringEscapeUtils;

import java.util.Objects;

public class StringVarDetails extends VarDetail{
    private static final CREATION_TYPE createdBy = CREATION_TYPE.DIRECT_ASSIGN;
    private final String value;

    public StringVarDetails(int ID, String value) {
        super(ID);
        this.value = value;
    }

    public CREATION_TYPE getCreatedBy() {
        return createdBy;
    }

    @Override
    public Class<?> getType() {
        return String.class;
    }

    public String getTypeSimpleName() {return String.class.getSimpleName();}

    @Override
    public Object getGenValue() {
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

    @Override
    public String toString() {
        return "StringVarDetails{" +
                "ID=" + getID() +
                ", value='" + (value == null ? "null" : value) + '\'' +
                '}';
    }

    @Override
    public String toDetailedString() {
        return this.toString();
    }
}
