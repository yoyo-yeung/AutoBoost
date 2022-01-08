package program.instrumentation;

import program.analysis.MethodDetails;
import soot.SootMethod;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class InstrumentResult {
    private static InstrumentResult singleton = new InstrumentResult();
    private final Map<Integer, MethodDetails> methodDetailsMap = Collections.synchronizedMap(new HashMap<>());

    public static InstrumentResult getSingleton() {
        return singleton;
    }

    public void addMethod(MethodDetails details) {
        this.methodDetailsMap.put(details.getId(), details);
    }
    
    public void addMethod(SootMethod method) {
         addMethod(new MethodDetails(method));
    }

    public Map<Integer, MethodDetails> getMethodDetailsMap() {
        return methodDetailsMap;
    }

    public MethodDetails getMethodDetailByID(int methodID) {
        if(!this.methodDetailsMap.containsKey(methodID))
            throw new IllegalArgumentException("MethodDetails for " + methodID + " does not exist");
        else return this.getMethodDetailsMap().get(methodID);
    }
}
