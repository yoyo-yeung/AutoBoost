package program.instrumentation;

import program.analysis.ClassDetails;
import program.analysis.MethodDetails;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class InstrumentResult {
    private static final InstrumentResult singleton = new InstrumentResult();
    private final Map<Integer, MethodDetails> methodDetailsMap = new ConcurrentHashMap<>();
    private final Map<String, ClassDetails> classDetailsMap = new HashMap<>();
    private final Map<String, Integer> libMethSignToMethIDMap = new ConcurrentHashMap<>();

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

    public boolean isLibMethod(Integer methodID) {
        return this.libMethSignToMethIDMap.containsValue(methodID);
    }
}
