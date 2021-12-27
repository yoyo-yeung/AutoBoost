package program.execution;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.commons.lang3.ClassUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import program.execution.variable.*;

import java.lang.reflect.Array;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class ExecutionTrace {
    private static final Logger logger = LogManager.getLogger(ExecutionTrace.class);
    private static final ExecutionTrace singleton = new ExecutionTrace();
    private static final AtomicInteger exeIDGenerator = new AtomicInteger(0);
    private static final AtomicInteger varIDGenerator = new AtomicInteger(1);
    private final Map<Integer, MethodExecution> allMethodExecs;
    private final Map<Integer, VarDetail> allVars; // store all vardetail used, needed for lookups
    private final Map<Integer, List<Integer>> varToUsageMap;
    private final Map<Integer, List<Integer>> varToDefMap;
    private final Map<Integer, Set<Integer>> callToVarUsageMap;
    private final Map<Integer, Set<Integer>> callToVarDefMap;
    private final DefaultDirectedGraph<Integer, DefaultEdge> callGraph;
    private final VarDetail nullVar;

    private final Gson gson = new GsonBuilder().addSerializationExclusionStrategy(new ExclusionStrategy() {
        @Override
        public boolean shouldSkipField(FieldAttributes fieldAttributes) {
            if(Arrays.stream(fieldAttributes.getDeclaringClass().getDeclaredFields()).filter(field -> fieldAttributes.getName().equals(field.getName())).count()>1)
                return true;
            return false;
        }
        @Override
        public boolean shouldSkipClass(Class<?> aClass) {
            return aClass.equals(java.text.DecimalFormat.class);
        }
    }).create();
    public ExecutionTrace() {
        this.allMethodExecs = new HashMap<>();
        this.allVars = new HashMap<>();
        this.varToUsageMap = new HashMap<>();
        this.varToDefMap = new HashMap<Integer, List<Integer>>();
        this.callToVarUsageMap = new HashMap<>();
        this.callToVarDefMap = new HashMap<>();
        callGraph = new DefaultDirectedGraph<>(DefaultEdge.class);
        nullVar = new ObjVarDetails(0, Object.class, "null");
        this.allVars.put(nullVar.getID(), nullVar);
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
     * If object already stored, return existing VarDetail ID stored
     * If not, create a new VarDetail and return the corresponding ID
     * @param type type of the object to be stored
     * @param objValue object to be stored
     * @return ID of the VarDetail storing the provided object
     */
    public int getVarDetailID(Class<?> type, Object objValue) {
        if(objValue == null)
            objValue = "null";
        if(!type.isArray() && !type.equals(String.class) && !ClassUtils.isPrimitiveOrWrapper(type) && !(objValue instanceof List))
            objValue = gson.toJson(objValue);
        int varID = findExistingVarDetailID(type, objValue);
        VarDetail varDetail;
        if(varID == -1) {
            if(type.isPrimitive()) {
                try {
                    varDetail = new PrimitiveVarDetails(getNewVarID(), type, objValue);
                } catch (NoSuchFieldException | IllegalAccessException e) {
                    logger.error("Error when trying to create PrimitiveVarDetails");
                    varDetail = null;
                }
            }
            else if (type.equals(String.class)) {
                varDetail = new StringVarDetails(getNewVarID(), (String) objValue);
            }
            else if (type.isArray() || ClassUtils.getAllInterfaces(type).contains(List.class)) {
                List<Integer> components;
                Stream<Integer> componentStream;
                if(type.isArray()) {
                    Object finalObjValue = objValue;
                    componentStream = IntStream.range(0, Array.getLength(objValue)).mapToObj(i -> getVarDetailID(finalObjValue.getClass().getComponentType(), Array.get(finalObjValue, i)));
                }
                else {
                    componentStream = ((List<?>)objValue).stream().map(v -> getVarDetailID(v == null? Object.class: v.getClass(), v));
                }
                varDetail = new ArrVarDetails(getNewVarID(), componentStream.collect(Collectors.toList()), objValue);
            }

            else if (ClassUtils.isPrimitiveWrapper(type)) {
                varDetail = new WrapperVarDetails(getNewVarID(), type, objValue);
            }
            else {
                // other cases
                varDetail = new ObjVarDetails(getNewVarID(), type, (String) objValue);
            }
            assert varDetail != null; // if null, then fail to generate test
            addNewVarDetail(varDetail, ExecutionLogger.getLatestExecution().getID());
            varID = varDetail.getID();
        }
        else {
            addVarDetailUsage(varID, ExecutionLogger.getLatestExecution().getID());
        }
        return varID;
    }

    /**
     * If the obj was defined and stored before, return ID of the corresponding ObjVarDetails for reuse. Else return -1
     * @param objValue
     * @return ID of ObjVarDetails if the obj was defined and stored before, -1 if not
     */
    private int findExistingVarDetailID(Class<?> type, Object objValue) {
        if(objValue.equals("null"))
            return nullVar.getID();
        if(type.isArray()) {
            Object finalObjValue = objValue;
            objValue = IntStream.range(0, Array.getLength(objValue)).mapToObj(i -> Array.get(finalObjValue, i)).map(comp -> String.valueOf(getVarDetailID(finalObjValue.getClass().getComponentType(), comp))).collect(Collectors.joining(","));
        }
        if(ClassUtils.getAllInterfaces(type).contains(List.class) && objValue instanceof List) {
            List<?> finalObjValue = (List<?>) objValue;
            objValue = finalObjValue.stream().map(comp -> String.valueOf(getVarDetailID(comp == null ? Object.class : comp.getClass(), comp))).collect(Collectors.joining(","));
        }
        Object finalObjValue1 = objValue;
        List<VarDetail> results = this.allVars.values().stream().filter(Objects::nonNull).filter(v -> v.getType().equals(type) && v.getValue().equals(finalObjValue1)).collect(Collectors.toList());
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
        // only store it as a def if it is an object (need construction)
        if(detail instanceof ObjVarDetails)
            this.varToDefMap.get(detail.getID()).add(executionID);
        this.varToUsageMap.get(detail.getID()).add(executionID);
        this.callToVarDefMap.get(executionID).add(detail.getID());
        this.callToVarUsageMap.get(executionID).add(detail.getID());
    }

    /**
     * Add record of a MethodExecution (with ID executionID) using a particular VarDetail (with ID detailID)
     * @param detailID ID of existing VarDetail
     * @param executionID ID of a method execution
     */
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

    public VarDetail getVarDetailByID(int varID) {
        if(!this.allVars.containsKey(varID))
            throw new IllegalArgumentException("VarDetail with ID" + varID + " does not exist.");
        return this.allVars.get(varID);
    }

    public MethodExecution getMethodExecutionByID(int exeID) {
        if(!this.allMethodExecs.containsKey(exeID))
            throw new IllegalArgumentException("MethodExecution with ID " + exeID + " does not exist");
        return this.allMethodExecs.get(exeID);
    }
}
