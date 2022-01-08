package program.execution;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import entity.LOG_ITEM;
import helper.Properties;
import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import program.execution.variable.*;

import java.lang.reflect.Array;
import java.text.DecimalFormat;
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

    public List<Integer> getDefExeList(Integer varID) {
        return this.varToDefMap.getOrDefault(varID, new ArrayList<>());
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
     * @param process
     * @return ID of the VarDetail storing the provided object
     */
    public int getVarDetailID(Class<?> type, Object objValue, LOG_ITEM process) {
        if(objValue == null)
            objValue = "null";

        if(type.isEnum()) {
            objValue = ((Enum)objValue).name();
        }
        else if (type.equals(java.text.DecimalFormat.class) ||ClassUtils.getAllInterfaces(type).contains(DecimalFormat.class)){
            objValue = ToStringBuilder.reflectionToString(objValue);
        }
        else if(!type.isArray() && !type.equals(String.class) && !ClassUtils.isPrimitiveOrWrapper(type) && !(objValue instanceof Map) && !(objValue instanceof List)&& !(objValue instanceof Set)&&!(objValue instanceof Map.Entry)) {
            objValue = gson.toJson(objValue);
        }
        int varID = findExistingVarDetailID(type, objValue, process);
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
            else if (type.equals(String.class))
                varDetail = new StringVarDetails(getNewVarID(), (String) objValue);
            else if (ArrVarDetails.availableTypeCheck(type)) {
                Stream<Integer> componentStream;
                if(type.isArray()) {
                    Object finalObjValue = objValue;

                    componentStream = IntStream.range(0, Array.getLength(objValue)).mapToObj(i -> getVarDetailID(finalObjValue.getClass().getComponentType(), Array.get(finalObjValue, i), process));
                }
                else
                    componentStream = ((Collection<?>)objValue).stream().map(v -> getVarDetailID(getClassOfObj(v), v, process));
                if(ClassUtils.getAllInterfaces(type).contains(Set.class))
                    componentStream = componentStream.sorted();
                varDetail = new ArrVarDetails(getNewVarID(), componentStream.collect(Collectors.toList()), objValue);
            }
            else if(ClassUtils.getAllInterfaces(type).contains(Map.class)){
                Map<?,?> finalObjValue = (Map<?,?>)objValue;
                Map<Integer, Integer> components = new HashMap<>();
                finalObjValue.forEach((key, value) -> components.put(getVarDetailID(getClassOfObj(key), key, process), getVarDetailID(getClassOfObj(value), value, process)));
                varDetail = new MapVarDetails(getNewVarID(), type, components, objValue);
            }
            else if (ClassUtils.isPrimitiveWrapper(type)) {
                varDetail = new WrapperVarDetails(getNewVarID(), type, objValue);
            }
            else if(type.isEnum()) {
                varDetail = new EnumVarDetails(getNewVarID(), type, (String) objValue);
            }
            else {
                // other cases
                varDetail = new ObjVarDetails(getNewVarID(), type, (String)objValue);
            }
            assert varDetail != null; // if null, then fail to generate test
//            if(process.equals(LOG_ITEM.RETURN_THIS) && )
            if(varDetail instanceof ObjVarDetails && ((process.equals(LOG_ITEM.RETURN_THIS) && ExecutionLogger.getLatestExecution().getCalleeId()==varDetail.getID() )|| process.equals(LOG_ITEM.CALL_THIS) || process.equals(LOG_ITEM.CALL_PARAM)))
                addVarDetailUsage(varDetail, ExecutionLogger.getLatestExecution().getID(), process);
            else addNewVarDetail(varDetail, ExecutionLogger.getLatestExecution().getID(), process);

            varID = varDetail.getID();
        }
        else {
            varDetail = this.getVarDetailByID(varID);
            if(varDetail instanceof ObjVarDetails && (this.varToDefMap.get(varID)==null || this.varToDefMap.get(varID).size() == 0) && (process.equals(LOG_ITEM.RETURN_ITEM) || (process.equals(LOG_ITEM.RETURN_THIS) && ExecutionLogger.getLatestExecution().getCalleeId()!=varID)))
                addNewVarDetail(varDetail, ExecutionLogger.getLatestExecution().getID(), process);
            else addVarDetailUsage(varDetail, ExecutionLogger.getLatestExecution().getID(), process);
        }
        return varID;
    }

    /**
     * If the obj was defined and stored before, return ID of the corresponding ObjVarDetails for reuse. Else return -1
     * @param objValue
     * @param process
     * @return ID of ObjVarDetails if the obj was defined and stored before, -1 if not
     */
    private int findExistingVarDetailID(Class<?> type, Object objValue, LOG_ITEM process) {
        if(objValue instanceof String && objValue.equals("null")) {
            return nullVar.getID();
        }
        if(type.isArray()) {
            Object finalObjValue = objValue;
            objValue = IntStream.range(0, Array.getLength(objValue)).mapToObj(i -> Array.get(finalObjValue, i)).map(comp -> String.valueOf(getVarDetailID(finalObjValue.getClass().getComponentType(), comp, process))).collect(Collectors.joining(Properties.getDELIMITER()));
        }
        if(ClassUtils.getAllInterfaces(type).contains(List.class) && objValue instanceof List) {
            objValue = ((List<?>) objValue).stream().map(comp -> String.valueOf(getVarDetailID(getClassOfObj(comp), comp, process))).collect(Collectors.joining(Properties.getDELIMITER()));
        }
        if(ClassUtils.getAllInterfaces(type).contains(Set.class) && objValue instanceof Set) {
            objValue = ((Set<?>) objValue).stream().map(comp -> String.valueOf(getVarDetailID(getClassOfObj(comp), comp, process))).sorted().collect(Collectors.joining(Properties.getDELIMITER()));
        }
        if(ClassUtils.getAllInterfaces(type).contains(Map.class) && objValue instanceof Map) {
            objValue = ((Map<?,?>) objValue).entrySet().stream().map(comp -> getVarDetailID(getClassOfObj(comp.getKey()), comp.getKey(), process) +"=" + getVarDetailID(getClassOfObj(comp.getValue()), comp.getValue(), process) ).sorted().collect(Collectors.joining(Properties.getDELIMITER()));
        }
        if(type.isEnum())
            objValue = type.getSimpleName()+ "." + objValue;
        Object finalObjValue1 = objValue;
        List<VarDetail> results = this.allVars.values().stream().filter(Objects::nonNull).filter(v -> v.getType().equals(type) && v.getValue().equals(finalObjValue1)).collect(Collectors.toList());
        if(results.size() == 0 ) return -1;
        else return results.get(0).getID();
    }
    private Class<?> getClassOfObj(Object obj) {
        if(obj != null) return obj.getClass();
        else return Object.class;
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
     * @param process
     */
    public void addNewVarDetail(VarDetail detail, int executionID, LOG_ITEM process) {
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
     * @param detail ID of existing VarDetail
     * @param executionID ID of a method execution
     * @param process
     */
    public void addVarDetailUsage(VarDetail detail, int executionID, LOG_ITEM process) {
        if(!this.allVars.containsKey(detail.getID()))
            this.allVars.put(detail.getID(), detail);
        this.setUpVarMaps(detail.getID());
        this.setUpCallMaps(executionID);
        this.varToUsageMap.get(detail.getID()).add(executionID);
        this.callToVarUsageMap.get(executionID).add(detail.getID());
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

    public Set<Integer> getAllChildern(int father) {
        Set<Integer> results = new HashSet<>();
        this.callGraph.outgoingEdgesOf(father).forEach(e -> {
            results.add(this.callGraph.getEdgeTarget(e));
            results.addAll(getAllChildern(this.callGraph.getEdgeTarget(e)));
        });
        return results;
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

    public VarDetail getNullVar() {
        return nullVar;
    }
}
