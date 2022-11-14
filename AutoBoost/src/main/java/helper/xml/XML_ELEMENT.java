package helper.xml;

public enum XML_ELEMENT {
    VALUE("value"), ARRAY("array"), MAP("map"), COLLECTION("collection"), MAP_KEY("key"), MAP_VALUE("value"), PROCESSED_FIELD_VALUE("processed_field"), START_OF_CLASS("class_fields");
    private final String elementName;

    XML_ELEMENT(String elementName) {
        this.elementName = elementName;
    }

    public String getElementName() {
        return elementName;
    }
}
