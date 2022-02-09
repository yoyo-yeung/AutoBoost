package program.execution;

import entity.ACCESS;
import entity.LOG_ITEM;
import entity.METHOD_TYPE;
import helper.Properties;
import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import program.analysis.ClassDetails;
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
     *
     * @param type     type of the object to be stored
     * @param objValue object to be stored
     * @param process
     * @return ID of the VarDetail storing the provided object
     */
    public int getVarDetailID(Class<?> type, Object objValue, LOG_ITEM process) {
        boolean artificialEnum = false;
        if (type.isEnum()) {
            objValue = ((Enum) objValue).name();
        } else if (!type.isArray() && !type.equals(String.class) && !ClassUtils.isPrimitiveOrWrapper(type) && !(objValue instanceof Map) && !(objValue instanceof List) && !(objValue instanceof Set) && !(objValue instanceof Map.Entry)) {
            Object finalObjValue1 = objValue;
            Optional<String> match = Arrays.stream(type.getDeclaredFields()).filter(f -> Modifier.isPublic(f.getModifiers()) && Modifier.isStatic(f.getModifiers()) && !Modifier.isTransient(f.getModifiers()) && f.isAccessible()).filter(f -> {
                try {
                    Object val = f.get(finalObjValue1);
                    return val != null && val.equals(finalObjValue1);
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                    return false;
                }
            }).map(Field::getName).findAny();

            if (match.isPresent()) {
                artificialEnum = true;
                objValue = match.get();
            } else if (type.equals(Class.class)) {
                artificialEnum = true;
                type = (Class<?>) objValue;
                objValue = "class";
            } else objValue = toStringWithAttr(objValue);
        }
        if (ClassUtils.isPrimitiveOrWrapper(type)) {
            if ((type.equals(double.class) || type.equals(Double.class))) {
                if (Double.isNaN(((Double) objValue))) {
                    artificialEnum = true;
                    type = Double.class;
                    objValue = "NaN";
                } else if (Double.POSITIVE_INFINITY == ((Double) objValue)) {
                    artificialEnum = true;
                    type = Double.class;
                    objValue = "POSITIVE_INFINITY";
                } else if (Double.NEGATIVE_INFINITY == ((Double) objValue)) {
                    artificialEnum = true;
                    type = Double.class;
                    objValue = "NEGATIVE_INFINITY";
                }
            }
            if (type.equals(Integer.class) || type.equals(int.class)) {
                if (Integer.MAX_VALUE == ((Integer) objValue)) {
                    artificialEnum = true;
                    type = Integer.class;
                    objValue = "MAX_VALUE";
                } else if (Integer.MIN_VALUE == ((Integer) objValue)) {
                    artificialEnum = true;
                    type = Integer.class;
                    objValue = "MIN_VALUE";
                }
            }
        }
        int varID = objValue == null ? nullVar.getID() : findExistingVarDetailID(type, objValue, process, artificialEnum);
        VarDetail varDetail;
        MethodExecution latestExecution = ExecutionLogger.getLatestExecution();
        if (varID == -1) {
            if (type.isPrimitive()) {
                try {
                    varDetail = new PrimitiveVarDetails(getNewVarID(), type, objValue);
                } catch (NoSuchFieldException | IllegalAccessException e) {
                    logger.error("Error when trying to create PrimitiveVarDetails");
                    varDetail = null;
                }
            } else if (type.equals(String.class))
                varDetail = new StringVarDetails(getNewVarID(), (String) objValue);
            else if (ArrVarDetails.availableTypeCheck(type)) {
                varDetail = new ArrVarDetails(getNewVarID(), getComponentStream(type, objValue, process).collect(Collectors.toList()), objValue);
            } else if (Map.class.isAssignableFrom(type)) {
                varDetail = new MapVarDetails(getNewVarID(), type, ((Map<?, ?>) objValue).entrySet().stream().collect(Collectors.toMap(e -> getVarDetailID(getClassOfObj(e.getKey()), e.getKey(), process), e -> getVarDetailID(getClassOfObj(e.getValue()), e.getValue(), process))), objValue);
            } else if (type.isEnum() || artificialEnum) {
                varDetail = new EnumVarDetails(getNewVarID(), type, (String) objValue);
            } else if (ClassUtils.isPrimitiveWrapper(type)) {
                varDetail = new WrapperVarDetails(getNewVarID(), type, objValue);
            } else {
                // other cases
                varDetail = new ObjVarDetails(getNewVarID(), type, (String) objValue);
            }
            assert varDetail != null; // if null, then fail to generate test
            if (setFirstOccurrenceAsUse(varDetail, process, latestExecution))
                addVarDetailUsage(varDetail, latestExecution.getID());
            else addNewVarDetail(varDetail, latestExecution.getID());

            varID = varDetail.getID();
        } else {
            varDetail = this.getVarDetailByID(varID);
            if (setOccurrenceAsDef(varDetail, process, latestExecution)) {
                addNewVarDetail(varDetail, latestExecution.getID());
            } else {
                addVarDetailUsage(varDetail, latestExecution.getID());
            }
        }
        return varID;
    }

    private boolean setFirstOccurrenceAsUse(VarDetail varDetail, LOG_ITEM process, MethodExecution execution) {
        if (!(varDetail instanceof ObjVarDetails) || execution.getTest() == null)
            return true;
        MethodDetails details = InstrumentResult.getSingleton().getMethodDetailByID(execution.getMethodInvokedId());
        if (details.getAccess().equals(ACCESS.PRIVATE) || (details.getAccess().equals(ACCESS.PROTECTED) && !details.getDeclaringClass().getPackageName().equals(Properties.getSingleton().getGeneratedPackage())))
            return true;
        switch (process) {
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
        if (!(varDetail instanceof ObjVarDetails) || execution.getTest() == null)
            return false;
        if (!process.equals(LOG_ITEM.RETURN_THIS) && !process.equals(LOG_ITEM.RETURN_ITEM)) return false;
        MethodDetails details = InstrumentResult.getSingleton().getMethodDetailByID(execution.getMethodInvokedId());
        MethodExecution existingDefEx = getDefExeList(varDetail.getID()) == null ? null : getMethodExecutionByID(getDefExeList(varDetail.getID()));
        MethodDetails existingDef = existingDefEx == null ? null : InstrumentResult.getSingleton().getMethodDetailByID(existingDefEx.getMethodInvokedId());
        if (details.getAccess().equals(ACCESS.PRIVATE) || (details.getAccess().equals(ACCESS.PROTECTED) && !details.getDeclaringClass().getPackageName().equals(Properties.getSingleton().getGeneratedPackage())))
            return false;
        if (execution.getCalleeId() == varDetail.getID() || execution.getParams().contains(varDetail.getID()))
            return false;
        if (existingDef == null) return true;
        else if (existingDef.getId() == details.getId()) return false;

        int existingNullDefCount = getNullDefCount(existingDefEx);
        int currentNullDefCount = getNullDefCount(execution);
        if (existingNullDefCount > currentNullDefCount) return true;
        else if (existingNullDefCount == currentNullDefCount)
            return ((existingDef.getParameterCount() + (existingDef.getType().equals(METHOD_TYPE.CONSTRUCTOR) || existingDef.getType().equals(METHOD_TYPE.STATIC) ? 0 : 1)) > (details.getParameterCount() + (details.getType().equals(METHOD_TYPE.STATIC) || details.getType().equals(METHOD_TYPE.CONSTRUCTOR) ? 0 : 1)));
        return false;
    }

    private int getNullDefCount(MethodExecution execution) {
        return (int) execution.getParams().stream().map(this::getVarDetailByID).filter(p -> ((p instanceof ObjVarDetails) && getDefExeList(p.getID()) == null)).count() + ((execution.getCalleeId() == -1 || getDefExeList(execution.getCalleeId()) != null) ? 0 : 1);
    }

    private Stream<Integer> getComponentStream(Class<?> type, Object obj, LOG_ITEM process) {
        if (!ArrVarDetails.availableTypeCheck(type))
            throw new IllegalArgumentException("Provided Obj cannot be handled.");
        Stream<Integer> componentStream;
        if (type.isArray()) {
            componentStream = IntStream.range(0, Array.getLength(obj)).mapToObj(i -> getVarDetailID(type.getComponentType(), Array.get(obj, i), process));
        } else
            componentStream = ((Collection) obj).stream().map(v -> getVarDetailID(getClassOfObj(v), v, process));
        if (Set.class.isAssignableFrom(type))
            componentStream = componentStream.sorted();
        return componentStream;
    }

    /**
     * If the obj was defined and stored before, return ID of the corresponding ObjVarDetails for reuse. Else return -1
     *
     * @param objValue
     * @param process
     * @param artificialEnum
     * @return ID of ObjVarDetails if the obj was defined and stored before, -1 if not
     */
    private int findExistingVarDetailID(Class<?> type, Object objValue, LOG_ITEM process, boolean artificialEnum) {
        Class<?> varDetailType = null;
        if (ArrVarDetails.availableTypeCheck(type)) {
            objValue = getComponentStream(type, objValue, process).map(String::valueOf).collect(Collectors.joining(Properties.getDELIMITER()));
            varDetailType = ArrVarDetails.class;
        }
        if (Map.class.isAssignableFrom(type) && objValue instanceof Map) {
            objValue = ((Map<?, ?>) objValue).entrySet().stream().map(comp -> getVarDetailID(getClassOfObj(comp.getKey()), comp.getKey(), process) + "=" + getVarDetailID(getClassOfObj(comp.getValue()), comp.getValue(), process)).sorted().collect(Collectors.joining(Properties.getDELIMITER()));
            varDetailType = MapVarDetails.class;
        }
        if (type.isEnum() || artificialEnum) {
            objValue = type.getSimpleName() + "." + objValue;
            varDetailType = EnumVarDetails.class;
        }
        if (type.equals(String.class)) objValue = "\"" + StringEscapeUtils.escapeJava((String) objValue) + "\"";
        // high chance of having the same value
        if (process.equals(LOG_ITEM.RETURN_THIS) && ExecutionLogger.getLatestExecution().getCalleeId() != -1) {
            VarDetail calleeDetails = getVarDetailByID(ExecutionLogger.getLatestExecution().getCalleeId());
            if (calleeDetails.getType().equals(type) && calleeDetails.getValue().equals(objValue))
                return calleeDetails.getID();
        }
        Object finalObjValue1 = type.equals(long.class) || type.equals(Long.class) ? objValue + "L" : objValue;
        Class<?> finalVarDetailType = varDetailType;
        Optional<VarDetail> result = this.allVars.values().stream()
                .filter(Objects::nonNull)
                .filter(v -> {
                    if (finalVarDetailType != null) return finalVarDetailType.isInstance(v);
                    else if (type.isPrimitive()) return v instanceof PrimitiveVarDetails;
                    else if (type.equals(String.class)) return v instanceof StringVarDetails;
                    else if (ClassUtils.isPrimitiveOrWrapper(type)) return v instanceof WrapperVarDetails;
                    else return v instanceof ObjVarDetails;
                })
                .filter(v -> v.getType().equals(type) && v.getValue().equals(finalObjValue1))
                .findAny();
        return result.map(VarDetail::getID).orElse(-1);
    }

    private Class<?> getClassOfObj(Object obj) {
        if (obj != null) return obj.getClass();
        else return Object.class;
    }

    /**
     * Set up all maps using VarDetail key as ID
     *
     * @param varID key of VarDetail
     */
    private void setUpVarMap(int varID) {
        if (!this.varToDefMap.containsKey(varID))
            this.varToDefMap.put(varID, null);
    }


    /**
     * Add a newly created VarDetail to the collection
     * Add def-relationship between method execution and VarDetail
     *
     * @param detail      Newly created VarDetail to document
     * @param executionID ID of MethodExecution that define a new VarDetail
     */
    public void addNewVarDetail(VarDetail detail, int executionID) {
        if (!this.allVars.containsKey(detail.getID()))
            this.allVars.put(detail.getID(), detail);
        this.setUpVarMap(detail.getID());
        // only store it as a def if it is an object (need construction)
        if (detail instanceof ObjVarDetails)
            this.varToDefMap.put(detail.getID(), executionID);
    }

    /**
     * Add record of a MethodExecution (with ID executionID) using a particular VarDetail (with ID detailID)
     *
     * @param detail      ID of existing VarDetail
     * @param executionID ID of a method execution
     */
    public void addVarDetailUsage(VarDetail detail, int executionID) {
        if (!this.allVars.containsKey(detail.getID()))
            this.allVars.put(detail.getID(), detail);
        this.setUpVarMap(detail.getID());
    }

    public void addMethodExecution(MethodExecution execution, int methodId) {
        int executionID = execution.getID();
        this.allMethodExecs.put(executionID, execution);
        this.callGraph.addVertex(executionID); // add vertex even if it has no son/ father
    }

    public void addMethodRelationship(int father, int son) {
        this.callGraph.addVertex(father);
        this.callGraph.addVertex(son);
        this.callGraph.addEdge(father, son);
    }

    public void changeVertex(int original, int now) {
        if (!this.callGraph.containsVertex(original)) return;
        this.callGraph.addVertex(now);
        this.callGraph.outgoingEdgesOf(original).forEach(e -> addMethodRelationship(now, this.callGraph.getEdgeTarget(e)));
        this.callGraph.removeVertex(original);
    }

    public void removeVertex(int vertex) {
        if (!this.callGraph.containsVertex(vertex)) return;
        this.callGraph.removeVertex(vertex);
    }

    public Set<Integer> getAllChildren(int father) {
        Set<Integer> results = new HashSet<>();
        this.callGraph.outgoingEdgesOf(father).forEach(e -> {
            results.add(this.callGraph.getEdgeTarget(e));
            results.addAll(getAllChildren(this.callGraph.getEdgeTarget(e)));
        });
        return results;
    }

    public VarDetail getVarDetailByID(Integer varID) {
        if (!this.allVars.containsKey(varID))
            throw new IllegalArgumentException("VarDetail with ID" + varID + " does not exist.");
        return this.allVars.get(varID);
    }

    public MethodExecution getMethodExecutionByID(Integer exeID) {
        if (this.allMethodExecs.containsKey(exeID)) return this.allMethodExecs.get(exeID);
        Optional<MethodExecution> result = ExecutionLogger.getExecuting().stream().filter(e -> e.getID() == exeID).findAny();
        if (result.isPresent()) return result.get();
        else throw new IllegalArgumentException("MethodExecution with ID " + exeID + " does not exist");
    }

    public VarDetail getNullVar() {
        return nullVar;
    }

    private String toStringWithAttr(Object obj) {
        if (obj == null) return "null";
        StringBuilder result = new StringBuilder();
        baseFieldsToString(obj, 25, result);
        return result.toString();
    }

    private void baseFieldsToString(Object obj, int depth, StringBuilder result) {
        if (obj == null) {
            result.append("null");
            return;
        } else if (depth == 0) {
            result.append(obj.getClass().getName()).append("[]");
            return;
        } else if (obj.getClass().isArray()) {
            result.append("[");
            IntStream.range(0, Array.getLength(obj)).forEach(i -> {
                if (Array.get(obj, i) == null) result.append("null");
                else baseFieldsToString(Array.get(obj, i), depth - 1, result);
                if (i < Array.getLength(obj) - 1) result.append(",");
            });
            result.append("]");
            return;
        } else if (obj instanceof Map) {
            result.append("{");
            ((Map<?, ?>) obj).forEach((key, value) -> {
                result.append("[");
                baseFieldsToString(key, depth - 1, result);
                result.append("=");
                baseFieldsToString(value, depth - 1, result);
                result.append("]");
            });
            result.append("}");
            return;
        } else if (obj instanceof Collection) {
            result.append("{");
            ((Collection) obj).forEach(i -> {
                baseFieldsToString(i, depth - 1, result);
                result.append(",");
            });
            result.deleteCharAt(result.length() - 1);
            result.append("}");
        } else if (ClassUtils.isPrimitiveOrWrapper(obj.getClass()) || obj.getClass().getName().startsWith("java")) {
            result.append(obj);
            return;
        }

        if (!InstrumentResult.getSingleton().getClassDetailsMap().containsKey(obj.getClass().getName())) {
            Class<?> CUC = obj.getClass();
            List<Class> classesToGetFields = new ArrayList<>(ClassUtils.getAllSuperclasses(CUC));
            classesToGetFields.add(CUC);
            classesToGetFields.removeIf(c -> c.equals(Object.class) || c.getName().startsWith("java") || ClassUtils.isPrimitiveOrWrapper(c));
            InstrumentResult.getSingleton().addClassDetails(new ClassDetails(CUC.getName(), classesToGetFields.stream()
                    .flatMap(c -> Arrays.stream(c.getDeclaredFields()))
                    .filter(f -> !(soot.Modifier.isStatic(f.getModifiers()) && soot.Modifier.isFinal(f.getModifiers())))
                    .collect(Collectors.toList())));
        }

        result.append("{");
        InstrumentResult.getSingleton().getClassDetailsByID(obj.getClass().getName()).getClassFields()
                .forEach(f -> {
                    f.setAccessible(true);
                    try {
                        baseFieldsToString(f.get(obj), depth - 1, result);
                        result.append(",");
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    }
                });
        result.deleteCharAt(result.length() - 1);
        result.append("}");
    }
}
