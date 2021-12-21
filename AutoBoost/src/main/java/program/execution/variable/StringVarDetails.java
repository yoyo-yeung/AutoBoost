package program.execution.variable;


import org.apache.commons.text.StringEscapeUtils;

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

}
