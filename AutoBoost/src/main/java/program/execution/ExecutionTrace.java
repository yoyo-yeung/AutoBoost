package program.execution;

import entity.ACCESS;
import entity.LOG_ITEM;
import entity.METHOD_TYPE;
import helper.Properties;
import org.apache.commons.lang3.ClassUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import program.analysis.MethodDetails;
import program.execution.variable.*;
import program.instrumentation.InstrumentResult;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
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
    private final Map<Integer, Integer> varToDefMap;
    private final DefaultDirectedGraph<Integer, DefaultEdge> callGraph;
    private final VarDetail nullVar;


    public ExecutionTrace() {
        this.allMethodExecs = new HashMap<>();
        this.allVars = new HashMap<>();
        this.varToDefMap = new HashMap<Integer, Integer>();
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


    public Map<Integer, Integer> getVarToDefMap() {
        return varToDefMap;
    }

    public Integer getDefExeList(Integer varID) {
        return this.varToDefMap.getOrDefault(varID, null);
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
        boolean artificalEnum = false;
        if(type.isEnum()) {
            objValue = ((Enum)objValue).name();
        }
        else if(!type.isArray() && !type.equals(String.class) && !ClassUtils.isPrimitiveOrWrapper(type) && !(objValue instanceof Map) && !(objValue instanceof List)&& !(objValue instanceof Set)&&!(objValue instanceof Map.Entry)) {
            Object finalObjValue1 = objValue;
            List<String> matches = Arrays.stream(type.getDeclaredFields()).filter(f -> Modifier.isPublic(f.getModifiers()) && Modifier.isStatic(f.getModifiers()) && !Modifier.isTransient(f.getModifiers()) && f.isAccessible()).filter(f -> {
                try {
                    Object val = f.get(finalObjValue1);
                    return val != null && val.equals(finalObjValue1);
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                    return false;
                }
            }).map(Field::getName).collect(Collectors.toList());

            if(matches.size() > 0) {
                artificalEnum = true;
                objValue = matches.get(0);
            }
            else if(type.equals(Class.class)) {
                artificalEnum = true;
                type = (Class<?>) objValue;
                objValue = "class";
            }
            else objValue = toStringWithAttr(objValue);
        }
        if(ClassUtils.isPrimitiveOrWrapper(type)) {
            if((type.equals(double.class) || type.equals(Double.class))) {
                if(Double.isNaN(((Double) objValue))) {
                    artificalEnum = true;
                    type = Double.class;
                    objValue = "NaN";
                }
                else if(Double.POSITIVE_INFINITY == ((Double)objValue)) {
                    artificalEnum = true;
                    type = Double.class;
                    objValue = "POSITIVE_INFINITY";
                }
                else if(Double.NEGATIVE_INFINITY == ((Double)objValue)) {
                    artificalEnum = true;
                    type = Double.class;
                    objValue = "NEGATIVE_INFINITY";
                }
            }
            if(type.equals(Integer.class) || type.equals(int.class)){
                if(Integer.MAX_VALUE == ((Integer)objValue)) {
                    artificalEnum = true;
                    type = Integer.class;
                    objValue = "MAX_VALUE";
                }
                else if(Integer.MIN_VALUE == ((Integer)objValue)) {
                    artificalEnum = true;
                    type = Integer.class;
                    objValue = "MIN_VALUE";
                }
            }
        }
        int varID = objValue == null ? nullVar.getID() : findExistingVarDetailID(type, objValue, process);
        VarDetail varDetail;
        MethodExecution latestExecution = ExecutionLogger.getLatestExecution();
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
                varDetail = new ArrVarDetails(getNewVarID(), getComponentStream(type, objValue, process).collect(Collectors.toList()), objValue);
            }
            else if(ClassUtils.getAllInterfaces(type).contains(Map.class)){
                Map<?,?> finalObjValue = (Map<?,?>)objValue;
                Map<Integer, Integer> components = new HashMap<>();
                finalObjValue.forEach((key, value) -> components.put(getVarDetailID(getClassOfObj(key), key, process), getVarDetailID(getClassOfObj(value), value, process)));
                varDetail = new MapVarDetails(getNewVarID(), type, components, objValue);
            }
            else if(type.isEnum() || artificalEnum) {
                varDetail = new EnumVarDetails(getNewVarID(), type, (String) objValue);
            }
            else if (ClassUtils.isPrimitiveWrapper(type)) {
                varDetail = new WrapperVarDetails(getNewVarID(), type, objValue);
            }
            else {
                // other cases
                varDetail = new ObjVarDetails(getNewVarID(), type, (String)objValue);
            }
            assert varDetail != null; // if null, then fail to generate test
            if(setFirstOccurrenceAsUse(varDetail, process, latestExecution))
                addVarDetailUsage(varDetail, latestExecution.getID());
            else addNewVarDetail(varDetail, latestExecution.getID());

            varID = varDetail.getID();
        }
        else {
            varDetail = this.getVarDetailByID(varID);
//                if (varDetail instanceof ObjVarDetails && process.equals(LOG_ITEM.RETURN_THIS)  && result.getMethodDetailByID(latestExecution.getMethodInvokedId()).getType().equals(METHOD_TYPE.CONSTRUCTOR) && latestExecution.getParams().stream().noneMatch(p -> p == finalVarID) && (existingDef==null || (!existingDef.getType().equals(METHOD_TYPE.CONSTRUCTOR) && currentMethd.getType().equals(METHOD_TYPE.CONSTRUCTOR)) ||( !existingDef.getAccess().equals(ACCESS.PUBLIC)) && currentMethd.getAccess().equals(ACCESS.PUBLIC)))
            if(setOccurrenceAsDef(varDetail, process, latestExecution))
            {
                addNewVarDetail(varDetail, latestExecution.getID());
            }
            else {
                addVarDetailUsage(varDetail, latestExecution.getID());
            }
        }
        return varID;
    }

    private boolean setFirstOccurrenceAsUse(VarDetail varDetail, LOG_ITEM process, MethodExecution execution) {
        if(!(varDetail instanceof ObjVarDetails))
            return true;
        MethodDetails details = InstrumentResult.getSingleton().getMethodDetailByID(execution.getMethodInvokedId());
        if(details.getAccess().equals(ACCESS.PRIVATE) || (details.getAccess().equals(ACCESS.PROTECTED) && !details.getDeclaringClass().getPackageName().equals(Properties.getSingleton().getGeneratedPackage())))
            return true;
        switch(process) {
            case CALL_THIS:
            case CALL_PARAM:
                return true;
            case RETURN_THIS:
            case RETURN_ITEM:
                return (execution.getCalleeId() == varDetail.getID() || execution.getParams().contains(varDetail.getID()));
            default:
                return false;
        }
    }

    private boolean setOccurrenceAsDef(VarDetail varDetail, LOG_ITEM process, MethodExecution execution) {
        if(!(varDetail instanceof ObjVarDetails))
            return false;
        if(!process.equals(LOG_ITEM.RETURN_THIS) && !process.equals(LOG_ITEM.RETURN_ITEM)) return false;
        MethodDetails details = InstrumentResult.getSingleton().getMethodDetailByID(execution.getMethodInvokedId());
        MethodExecution existingDefEx = getDefExeList(varDetail.getID()) == null ? null : getMethodExecutionByID(getDefExeList(varDetail.getID()));
        MethodDetails existingDef = existingDefEx == null ? null : InstrumentResult.getSingleton().getMethodDetailByID(existingDefEx.getMethodInvokedId());
        if(details.getAccess().equals(ACCESS.PRIVATE) || (details.getAccess().equals(ACCESS.PROTECTED) && !details.getDeclaringClass().getPackageName().equals(Properties.getSingleton().getGeneratedPackage())))
            return false;
        if(execution.getCalleeId() == varDetail.getID() || execution.getParams().contains(varDetail.getID()))
            return false;
        if(existingDef == null) return true;
        else if(existingDef.getId() == details.getId()) return false;

        int existingNullDefCount = getNullDefCount(existingDefEx);
        int currentNullDefCount = getNullDefCount(execution);
        if(existingNullDefCount > currentNullDefCount) return true;
        else if(existingNullDefCount == currentNullDefCount) return ((existingDef.getParameterCount() + (existingDef.getType().equals(METHOD_TYPE.CONSTRUCTOR) || existingDef.getType().equals(METHOD_TYPE.STATIC)? 0: 1)) > (details.getParameterCount() + (details.getType().equals(METHOD_TYPE.STATIC) || details.getType().equals(METHOD_TYPE.CONSTRUCTOR) ? 0 : 1)));
        return false;
    }

    private int getNullDefCount(MethodExecution execution) {
        return (int) execution.getParams().stream().map(this::getVarDetailByID).filter(p ->( (p instanceof ObjVarDetails) && getDefExeList(p.getID()) == null )).count() + ((execution.getCalleeId() == -1 || getDefExeList(execution.getCalleeId()) != null) ? 0: 1);
    }
    private Stream<Integer> getComponentStream(Class<?> type, Object obj, LOG_ITEM process) {
        if(!ArrVarDetails.availableTypeCheck(type))
            throw new IllegalArgumentException("Provided Obj cannot be handled.");
        Stream<Integer> componentStream;
        if(type.isArray()){
            componentStream = IntStream.range(0, Array.getLength(obj)).mapToObj(i -> getVarDetailID( getClassOfObj( Array.get(obj, i)), Array.get(obj, i), process));
        }
        else
            componentStream = ((Collection) obj).stream().map(v -> getVarDetailID(getClassOfObj(v), v, process));
        if(ClassUtils.getAllInterfaces(type).contains(Set.class))
            componentStream = componentStream.sorted();
        return componentStream;
    }

    /**
     * If the obj was defined and stored before, return ID of the corresponding ObjVarDetails for reuse. Else return -1
     * @param objValue
     * @param process
     * @return ID of ObjVarDetails if the obj was defined and stored before, -1 if not
     */
    private int findExistingVarDetailID(Class<?> type, Object objValue, LOG_ITEM process) {
        if(ArrVarDetails.availableTypeCheck(type)) {
            objValue = getComponentStream(type, objValue, process).map(String::valueOf).collect(Collectors.joining(Properties.getDELIMITER()));
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
    private void setUpVarMap(int varID) {
        if(!this.varToDefMap.containsKey(varID))
            this.varToDefMap.put(varID, null);
    }


    /**
     * Add a newly created VarDetail to the collection
     * Add def-relationship between method execution and VarDetail
     * @param detail Newly created VarDetail to document
     * @param executionID ID of MethodExecution that define a new VarDetail
     */
    public void addNewVarDetail(VarDetail detail, int executionID) {
        this.allVars.put(detail.getID(), detail);
        this.setUpVarMap(detail.getID());
        // only store it as a def if it is an object (need construction)
        if(detail instanceof ObjVarDetails)
            this.varToDefMap.put(detail.getID(), executionID);
    }

    /**
     * Add record of a MethodExecution (with ID executionID) using a particular VarDetail (with ID detailID)
     * @param detail ID of existing VarDetail
     * @param executionID ID of a method execution
     */
    public void addVarDetailUsage(VarDetail detail, int executionID) {
        if(!this.allVars.containsKey(detail.getID()))
            this.allVars.put(detail.getID(), detail);
        this.setUpVarMap(detail.getID());
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
        Stack<MethodExecution> executing = ExecutionLogger.getExecuting();
        if(!this.allMethodExecs.containsKey(exeID) && executing.stream().noneMatch(e -> e.getID()==exeID))
            throw new IllegalArgumentException("MethodExecution with ID " + exeID + " does not exist");
        else if (!this.allMethodExecs.containsKey(exeID)) {
            return executing.stream().filter(e -> e.getID()==exeID).findFirst().get();
        }
        return this.allMethodExecs.get(exeID);
    }

    public VarDetail getNullVar() {
        return nullVar;
    }

    private String toStringWithAttr(Object obj) {
        Stack<MethodExecution> executing = ExecutionLogger.getExecuting();
        int existing = executing.size();
        if (obj == null) return "null";
        String val =  baseFieldsToString(obj, 4);
//        logger.debug(val);
        return val;
    }
    private String baseFieldsToString(Object obj, int depth) {
        if(obj==null ) return "null";
        else if(obj.getClass().isArray()) return "["+IntStream.range(0, Array.getLength(obj)).mapToObj(i -> Array.get(obj, i) == null ? "null" : baseFieldsToString(Array.get(obj, i), depth-1)).collect(Collectors.joining(",")) + "]";
        else if (obj instanceof Map) return "{"+((Map<?,?>)obj).entrySet().stream().map(e -> "{"+baseFieldsToString(e.getKey(), depth -1)+"="+baseFieldsToString(e.getValue(), depth-1)+"}").collect(Collectors.joining(","))+ "}";
        else if (obj instanceof Collection) return "{"+String.valueOf(((Collection) obj).stream().map(i -> baseFieldsToString(i, depth-1)).collect(Collectors.joining(",")))+ "}";
        else if(ClassUtils.isPrimitiveOrWrapper(obj.getClass()) || obj.getClass().getName().startsWith("java")) return String.valueOf(obj);
        else if (depth==0) return obj.getClass().getName() + "[]";
        List<Class> classesToGetFields = new ArrayList<>();
        classesToGetFields.add(obj.getClass());
        classesToGetFields.addAll(ClassUtils.getAllSuperclasses(obj.getClass()));
        classesToGetFields.removeIf(c -> c.equals(Object.class));
        return "{"+classesToGetFields.stream().filter(c -> !c.getName().startsWith("java") && !ClassUtils.isPrimitiveOrWrapper(c))
                .map(c -> {
            return c.getName() + "[" + Arrays.stream(c.getDeclaredFields()).filter(f -> !(Modifier.isStatic(f.getModifiers()) && Modifier.isFinal(f.getModifiers()))).map(f -> {
                try {
                    f.setAccessible(true);
                    return f.getName()+"=" + baseFieldsToString(f.get(obj), depth -1);
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                    return null;
                }
            }).filter(Objects::nonNull).map(Object::toString).collect(Collectors.joining(",")) +"]";
        }).collect(Collectors.joining(",")) +"}";
    }
}
