package helper.xml;

public enum XML_ATTRIBUTE {
    CLASS("class"), PROCESSED_FIELD_NAME("field"), BELONG("belong");
    private final String attriName;

    XML_ATTRIBUTE(String attrName) {
        this.attriName = attrName;
    }

    public String getAttriName() {
        return attriName;
    }
}
