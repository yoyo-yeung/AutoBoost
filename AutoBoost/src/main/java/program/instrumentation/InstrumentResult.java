package program.instrumentation;

import program.analysis.ClassAnalysis;
import program.analysis.MethodDetails;
import soot.SootMethod;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class InstrumentResult {
    private static InstrumentResult singleton = new InstrumentResult();
    private Map<Integer, MethodDetails> methodDetailsMap = Collections.synchronizedMap(new HashMap<>());
    private AtomicInteger methodIdGenerator = new AtomicInteger(0);
    private ClassAnalysis classAnalysis = new ClassAnalysis();
//    private List<>

    public static InstrumentResult getSingleton() {
        return singleton;
    }


    public int getNewMethodId() {
        return methodIdGenerator.incrementAndGet();
    }

    public void addMethod(MethodDetails details) {
        this.methodDetailsMap.put(details.getId(), details);
    }
    public void addMethod(SootMethod method) {

         addMethod(new MethodDetails(method));
    }

    public ClassAnalysis getClassAnalysis() {
        return classAnalysis;
    }

    public Map<Integer, MethodDetails> getMethodDetailsMap() {
        return methodDetailsMap;
    }
    public boolean visitedMethod(String declaringClass, String subsignature) {
        return this.methodDetailsMap.values().stream().anyMatch(m -> m.getDeclaringClass().equals(declaringClass)&& m.getSubsignature().equals(subsignature));
    }
}
