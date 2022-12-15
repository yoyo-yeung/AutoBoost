package helper.xml;

import entity.LOG_ITEM;
import helper.Helper;
import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import program.execution.ExecutionTrace;
import program.execution.MethodExecution;
import program.execution.variable.ArrVarDetails;
import program.execution.variable.MapVarDetails;
import program.execution.variable.VarDetail;
import program.instrumentation.InstrumentResult;

import javax.xml.stream.*;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class XMLParser {
    private static final Logger logger = LogManager.getLogger(XMLParser.class);
    private final XMLOutputFactory xmlOutputFactory = XMLOutputFactory.newInstance();
    private static final XMLInputFactory factory = XMLInputFactory.newInstance();
    private static Map<Integer, Map<Map.Entry<String, String>, VarDetail> > contentMapCache = new HashMap<>();

    public static Map<Map.Entry<String, String>, VarDetail> fromXMLtoContentMap(VarDetail currentVar, String xml) {
        if(contentMapCache.containsKey(currentVar.getID()))
            return contentMapCache.get(currentVar.getID());

        Map<Map.Entry<String, String>, VarDetail> fieldToVarMap = new HashMap<>();
        String fieldName = "";
        String className = "";
        VarDetail varDetail = null;
        boolean varDetailIDFound = true;
        ExecutionTrace.IntermediateVarContent intermediateVarContent = new ExecutionTrace.IntermediateVarContent();
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
                                break;
                            case VARDETAIL_CLASS:
                                varDetailIDFound = false;
                                intermediateVarContent.setVarDetailClass((Class<? extends VarDetail>) ClassUtils.getClass(attribute.getValue()));
                                break;
                            case VARDETAIL_TYPE:
                                intermediateVarContent.setVarType(ClassUtils.getClass(attribute.getValue()));
                                break;
                            case VARDETAIL_VAL:
                                intermediateVarContent.setVarValue(StringEscapeUtils.unescapeXml(attribute.getValue()));
                                break;
                            case VARDETAIL_VALTYPE:
                                intermediateVarContent.setValueStoredType(ClassUtils.getClass(attribute.getValue()));
                                break;
                        }

                    }
                    if(!varDetailIDFound) {
                        if(ClassUtils.isPrimitiveWrapper(intermediateVarContent.getValueStoredType())) {
                            try {
                                if(!intermediateVarContent.getValueStoredType().equals(Character.class))
                                    intermediateVarContent.setVarValue(intermediateVarContent.getValueStoredType().getConstructor(String.class).newInstance(intermediateVarContent.getVarValue()));
                                else                             intermediateVarContent.setVarValue(intermediateVarContent.getValueStoredType().getConstructor(char.class).newInstance(((String)intermediateVarContent.getVarValue()).length() == 0 ? Character.MIN_VALUE : ((String)intermediateVarContent.getVarValue()).charAt(0)));

                            } catch (InstantiationException | NoSuchMethodException | IllegalAccessException |
                                     InvocationTargetException e) {
                                throw new RuntimeException(e);
                            }
                        }

                        intermediateVarContent.setVarValue(intermediateVarContent.getValueStoredType().cast(intermediateVarContent.getVarValue()));
                        intermediateVarContent.setVarCheckVal(intermediateVarContent.getVarValue());
                        varDetail = ExecutionTrace.getSingleton().getVarDetail(null, intermediateVarContent, System.identityHashCode(intermediateVarContent.getVarValue()), null, true, null);
                        intermediateVarContent.clear();
                        varDetailIDFound = true;
                    }
                    idToVarMap.put(fieldID, varDetail);
                    fieldToVarMap.put(new AbstractMap.SimpleEntry<>(className, fieldName), varDetail);

                }
            }
        } catch (XMLStreamException | ClassNotFoundException e) {
            logger.error(e.getMessage() + "\t" + className + "\t" + fieldName);
            throw new RuntimeException(e);
        }
        contentMapCache.put(currentVar.getID(), fieldToVarMap);
        return fieldToVarMap;
    }
    public static List<Integer> fromXMLtoVarDetailIDList(VarDetail currentVar, String xml) {
        List<Integer> varDetailIDList = new ArrayList<>();
        String fieldName = "";
        String className = "";
        VarDetail varDetail = null;
        boolean varDetailIDFound = true;
        ExecutionTrace.IntermediateVarContent intermediateVarContent = new ExecutionTrace.IntermediateVarContent();
        int processedFieldID = -1;
        try {
            XMLEventReader eventReader =
                    factory.createXMLEventReader(new StringReader(xml));
            Map<Integer, VarDetail> idToVarMap = new HashMap<>();
            idToVarMap.put(-1, currentVar);
            int elementID = 0;
            while (eventReader.hasNext()) {
                XMLEvent event = eventReader.nextEvent();
                if (event.getEventType() == XMLStreamConstants.START_ELEMENT) {
                    StartElement startElement = event.asStartElement();
                    XML_ELEMENT qName = XML_ELEMENT.valueOf(startElement.getName().getLocalPart());
                    Iterator<Attribute> attributes = startElement.getAttributes();
                    if (!qName.equals(XML_ELEMENT.ELEMENT)) continue;
                    while (attributes.hasNext()) {
                        Attribute attribute = attributes.next();
                        XML_ATTRIBUTE aName = XML_ATTRIBUTE.valueOf(attribute.getName().getLocalPart());
                        switch (aName) {
                            case ELEMENT_ID:
                                elementID = Integer.parseInt(attribute.getValue());
                                break;
                            case VAR_ID:
                                varDetail = ExecutionTrace.getSingleton().getVarDetailByID(Integer.parseInt(attribute.getValue()));
                                break;
                            case PROCESSED_FIELD_ID:
                                processedFieldID = Integer.parseInt(attribute.getValue());
                                varDetail = idToVarMap.get(processedFieldID);
                                break;
                            case VARDETAIL_CLASS:
                                varDetailIDFound = false;
                                intermediateVarContent.setVarDetailClass((Class<? extends VarDetail>) ClassUtils.getClass(attribute.getValue()));
                                break;
                            case VARDETAIL_TYPE:
                                intermediateVarContent.setVarType(ClassUtils.getClass(attribute.getValue()));
                                break;
                            case VARDETAIL_VAL:
                                intermediateVarContent.setVarValue(StringEscapeUtils.unescapeXml(attribute.getValue()));
                                break;
                            case VARDETAIL_VALTYPE:
                                intermediateVarContent.setValueStoredType(ClassUtils.getClass(attribute.getValue()));
                                break;
                        }

                    }
                    if(!varDetailIDFound) {
                        if(ClassUtils.isPrimitiveWrapper(intermediateVarContent.getValueStoredType())) {
                            try {
                                if(!intermediateVarContent.getValueStoredType().equals(Character.class))
                                    intermediateVarContent.setVarValue(intermediateVarContent.getValueStoredType().getConstructor(String.class).newInstance(intermediateVarContent.getVarValue()));
                                else                             intermediateVarContent.setVarValue(intermediateVarContent.getValueStoredType().getConstructor(char.class).newInstance(((String)intermediateVarContent.getVarValue()).length() == 0 ? Character.MIN_VALUE : ((String)intermediateVarContent.getVarValue()).charAt(0)));

                            } catch (InstantiationException | NoSuchMethodException | IllegalAccessException |
                                     InvocationTargetException e) {
                                throw new RuntimeException(e);
                            }
                        }

                        intermediateVarContent.setVarValue(intermediateVarContent.getValueStoredType().cast(intermediateVarContent.getVarValue()));
                        intermediateVarContent.setVarCheckVal(intermediateVarContent.getVarValue());
                        varDetail = ExecutionTrace.getSingleton().getVarDetail(null, intermediateVarContent, System.identityHashCode(intermediateVarContent.getVarValue()), null, true, null);
                        intermediateVarContent.clear();
                        varDetailIDFound = true;
                    }
                    idToVarMap.put(elementID, varDetail);
                    varDetailIDList.add(varDetail.getID());

                }
            }
        } catch (XMLStreamException | ClassNotFoundException e) {
            logger.error(e.getMessage() + "\t" + className + "\t" + fieldName);
            throw new RuntimeException(e);
        }
        return varDetailIDList;
    }

    public static void clearCache() {
        contentMapCache.clear();
    }

    public static Map<Integer, Map<Map.Entry<String, String>, VarDetail>> getContentMapCache() {
        return contentMapCache;
    }

    public String getXML(MethodExecution execution, Object obj, LOG_ITEM process, int depth, Map<Integer, Integer> processedHashToVarIDMap) {
        String xmlString;
        try {
            AtomicInteger fieldIDGenerator = new AtomicInteger(-1);
            StringWriter stringWriter = new StringWriter();
            XMLStreamWriter xmlStreamWriter = xmlOutputFactory.createXMLStreamWriter(stringWriter);
            xmlStreamWriter.writeStartDocument();
            PROCESSING_OBJ_TYPE processingObjType;
            if(ArrVarDetails.availableTypeCheck(obj.getClass()))
                processingObjType = PROCESSING_OBJ_TYPE.ARR;
            else
                processingObjType = PROCESSING_OBJ_TYPE.OBJ;
            switch(processingObjType) {
                case ARR:
                    xmlStreamWriter.writeStartElement(XML_ELEMENT.ARR.name());
                    break;
                default:
                    xmlStreamWriter.writeStartElement(XML_ELEMENT.OBJECT.name());
            }
            xmlStreamWriter.writeAttribute(XML_ATTRIBUTE.CLASS.name(), obj.getClass().getName());
            write(execution, obj, process, xmlStreamWriter, new HashMap<>(), "", depth, fieldIDGenerator, processedHashToVarIDMap, processingObjType);
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


    private void write(MethodExecution execution, Object obj, LOG_ITEM process, XMLStreamWriter xmlStreamWriter, Map<Integer, Integer> hashCodeToFieldMap, String fieldName, int depth, AtomicInteger fieldIDGenerator, Map<Integer, Integer> processedHashToVarIDMap, PROCESSING_OBJ_TYPE processingObjType) throws XMLStreamException {
        Integer objHash = System.identityHashCode(obj);
        if (obj != null && hashCodeToFieldMap.containsKey(objHash)) {
            writeProcessedFieldToXML(hashCodeToFieldMap.get(objHash), xmlStreamWriter);
            return;
        }
        hashCodeToFieldMap.put(objHash, fieldIDGenerator.get());
        if (depth <= 0) return;
        if (fieldName.isEmpty() )
            switch(processingObjType) {
                case OBJ:
                    writeObjToXML(execution, obj, process, xmlStreamWriter, hashCodeToFieldMap, fieldName, depth, fieldIDGenerator, processedHashToVarIDMap);
                    break;
                case ARR:
                    writeArrToXML(execution, obj, process, xmlStreamWriter, hashCodeToFieldMap, fieldName, depth, fieldIDGenerator, processedHashToVarIDMap);
                    break;
            }
        else
            writeFieldToXML(execution, obj, process, xmlStreamWriter, depth, processedHashToVarIDMap);
    }

    private void writeFieldToXML(MethodExecution execution, Object obj, LOG_ITEM process, XMLStreamWriter xmlStreamWriter, int depth, Map<Integer, Integer> processedHashToVarIDMap) throws XMLStreamException {
        Object toWrite = ExecutionTrace.getSingleton().getContentForXMLStorage(execution, obj == null ? Object.class : obj.getClass(), obj, process, true, new HashSet<>(), depth - 1, processedHashToVarIDMap);
        if(toWrite instanceof VarDetail)
            writeVarIDToXML(((VarDetail) toWrite).getID(), xmlStreamWriter);
        else if(toWrite instanceof ExecutionTrace.IntermediateVarContent)
            writeVarContentToXML((ExecutionTrace.IntermediateVarContent) toWrite, xmlStreamWriter);

    }

    private void writeVarContentToXML(ExecutionTrace.IntermediateVarContent varContent, XMLStreamWriter xmlStreamWriter) throws XMLStreamException {
        xmlStreamWriter.writeAttribute(XML_ATTRIBUTE.VARDETAIL_CLASS.name(), varContent.getVarDetailClass().getName());
        xmlStreamWriter.writeAttribute(XML_ATTRIBUTE.VARDETAIL_TYPE.name(), varContent.getVarType().getName());
        xmlStreamWriter.writeAttribute(XML_ATTRIBUTE.VARDETAIL_VAL.name(), StringEscapeUtils.escapeXml10(varContent.getVarValue().toString()));
        xmlStreamWriter.writeAttribute(XML_ATTRIBUTE.VARDETAIL_VALTYPE.name(), varContent.getValueStoredType().getName());
    }
    private void writeVarIDToXML(int objID, XMLStreamWriter xmlStreamWriter) throws XMLStreamException {
        xmlStreamWriter.writeAttribute(XML_ATTRIBUTE.VAR_ID.name(), Integer.toString(objID));
    }

    private void writeObjToXML(MethodExecution execution, Object obj, LOG_ITEM process, XMLStreamWriter xmlStreamWriter, Map<Integer, Integer> hashCodeToFieldMap, String fieldName, int depth, AtomicInteger fieldIDGenerator, Map<Integer, Integer> processedHashToVarIDMap) throws XMLStreamException {
        if (depth == 1) return;
        InstrumentResult.getSingleton().getClassFields(obj.getClass()).forEach(f -> {
            f.setAccessible(true);
            try {
                xmlStreamWriter.writeStartElement(XML_ELEMENT.FIELD.name());
                xmlStreamWriter.writeAttribute(XML_ATTRIBUTE.FIELD_ID.name(), Integer.toString(fieldIDGenerator.incrementAndGet()));
                xmlStreamWriter.writeAttribute(XML_ATTRIBUTE.FIELD_NAME.name(), f.getName());
                xmlStreamWriter.writeAttribute(XML_ATTRIBUTE.BELONG.name(), f.getDeclaringClass().getName());
                write(execution, f.get(obj), process, xmlStreamWriter, hashCodeToFieldMap, f.getName(), depth - 1, fieldIDGenerator, processedHashToVarIDMap, PROCESSING_OBJ_TYPE.OBJ);
                xmlStreamWriter.writeEndElement();
            } catch (XMLStreamException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void writeArrToXML(MethodExecution execution, Object obj, LOG_ITEM process, XMLStreamWriter xmlStreamWriter, Map<Integer, Integer> hashCodeToFieldMap, String fieldName, int depth, AtomicInteger arrIDGenerator, Map<Integer, Integer> processedHashToVarIDMap) {
        if(depth == 1) return ;
        Stream<Object> componentStream;
        if(obj.getClass().isArray())
            componentStream = IntStream.range(0, Array.getLength(obj)).mapToObj(i ->Helper.getArrayElement(obj, i ));
        else
            componentStream = ((Collection)obj).stream();
        componentStream.forEach(c -> {
            try {
                String arrID = Integer.toString(arrIDGenerator.incrementAndGet());
                xmlStreamWriter.writeStartElement(XML_ELEMENT.ELEMENT.name());                xmlStreamWriter.writeAttribute(XML_ATTRIBUTE.ELEMENT_ID.name(), arrID);
                write(execution, c, process, xmlStreamWriter, hashCodeToFieldMap, arrID, depth - 1, arrIDGenerator, processedHashToVarIDMap, PROCESSING_OBJ_TYPE.ARR);
                xmlStreamWriter.writeEndElement();

            } catch (XMLStreamException e) {
                throw new RuntimeException(e);
            }

        });
    }
    public void writeProcessedFieldToXML(Integer fieldId, XMLStreamWriter xmlStreamWriter) throws XMLStreamException {
        xmlStreamWriter.writeAttribute(XML_ATTRIBUTE.PROCESSED_FIELD_ID.name(), String.valueOf(fieldId));
    }

    private enum PROCESSING_OBJ_TYPE {
        OBJ(), ARR(), MAP()
    }
}
