package program.instrumentation;

import program.analysis.ClassDetails;
import program.analysis.MethodDetails;
import soot.SootClass;
import soot.SootField;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class InstrumentResult {
    private static final InstrumentResult singleton = new InstrumentResult();
    private final Map<Integer, MethodDetails> methodDetailsMap = new ConcurrentHashMap<Integer, MethodDetails>();
    private final Map<String, ClassDetails> classDetailsMap = new HashMap<>();
    private final Map<String, Integer> libMethSignToMethIDMap = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> classPublicFieldsMap = new HashMap<>();
    private final Map<String, Integer> fieldAccessToMethIDMap = new ConcurrentHashMap<>();

    public static InstrumentResult getSingleton() {
        return singleton;
    }

    public void addMethod(MethodDetails details) {
        this.methodDetailsMap.put(details.getId(), details);
    }

    public void addLibMethod(MethodDetails details) {
        this.addMethod(details);
        this.libMethSignToMethIDMap.put(details.getSignature(), details.getId());
    }

    public void addFieldAccessMethod(MethodDetails details) {
        this.addMethod(details);
        this.fieldAccessToMethIDMap.put(getFieldAccessMapKey(details.getDeclaringClass().getName(), details.getName()), details.getId());
    }
    public Map<Integer, MethodDetails> getMethodDetailsMap() {
        return methodDetailsMap;
    }

    public MethodDetails getMethodDetailByID(int methodID) {
        if(!this.methodDetailsMap.containsKey(methodID))
            throw new IllegalArgumentException("MethodDetails for " + methodID + " does not exist");
        else return this.getMethodDetailsMap().get(methodID);
    }

    public Map<String, ClassDetails> getClassDetailsMap() {
        return classDetailsMap;
    }

    public ClassDetails getClassDetailsByID(String className) {
        if(!this.classDetailsMap.containsKey(className))
            throw new IllegalArgumentException("ClassDetails for " + className + " does not exist");
        else return this.classDetailsMap.get(className);
    }

    public void addClassDetails(ClassDetails classDetails) {
        if(!this.classDetailsMap.containsKey(classDetails.getClassFullName()))
            this.classDetailsMap.put(classDetails.getClassFullName(), classDetails);
    }

    public void clearClassDetails() {
        this.classDetailsMap.clear();
    }

    /**
     * Not using bidirectional map / additional map as this method is only expected to be used during instrumentation for java lib method finding
     * @param methodSignature Signature of method
     * @return MethodDetails if the details already exist, else return null
     */
    public MethodDetails findExistingLibMethod(String methodSignature) {
        if(this.libMethSignToMethIDMap.containsKey(methodSignature))
            return this.getMethodDetailByID(this.libMethSignToMethIDMap.get(methodSignature));
        else return null;
    }

    public MethodDetails findExistingFieldAccessMethod(String declaringClass, String fieldName ) {
        String key = getFieldAccessMapKey(declaringClass, fieldName);
        if(this.fieldAccessToMethIDMap.containsKey(key))
            return this.getMethodDetailByID(this.fieldAccessToMethIDMap.get(key));
        else return null;
    }

    public boolean isLibMethod(Integer methodID) {
        return this.libMethSignToMethIDMap.containsValue(methodID);
    }

    public Map<String, Set<String>> getClassPublicFieldsMap() {
        return classPublicFieldsMap;
    }

    public void addClassPublicFields(String className, SootClass sootClass) {
        if(this.getClassPublicFieldsMap().containsKey(className)) return;
        this.getClassPublicFieldsMap().put(className, sootClass.getFields().stream()
                        .filter(f -> f.isPublic() && f.isStatic())
                        .map(SootField::getName)
                        .collect(Collectors.toSet())
                );
    }

    private String getFieldAccessMapKey(String declaringClass, String fieldName) {
        return declaringClass+"_"+fieldName;
    }

    public Map<String, Integer> getFieldAccessToMethIDMap() {
        return fieldAccessToMethIDMap;
    }
}
