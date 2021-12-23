package program.execution;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import program.execution.variable.ObjVarDetails;
import program.execution.variable.VarDetail;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class ExecutionTrace {
    private static final Logger logger = LogManager.getLogger(ExecutionTrace.class);
    private static final ExecutionTrace singleton = new ExecutionTrace();
    private static final AtomicInteger exeIDGenerator = new AtomicInteger(0);
    private static final AtomicInteger varIDGenerator = new AtomicInteger(0);
    private final Map<Integer, MethodExecution> allMethodExecs;
    private final Map<Integer, VarDetail> allVars; // store all vardetail used, needed for lookups
    private final Map<Integer, Set<Integer>> varToUsageMap;
    private final Map<Integer, List<Integer>> varToDefMap;
    private final Map<Integer, Set<Integer>> callToVarUsageMap;
    private final Map<Integer, Set<Integer>> callToVarDefMap;
    private final DefaultDirectedGraph<Integer, DefaultEdge> callGraph;

    public ExecutionTrace() {
        this.allMethodExecs = new HashMap<>();
        this.allVars = new HashMap<>();
        this.varToUsageMap = new HashMap<>();
        this.varToDefMap = new HashMap<>();
        this.callToVarUsageMap = new HashMap<>();
        this.callToVarDefMap = new HashMap<>();
        callGraph = new DefaultDirectedGraph<>(DefaultEdge.class);
    }

    public static ExecutionTrace getSingleton() {
        return singleton;
    }

    public Map<Integer, MethodExecution> getAllMethodExecs() {
        return allMethodExecs;
    }

    public Map<Integer, VarDetail> getAllVars() {
        return allVars;
    }

    public Map<Integer, Set<Integer>> getVarToUsageMap() {
        return varToUsageMap;
    }

    public Map<Integer, List<Integer>> getVarToDefMap() {
        return varToDefMap;
    }

    public Map<Integer, Set<Integer>> getCallToVarUsageMap() {
        return callToVarUsageMap;
    }

    public Map<Integer, Set<Integer>> getCallToVarDefMap() {
        return callToVarDefMap;
    }

    public DefaultDirectedGraph<Integer, DefaultEdge> getCallGraph() {
        return callGraph;
    }

    public int getNewExeID() {
        return exeIDGenerator.incrementAndGet();
    }

    public int getNewVarID() {
        return varIDGenerator.incrementAndGet();
    }

    /**
     * If the obj was defined and stored before, return ID of the corresponding ObjVarDetails for reuse. Else return -1
     * @param obj
     * @return ID of ObjVarDetails if the obj was defined and stored before, -1 if not
     */
    public int getObjVarDetailsID(Object obj) {
        List<VarDetail> results = this.allVars.values().stream().filter(v -> v instanceof ObjVarDetails).filter(v -> Objects.deepEquals(v.getValue(), obj)).collect(Collectors.toList());
        if(results.size() == 0 ) return -1;
        else return results.get(0).getID();
    }

    public void addVarDetail(VarDetail detail) {
        this.allVars.put(detail.getID(), detail);
    }

    public void addMethodExecution(MethodExecution execution) {
        this.allMethodExecs.put(execution.getID(), execution);
        this.callGraph.addVertex(execution.getID()); // add vertex even if it has no son/ father
    }

    public void addMethodRelationship(int father, int son) {
        this.callGraph.addVertex(father);
        this.callGraph.addVertex(son);
        this.callGraph.addEdge(father, son);
    }
}
