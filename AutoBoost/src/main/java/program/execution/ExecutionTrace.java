package program.execution;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
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
    private final Map<Integer, List<Integer>> varToUsageMap;
    private final Map<Integer, List<Integer>> varToDefMap;
    private final Map<Integer, Set<Integer>> callToVarUsageMap;
    private final Map<Integer, Set<Integer>> callToVarDefMap;
    private final DefaultDirectedGraph<Integer, DefaultEdge> callGraph;

    public ExecutionTrace() {
        this.allMethodExecs = new HashMap<>();
        this.allVars = new HashMap<>();
        this.varToUsageMap = new HashMap<>();
        this.varToDefMap = new HashMap<Integer, List<Integer>>();
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

    public Map<Integer, List<Integer>> getVarToUsageMap() {
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
     * @param objValue
     * @return ID of ObjVarDetails if the obj was defined and stored before, -1 if not
     */
    public int getObjVarDetailsID(String objValue) {
        List<VarDetail> results = this.allVars.values().stream().filter(v ->v.getValue().equals(objValue)).collect(Collectors.toList());
        if(results.size() == 0 ) return -1;
        else return results.get(0).getID();
    }

    /**
     * Set up all maps using VarDetail key as ID
     * @param varID key of VarDetail
     */
    private void setUpVarMaps(int varID) {
        if(!this.varToDefMap.containsKey(varID) || this.varToDefMap.get(varID) == null)
            this.varToDefMap.put(varID, new ArrayList<>());
        if(!this.varToUsageMap.containsKey(varID) || this.varToUsageMap.get(varID) == null )
            this.varToUsageMap.put(varID, new ArrayList<>());
    }

    /**
     * Set up all maps using MethodExecution key as ID
     * @param executionID key of Method Execution
     */
    private void setUpCallMaps(int executionID) {
        if(!this.callToVarDefMap.containsKey(executionID) || this.callToVarDefMap.get(executionID) == null)
            this.callToVarDefMap.put(executionID, new HashSet<>());
        if(!this.callToVarUsageMap.containsKey(executionID) || this.callToVarUsageMap.get(executionID) == null)
            this.callToVarUsageMap.put(executionID, new HashSet<>());
    }

    /**
     * Add a newly created VarDetail to the collection
     * Add def-relationship between method execution and VarDetail
     * @param detail Newly created VarDetail to document
     * @param executionID ID of MethodExecution that define a new VarDetail
     */
    public void addNewVarDetail(VarDetail detail, int executionID) {
        this.allVars.put(detail.getID(), detail);
        this.setUpVarMaps(detail.getID());
        this.setUpCallMaps(executionID);
        this.varToDefMap.get(detail.getID()).add(executionID);
        this.varToUsageMap.get(detail.getID()).add(executionID);
        this.callToVarDefMap.get(executionID).add(detail.getID());
        this.callToVarUsageMap.get(executionID).add(detail.getID());
    }

    public void addVarDetailUsage(int detailID, int executionID) {
        this.setUpVarMaps(detailID);
        this.setUpCallMaps(executionID);
        this.varToUsageMap.get(detailID).add(executionID);
        this.callToVarUsageMap.get(executionID).add(detailID);
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
