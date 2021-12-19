package program.execution.variable;


import org.apache.commons.text.StringEscapeUtils;

public class StringVarDetails extends VarDetailImpl{
    String value;

    public StringVarDetails() {
    }

    public StringVarDetails(String value) {
        this.value = value;
    }

    public String getValue() {
        return "\""+ StringEscapeUtils.escapeJava(value) + "\""; // get escaped text WITH ""
    }

    public void setValue(String value) {
        this.value = value;
    }

}
