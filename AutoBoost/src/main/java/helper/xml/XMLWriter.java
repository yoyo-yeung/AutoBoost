package helper.xml;

import helper.Helper;
import org.apache.commons.lang3.ClassUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import program.execution.variable.MapVarDetails;
import program.instrumentation.InstrumentResult;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.lang.reflect.Array;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

public class XMLWriter {
    private static Logger logger = LogManager.getLogger(XMLWriter.class);
    private final int DEPTH = 7;

    public String getXML(Object obj) {
        String xmlString;
        try {
            StringWriter stringWriter = new StringWriter();
            XMLOutputFactory xmlOutputFactory = XMLOutputFactory.newInstance();
            XMLStreamWriter xmlStreamWriter = xmlOutputFactory.createXMLStreamWriter(stringWriter);
            xmlStreamWriter.writeStartDocument();
            write(obj, xmlStreamWriter, new HashMap<>(), "this", DEPTH);
            xmlStreamWriter.writeEndDocument();
            xmlStreamWriter.flush();
            xmlStreamWriter.close();
            xmlString = stringWriter.getBuffer().toString();
            stringWriter.close();
        } catch (IOException | XMLStreamException e) {
            throw new RuntimeException(e);
        }
        return xmlString;

    }

    private void write(Object obj, XMLStreamWriter xmlStreamWriter, Map<Integer, String> hashCodeToFieldMap, String fieldName, int depth) throws XMLStreamException {
        if (obj == null) {
            writeNullToXML(xmlStreamWriter);
            return;
        }
        if (ClassUtils.isPrimitiveOrWrapper(obj.getClass()) || obj.getClass().equals(String.class)) {
            writePrimitiveOrWrapperValueToXML(obj, xmlStreamWriter);
            return;
        }
        if (hashCodeToFieldMap.containsKey(System.identityHashCode(obj))) {
            writeFieldToXML(hashCodeToFieldMap.get(System.identityHashCode(obj)), xmlStreamWriter);
            return;
        }
        hashCodeToFieldMap.put(System.identityHashCode(obj), fieldName);
        if (depth <= 0) return;
        if (obj.getClass().isArray()) writeArrToXML(obj, xmlStreamWriter, hashCodeToFieldMap, fieldName, depth);
        else if (MapVarDetails.availableTypeCheck(obj.getClass()))
            writeMapToXML(obj, xmlStreamWriter, hashCodeToFieldMap, fieldName, depth);
        else if (obj instanceof Collection && obj.getClass().getName().startsWith("java."))
            writeCollectionToXML(obj, xmlStreamWriter, hashCodeToFieldMap, fieldName, depth);
        else writeObjToXML(obj, xmlStreamWriter, hashCodeToFieldMap, fieldName, depth);
    }

    private void writePrimitiveOrWrapperValueToXML(Object obj, XMLStreamWriter xmlStreamWriter) throws XMLStreamException {
        xmlStreamWriter.writeStartElement(XML_ELEMENT.VALUE.getElementName());
        xmlStreamWriter.writeAttribute(XML_ATTRIBUTE.CLASS.getAttriName(), obj.getClass().getName());
        xmlStreamWriter.writeCharacters(obj.toString());
        xmlStreamWriter.writeEndElement();
    }

    private void writeNullToXML(XMLStreamWriter xmlStreamWriter) throws XMLStreamException {
        xmlStreamWriter.writeStartElement(XML_ELEMENT.VALUE.getElementName());
        xmlStreamWriter.writeAttribute(XML_ATTRIBUTE.CLASS.getAttriName(), Object.class.getName());
        xmlStreamWriter.writeCharacters("null");
        xmlStreamWriter.writeEndElement();
    }

