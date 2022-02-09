package program.instrumentation;

import program.analysis.ClassDetails;
import program.analysis.MethodDetails;

import java.util.*;

public class InstrumentResult {
    private static final InstrumentResult singleton = new InstrumentResult();
    private final Map<Integer, MethodDetails> methodDetailsMap = new HashMap<>();
    private final Map<String, ClassDetails> classDetailsMap = new HashMap<>();

    public static InstrumentResult getSingleton() {
        return singleton;
    }

    public void addMethod(MethodDetails details) {
        this.methodDetailsMap.put(details.getId(), details);
    }

    public Map<Integer, MethodDetails> getMethodDetailsMap() {
        return methodDetailsMap;
    }

    public MethodDetails getMethodDetailByID(Integer methodID) {
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
}
