package helper.xml;

import entity.LOG_ITEM;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import program.execution.ExecutionTrace;
import program.execution.MethodExecution;
import program.execution.variable.VarDetail;
import program.instrumentation.InstrumentResult;

import javax.xml.stream.*;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class XMLParser {
    private static final Logger logger = LogManager.getLogger(XMLParser.class);
    private final XMLOutputFactory xmlOutputFactory = XMLOutputFactory.newInstance();
    private static final XMLInputFactory factory = XMLInputFactory.newInstance();

    public static Map<Map.Entry<String, String>, VarDetail> fromXMLtoContentMap(VarDetail currentVar, String xml) {
        Map<Map.Entry<String, String>, VarDetail> fieldToVarMap = new HashMap<>();
        String fieldName = "";
        String className = "";
        VarDetail varDetail = null;
        int processedFieldID = -1;
        try {
            XMLEventReader eventReader =
                    factory.createXMLEventReader(new StringReader(xml));
            Map<Integer, VarDetail> idToVarMap = new HashMap<>();
            idToVarMap.put(-1, currentVar);
            int fieldID = 0;
            while (eventReader.hasNext()) {
                XMLEvent event = eventReader.nextEvent();
                if (event.getEventType() == XMLStreamConstants.START_ELEMENT) {
                    StartElement startElement = event.asStartElement();
                    XML_ELEMENT qName = XML_ELEMENT.valueOf(startElement.getName().getLocalPart());
                    Iterator<Attribute> attributes = startElement.getAttributes();
                    if (!qName.equals(XML_ELEMENT.FIELD)) continue;
                    while (attributes.hasNext()) {
                        Attribute attribute = attributes.next();
                        XML_ATTRIBUTE aName = XML_ATTRIBUTE.valueOf(attribute.getName().getLocalPart());
                        switch (aName) {
                            case FIELD_ID:
                                fieldID = Integer.parseInt(attribute.getValue());
                                break;
                            case FIELD_NAME:
                                fieldName = attribute.getValue();
                                break;
                            case BELONG:
                                className = attribute.getValue();
                                break;
                            case VAR_ID:
                                varDetail = ExecutionTrace.getSingleton().getVarDetailByID(Integer.parseInt(attribute.getValue()));
                                break;
                            case PROCESSED_FIELD_ID:
                                processedFieldID = Integer.parseInt(attribute.getValue());
                                varDetail = idToVarMap.get(processedFieldID);
                        }

                    }
                    idToVarMap.put(fieldID, varDetail);
                    fieldToVarMap.put(new AbstractMap.SimpleEntry<>(className, fieldName), varDetail);

                }
            }
        } catch (XMLStreamException e) {
            logger.error(e.getMessage() + "\t" + className + "\t" + fieldName);
            throw new RuntimeException(e);
        }
        return fieldToVarMap;
    }

    public String getXML(MethodExecution execution, Object obj, LOG_ITEM process, int depth, Map<Integer, Integer> processedHashToVarIDMap) {
        String xmlString;
        try {
            AtomicInteger fieldIDGenerator = new AtomicInteger(-1);
            StringWriter stringWriter = new StringWriter();
            XMLStreamWriter xmlStreamWriter = xmlOutputFactory.createXMLStreamWriter(stringWriter);
            xmlStreamWriter.writeStartDocument();
            xmlStreamWriter.writeStartElement(XML_ELEMENT.OBJECT.name());
            xmlStreamWriter.writeAttribute(XML_ATTRIBUTE.CLASS.name(), obj.getClass().getName());
            write(execution, obj, process, xmlStreamWriter, new HashMap<>(), "", depth, fieldIDGenerator, processedHashToVarIDMap);
            xmlStreamWriter.writeEndElement();
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

    private void write(MethodExecution execution, Object obj, LOG_ITEM process, XMLStreamWriter xmlStreamWriter, Map<Integer, String> hashCodeToFieldMap, String fieldName, int depth, AtomicInteger fieldIDGenerator, Map<Integer, Integer> processedHashToVarIDMap) throws XMLStreamException {
        if (obj != null && hashCodeToFieldMap.containsKey(System.identityHashCode(obj))) {
            writeProcessedFieldToXML(hashCodeToFieldMap.get(System.identityHashCode(obj)), xmlStreamWriter);
            return;
        }
        hashCodeToFieldMap.put(System.identityHashCode(obj), Integer.toString(fieldIDGenerator.get()));
        if (depth <= 0) return;
        if (fieldName.isEmpty())
            writeObjToXML(execution, obj, process, xmlStreamWriter, hashCodeToFieldMap, fieldName, depth, fieldIDGenerator, processedHashToVarIDMap);
        else
            writeVarIDToXML(execution, obj, process, xmlStreamWriter, depth, processedHashToVarIDMap);
    }

    private void writeVarIDToXML(MethodExecution execution, Object obj, LOG_ITEM process, XMLStreamWriter xmlStreamWriter, int depth, Map<Integer, Integer> processedHashToVarIDMap) throws XMLStreamException {
        xmlStreamWriter.writeAttribute(XML_ATTRIBUTE.VAR_ID.name(), Integer.toString(ExecutionTrace.getSingleton().getVarDetail(execution, obj == null ? Object.class : obj.getClass(), obj, process, true, new HashSet<>(), depth - 1, processedHashToVarIDMap).getID()));
    }

    private void writeObjToXML(MethodExecution execution, Object obj, LOG_ITEM process, XMLStreamWriter xmlStreamWriter, Map<Integer, String> hashCodeToFieldMap, String fieldName, int depth, AtomicInteger fieldIDGenerator, Map<Integer, Integer> processedHashToVarIDMap) throws XMLStreamException {
        if (depth == 1) return;
        InstrumentResult.getSingleton().getClassFields(obj.getClass()).forEach(f -> {
            f.setAccessible(true);
            try {
                xmlStreamWriter.writeStartElement(XML_ELEMENT.FIELD.name());
                xmlStreamWriter.writeAttribute(XML_ATTRIBUTE.FIELD_ID.name(), Integer.toString(fieldIDGenerator.incrementAndGet()));
                xmlStreamWriter.writeAttribute(XML_ATTRIBUTE.FIELD_NAME.name(), f.getName());
                xmlStreamWriter.writeAttribute(XML_ATTRIBUTE.BELONG.name(), f.getDeclaringClass().getName());
                write(execution, f.get(obj), process, xmlStreamWriter, hashCodeToFieldMap, f.getName(), depth - 1, fieldIDGenerator, processedHashToVarIDMap);
                xmlStreamWriter.writeEndElement();
            } catch (XMLStreamException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public void writeProcessedFieldToXML(String fieldId, XMLStreamWriter xmlStreamWriter) throws XMLStreamException {
        xmlStreamWriter.writeAttribute(XML_ATTRIBUTE.PROCESSED_FIELD_ID.name(), fieldId);
    }
}