    private void writeArrToXML(Object obj, XMLStreamWriter xmlStreamWriter, Map<Integer, String> hashCodeToFieldMap, String fieldName, int depth) throws XMLStreamException {
        xmlStreamWriter.writeStartElement(XML_ELEMENT.ARRAY.getElementName());
        xmlStreamWriter.writeAttribute(XML_ATTRIBUTE.CLASS.getAttriName(), obj.getClass().getName());
        IntStream.range(0, Array.getLength(obj)).forEach(i -> {
            try {
                xmlStreamWriter.writeStartElement(Integer.toString(i));
                write(Helper.getArrayElement(obj, i), xmlStreamWriter, hashCodeToFieldMap, fieldName + i, depth - 1);
                xmlStreamWriter.writeEndElement();
            } catch (XMLStreamException e) {
                throw new RuntimeException(e);
            }
        });
        xmlStreamWriter.writeEndElement();
    }

    private void writeMapToXML(Object obj, XMLStreamWriter xmlStreamWriter, Map<Integer, String> hashCodeToFieldMap, String fieldName, int depth) throws XMLStreamException {
        AtomicInteger i = new AtomicInteger();
        xmlStreamWriter.writeStartElement(XML_ELEMENT.MAP.getElementName());
        ((Map<?, ?>) obj).forEach((key, value) -> {
            try {
                xmlStreamWriter.writeStartElement(String.valueOf(i.getAndIncrement()));
                xmlStreamWriter.writeStartElement(XML_ELEMENT.MAP_KEY.getElementName());
                write(key, xmlStreamWriter, hashCodeToFieldMap, fieldName + i.get() + ".key", depth - 1);

                xmlStreamWriter.writeEndElement();
                xmlStreamWriter.writeStartElement(XML_ELEMENT.MAP_VALUE.getElementName());
                write(value, xmlStreamWriter, hashCodeToFieldMap, fieldName + i.get() + ".value", depth - 1);
                xmlStreamWriter.writeEndElement();
                xmlStreamWriter.writeEndElement();
            } catch (XMLStreamException e) {
                throw new RuntimeException(e);
            }
        });
        xmlStreamWriter.writeEndElement();
    }


    private void writeCollectionToXML(Object obj, XMLStreamWriter xmlStreamWriter, Map<Integer, String> hashCodeToFieldMap, String fieldName, int depth) throws XMLStreamException {
        xmlStreamWriter.writeStartElement(XML_ELEMENT.COLLECTION.getElementName());
        AtomicInteger i = new AtomicInteger();
        ((Collection) obj).forEach(value -> {
            try {
                xmlStreamWriter.writeStartElement(String.valueOf(i.getAndIncrement()));
                write(value, xmlStreamWriter, hashCodeToFieldMap, fieldName + i.get(), depth - 1);
                xmlStreamWriter.writeEndElement();
            } catch (XMLStreamException e) {
                throw new RuntimeException(e);
            }
        });
        xmlStreamWriter.writeEndElement();
    }

    private void writeObjToXML(Object obj, XMLStreamWriter xmlStreamWriter, Map<Integer, String> hashCodeToFieldMap, String fieldName, int depth) throws XMLStreamException {
        InstrumentResult.getSingleton().getClassFields(obj.getClass()).forEach(f -> {
            f.setAccessible(true);
            try {
                xmlStreamWriter.writeStartElement( f.getName());
                xmlStreamWriter.writeAttribute(XML_ATTRIBUTE.BELONG.getAttriName(), f.getDeclaringClass().getName());
                write(f.get(obj), xmlStreamWriter, hashCodeToFieldMap, fieldName + "."+  f.getName(), depth - 1);
                xmlStreamWriter.writeEndElement();
            } catch (XMLStreamException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public void writeFieldToXML(String fieldName, XMLStreamWriter xmlStreamWriter) throws XMLStreamException {
        xmlStreamWriter.writeStartElement(XML_ELEMENT.PROCESSED_FIELD_VALUE.getElementName());
        xmlStreamWriter.writeAttribute(XML_ATTRIBUTE.PROCESSED_FIELD_NAME.getAttriName(), fieldName);
        xmlStreamWriter.writeEndElement();
    }
}
