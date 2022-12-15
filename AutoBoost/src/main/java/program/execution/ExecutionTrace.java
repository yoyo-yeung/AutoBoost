package program.execution;

import application.AutoBoost;
import application.PROGRAM_STATE;
import entity.ACCESS;
import entity.LOG_ITEM;
import entity.METHOD_TYPE;
import helper.Helper;
import helper.Properties;
import helper.xml.XMLParser;
import org.apache.commons.lang3.ClassUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DirectedMultigraph;
import org.mockito.MockingDetails;
import org.mockito.Mockito;
import program.analysis.MethodDetails;
import program.execution.variable.*;
import program.instrumentation.InstrumentResult;
import soot.Modifier;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class ExecutionTrace {
    private static final Logger logger = LogManager.getLogger(ExecutionTrace.class);
    private static final AtomicInteger varIDGenerator = new AtomicInteger(1);
    private static final ExecutionTrace singleton = new ExecutionTrace();
    private final InstrumentResult instrumentResult = InstrumentResult.getSingleton();
    private final Map<Integer, MethodExecution> allMethodExecs;
    private final Map<Class<?>, Set<MethodExecution>> constructingMethodExes;
    private final Map<Integer, VarDetail> allVars; // store all vardetail used, needed for lookups
    private final Map<Integer, Integer> unmockableVarToDefMap;
    private final DirectedMultigraph<Integer, CallOrderEdge> callGraph;
    private final Map<VarDetail, Stack<MethodExecution>> varToParentStackCache = new HashMap<>(); // cache last retrieval results to save time
    private final Map<MethodExecution, Boolean> exeToFaultyExeContainedCache = new HashMap<>(); // cache to save execution time
    private final VarDetail nullVar = new ObjVarDetails(0, Object.class, null);
    private final XMLParser parser = new XMLParser();
    private final Map<Integer, Set<VarDetail>> processedHashcodeToVarMap = new HashMap<>();
    private final Map<String, Set<VarDetail>> classNameToVarMap = new HashMap<>();

    /**
     * Constructor of ExecutionTrace, set up all vars.
     */
    public ExecutionTrace() {
        this.allMethodExecs = new ConcurrentHashMap<>();
        this.allVars = new ConcurrentHashMap<>();
        this.unmockableVarToDefMap = new HashMap<>();
        this.constructingMethodExes = new HashMap<>();
        callGraph = new DirectedMultigraph<>(CallOrderEdge.class);
        allVars.put(nullVar.getID(), nullVar);

    }

    /**
     * @return singleton of class, used across classes of program
     */
    public static ExecutionTrace getSingleton() {
        return singleton;
    }

    /**
     * @return new ID for VarDetail
     */
    public static int getNewVarID() {
        return varIDGenerator.incrementAndGet();
    }

    /**
     * @return all method executions stored, ID -> MethodExecution
     */
    public Map<Integer, MethodExecution> getAllMethodExecs() {
        return allMethodExecs;
    }

    /**
     * @return All Variables and their details stored, ID -> VarDetail
     */
    public Map<Integer, VarDetail> getAllVars() {
        return allVars;
    }

    /**
     * List all VarDetail ID and MethodExecution ID where the VarDetail is defined
     *
     * @return Map between a VarDetail ID and MethodExecution ID
     */
    public Map<Integer, Integer> getUnmockableVarToDefMap() {
        return unmockableVarToDefMap;
    }

    /**
     * Find method execution where the provided VarDetail is defined
     *
     * @param varID
     * @return ID of MethodExecution
     */
    public Integer getDefExeList(Integer varID) {
        return this.unmockableVarToDefMap.getOrDefault(varID, null);
    }

    public VarDetail getVarDetail(MethodExecution execution, Class<?> type, Object objValue, LOG_ITEM process, boolean canOnlyBeUse) {
        return getVarDetail(execution, type, objValue, process, canOnlyBeUse, new HashSet<>(), 7, new HashMap<>());
    }

    /**
     * If object already stored, return existing VarDetail ID stored
     * If not, create a new VarDetail and return the corresponding ID
     *
     * @param execution
     * @param type                    type of the object to be stored
     * @param objValue                object to be stored
     * @param process                 the current process, e.g. logging callee? param? or return value?
     * @param canOnlyBeUse
     * @param hashProcessing
     * @param processedHashToVarIDMap
     * @return VarDetail storing the provided object
     */
    public VarDetail getVarDetail(MethodExecution execution, Class<?> type, Object objValue, LOG_ITEM process, boolean canOnlyBeUse, Set<Integer> hashProcessing, int depth, Map<Integer, Integer> processedHashToVarIDMap) {
        int hashCode = System.identityHashCode(objValue);
        if (processedHashToVarIDMap.containsKey(hashCode)) {
            return getVarDetailByID(processedHashToVarIDMap.get(hashCode));
        }
        if (objValue == null) return nullVar;
       return getVarDetail(execution, processVar(execution, type, objValue, hashCode, process, canOnlyBeUse, hashProcessing, depth, processedHashToVarIDMap), hashCode, process, canOnlyBeUse, processedHashToVarIDMap);
    }

    public Object getContentForXMLStorage(MethodExecution execution, Class<?> type, Object objValue, LOG_ITEM process, boolean canOnlyBeUse, Set<Integer> hashProcessing, int depth, Map<Integer, Integer> processedHashToVarIDMap) {
        int hashCode = System.identityHashCode(objValue);
        if (processedHashToVarIDMap.containsKey(hashCode)) {
            return getVarDetailByID(processedHashToVarIDMap.get(hashCode));
        }
        if (objValue == null) return nullVar;
        IntermediateVarContent content = processVar(execution, type, objValue, hashCode, process, canOnlyBeUse, hashProcessing, depth, processedHashToVarIDMap);
        if(content.getVarDetailClass().equals(PrimitiveVarDetails.class) ||
                content.getVarDetailClass().equals(EnumVarDetails.class) ||
                content.getVarDetailClass().equals(WrapperVarDetails.class) ||
                (content.getVarDetailClass().equals(StringVarDetails.class) && ((String)content.getVarValue()).length() < 300))
            return content;
        else return getVarDetail(execution, content, hashCode, process, canOnlyBeUse, processedHashToVarIDMap);
    }

    public IntermediateVarContent processVar(MethodExecution execution, Class<?> type, Object objValue, int hashCode, LOG_ITEM process, boolean canOnlyBeUse, Set<Integer> hashProcessing, int depth, Map<Integer, Integer> processedHashToVarIDMap) {
        boolean artificialEnum = false;
        if (type.isEnum()) {
            objValue = ((Enum<?>) objValue).name();
        } else if (!type.isArray() && !type.equals(String.class) && !StringBVarDetails.availableTypeCheck(type) && !ClassUtils.isPrimitiveOrWrapper(type) && !((ArrVarDetails.availableTypeCheck(type) && ArrVarDetails.availableTypeCheck(objValue.getClass())) || (MapVarDetails.availableTypeCheck(type) && MapVarDetails.availableTypeCheck(objValue.getClass())))) {
            Object finalObjValue1 = objValue;

            Class<?> finalType = type;

            Optional<String> match = Arrays.stream(type.getDeclaredFields())
                    .filter(f -> f.getType().equals(finalType))
                    .filter(f -> (!instrumentResult.getClassPublicFieldsMap().containsKey(finalType.getName()) && Modifier.isPublic(f.getModifiers()) && Modifier.isStatic(f.getModifiers())) ||
                            instrumentResult.getClassPublicFieldsMap().getOrDefault(finalType.getName(), new HashSet<>()).contains(f.getName()))
                    .filter(f -> {
                        try {
                            Object val = f.get(finalObjValue1);
                            return val != null && System.identityHashCode(val) == hashCode;
                        } catch (IllegalAccessException e) {
                            return false;
                        }
                    }).map(Field::getName).findAny();

            if (match.isPresent()) {
                artificialEnum = true;
                objValue = match.get();
            } else if (type.equals(Class.class)) {
                artificialEnum = true;
                if (((Class<?>) objValue).isArray()) objValue = Array.class;
                objValue = ((Class<?>) objValue).getName();
            } else if (Mockito.mockingDetails(objValue).isMock()) {
                objValue = Mockito.mockingDetails(objValue);
                type = ((MockingDetails) objValue).getMockCreationSettings().getTypeToMock();
            } else {
                hashProcessing.add(hashCode);
                objValue = objectToStringWithAttr(execution, objValue, process, depth, processedHashToVarIDMap);
                hashProcessing.remove(hashCode);
            }
        } else if (ClassUtils.isPrimitiveOrWrapper(type)) {
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
        Class<? extends VarDetail> varDetailClass;
        Object checkVal = objValue;
        if (objValue instanceof MockingDetails) {
            varDetailClass = MockVarDetails.class;
        } else if (type.isEnum() || artificialEnum) varDetailClass = EnumVarDetails.class;
        else if (type.isPrimitive()) varDetailClass = PrimitiveVarDetails.class;
        else if (type.equals(String.class)) varDetailClass = StringVarDetails.class;
        else if (StringBVarDetails.availableTypeCheck(type)) {
            varDetailClass = StringBVarDetails.class;
            checkVal = getVarDetail(execution, String.class, objValue.toString(), process, true, hashProcessing, depth, processedHashToVarIDMap).getID();
        } else if (ArrVarDetails.availableTypeCheck(type) && ArrVarDetails.availableTypeCheck(objValue.getClass())) {
            varDetailClass = ArrVarDetails.class;
            checkVal = arrToString(execution, objValue, process, depth, processedHashToVarIDMap);
//            checkVal = getComponentStream(execution, type, objValue, process, canOnlyBeUse, hashProcessing, depth - 1, processedHashToVarIDMap).collect(Collectors.toList());
        } else if (MapVarDetails.availableTypeCheck(type) && MapVarDetails.availableTypeCheck(objValue.getClass())) {
            varDetailClass = MapVarDetails.class;
            hashProcessing.add(hashCode);
            checkVal = mapToString(execution, objValue, process, depth, processedHashToVarIDMap);
            hashProcessing.remove(hashCode);
        } else if (ClassUtils.isPrimitiveWrapper(type)) {
            varDetailClass = WrapperVarDetails.class;
        } else {
            varDetailClass = ObjVarDetails.class;
        }
        return new IntermediateVarContent(varDetailClass, type, objValue, checkVal, objValue.getClass());
    }

    public VarDetail getVarDetail(MethodExecution execution, IntermediateVarContent varContent, int hashCode, LOG_ITEM process, boolean canOnlyBeUse, Map<Integer, Integer> processedHashToVarIDMap) {
        Class<?> varDetailClass = varContent.getVarDetailClass();
        Class<?> type = varContent.getVarType();
        Object checkVal = varContent.getVarCheckVal();
        String className = varContent.getVarType().getName();
        Object  objValue = varContent.getVarValue();
        VarDetail varDetail = findExistingVarDetail(hashCode, type, varDetailClass, checkVal, className);
        if (varDetail == null) {
            if (varDetailClass.equals(EnumVarDetails.class))
                varDetail = new EnumVarDetails(getNewVarID(), type, (String) objValue);
            else if (varDetailClass.equals(PrimitiveVarDetails.class)) {
                varDetail = new PrimitiveVarDetails(getNewVarID(), type, objValue);
            } else if (varDetailClass.equals(StringVarDetails.class))
                varDetail = new StringVarDetails(getNewVarID(), (String) objValue);
            else if (varDetailClass.equals(StringBVarDetails.class))
                varDetail = new StringBVarDetails(getNewVarID(), type, (Integer) checkVal);
            else if (varDetailClass.equals(ArrVarDetails.class)) {
                varDetail = new ArrVarDetails(getNewVarID(), type, null, checkVal);
            } else if (varDetailClass.equals(MapVarDetails.class)) {
                varDetail = new MapVarDetails(getNewVarID(), (Class<? extends Map>) type, null, checkVal);
            } else if (varDetailClass.equals(WrapperVarDetails.class)) {
                varDetail = new WrapperVarDetails(getNewVarID(), type, objValue);
            } else if (varDetailClass.equals(MockVarDetails.class))
                varDetail = new MockVarDetails(getNewVarID(), type, (MockingDetails) objValue);
            else {
                // other cases
                varDetail = new ObjVarDetails(getNewVarID(), type, objValue);
            }
            addNewVarDetail(varDetail);
        }
        if (varDetail instanceof ObjVarDetails && !varDetail.getType().getName().startsWith(Properties.getSingleton().getPUT()) && !canOnlyBeUse) {
            if (setCurrentExeAsDef(varDetail, process, execution)) addNewVarDetailDef(varDetail, execution.getID());
            else addVarDetailUsage(varDetail);
        }
        if (varDetail instanceof ObjVarDetails || varDetail instanceof ArrVarDetails || varDetail instanceof MapVarDetails)
            processedHashToVarIDMap.put(hashCode, varDetail.getID());
        if (!processedHashcodeToVarMap.containsKey(hashCode))
            processedHashcodeToVarMap.put(hashCode, new HashSet<>());
        processedHashcodeToVarMap.get(hashCode).add(varDetail);
        if (!classNameToVarMap.containsKey(className))
            classNameToVarMap.put(className, new HashSet<>());
        classNameToVarMap.get(className).add(varDetail);
        return varDetail;
    }
    /**
     * Find if the current execution should be recognized as def of a var
     * e.g. If the variable is a callee, then it is a USE.
     *
     * @param varDetail the current Variable Details under review
     * @param process   the current logging step
     * @param execution the execution where the variable is used
     * @return if this occurance should be stored as def
     */
    private boolean setCurrentExeAsDef(VarDetail varDetail, LOG_ITEM process, MethodExecution execution) {
        if (!(varDetail instanceof ObjVarDetails)) return false; // no need to get a def if it is not an object
        // must be a resulting var
        switch (process) {
            case RETURN_ITEM:
            case RETURN_THIS:
                break;
            default:
                return false;
        }
        MethodDetails methodDetails = execution.getMethodInvoked();
        if ((methodDetails.getType().equals(METHOD_TYPE.MEMBER) && execution.getCallee().equals(varDetail)) || execution.getParams().stream().anyMatch(p -> p == varDetail.getID()))
            return false; // if they were inputs to begin with
        if (methodDetails.getAccess().equals(ACCESS.PRIVATE) || methodDetails.isFieldAccess())
            return false;
        if (methodDetails.getType().equals(METHOD_TYPE.MEMBER) && (getParentExeStack(execution.getCallee(), false) == null || getParentExeStack(execution.getCallee(), false).contains(execution)))
            return false;
        if (Helper.isCannotMockType(varDetail.getType()) && methodDetails.getDeclaringClass().getPackageName().startsWith(Properties.getSingleton().getPUT()))
            return false; // would not use as def if it is an un-mock-able param type created by PUT methods
        if (Properties.getSingleton().getFaultyFuncIds().contains(execution.getMethodInvoked().getId()) || containsFaultyDef(execution, true))
            return false;
        MethodExecution existingDef = getDefExeList(varDetail.getID()) == null ? null : getMethodExecutionByID(getDefExeList(varDetail.getID()));
        if (existingDef == null) return true;
        if (existingDef.sameCalleeParamNMethod(execution)) return false; // would compare method, callee, params
        MethodDetails existingDefDetails = existingDef.getMethodInvoked();
        if (existingDefDetails.getType().getRank() < methodDetails.getType().getRank()) return false;

        return Stream.of(execution, existingDef)
                .map(e -> new AbstractMap.SimpleEntry<>(e,
                        (e.getMethodInvoked().getType().equals(METHOD_TYPE.MEMBER) && e.getResultThisId() == varDetail.getID() ? -1000 : 0) +
                                e.getMethodInvoked().getParameterCount() +
                                e.getParams().stream().map(this::getVarDetailByID).filter(p -> p.getType().isAnonymousClass()).mapToInt(i -> 1000).sum() +
                                (instrumentResult.isLibMethod(e.getMethodInvoked().getId()) ? 999999 : 0) +
                                (e.getMethodInvoked().getType().equals(METHOD_TYPE.CONSTRUCTOR) || e.getMethodInvoked().getType().equals(METHOD_TYPE.STATIC) ? -2000 : 0)
//                                +
//                                (Properties.getSingleton().getFaultyFuncIds().contains(e.getMethodInvoked().getId()) ? 10000: 0)
                ))
                .min(Comparator.comparingInt(AbstractMap.SimpleEntry::getValue))
                .get().getKey().equals(execution);
    }


    /**
     * Used when the variable storing is of array/Collection type.
     * Retrieve Stream of VarDetail IDs of components in the array/Collection
     *
     * @param execution
     * @param type                    the type of variable (Array/subclasses of Collection/etc)
     * @param obj                     the variable
     * @param process                 the current logging step
     * @param canOnlyBeUse
     * @param processedHash
     * @param depth
     * @param processedHashToVarIDMap
     * @return Stream of VarDetail IDs
     */
    private Stream<Integer> getComponentStream(MethodExecution execution, Class<?> type, Object obj, LOG_ITEM process, boolean canOnlyBeUse, Set processedHash, int depth, Map<Integer, Integer> processedHashToVarIDMap) {
        if (!ArrVarDetails.availableTypeCheck(type))
            throw new IllegalArgumentException("Provided Obj cannot be handled.");
        Stream<Integer> componentStream;
        int hashCode = System.identityHashCode(obj);
        processedHash.add(hashCode);
        if (type.isArray()) {
            componentStream = IntStream.range(0, Array.getLength(obj))
                    .mapToObj(i -> Helper.getArrayElement(obj, i))
                    .filter(o -> Helper.isSimpleType(o) || !processedHash.contains(System.identityHashCode(o)))
                    .map(o -> getVarDetail(execution, type.getComponentType(), o, process, canOnlyBeUse, processedHash, depth, processedHashToVarIDMap).getID());
        } else if (Set.class.isAssignableFrom(type) && obj instanceof Set) {
            componentStream = ((Set) obj).stream().filter(v -> Helper.isSimpleType(v) || !processedHash.contains(System.identityHashCode(v)))
                    .map(v -> getVarDetail(execution, getClassOfObj(v), v, process, true, processedHash, depth, processedHashToVarIDMap).getID()).sorted(Comparator.comparingInt(x -> (int) x));
        } else {
            componentStream = ((Collection) obj).stream().filter(v -> Helper.isSimpleType(v) || !processedHash.contains(System.identityHashCode(v))).map(v -> getVarDetail(execution, getClassOfObj(v), v, process, canOnlyBeUse, processedHash, depth, processedHashToVarIDMap).getID());
        }
        processedHash.remove(hashCode);
        return componentStream;
    }

    /**
     * If the obj was defined and stored before, return ID of the corresponding ObjVarDetails for reuse. Else return -1
     *
     * @param varDetailClass
     * @param objValue
     * @param className
     * @return ObjVarDetails if the obj was defined and stored before, null if not
     */
    private VarDetail findExistingVarDetail(int hashCode, Class<?> type, Class<?> varDetailClass, Object objValue, String className) {
        if (varDetailClass.equals(EnumVarDetails.class)) {
//            if (type.equals(Class.class))
//                objValue = objValue.toString().replace("$", ".") + ".class";
//        } else if (varDetailClass.equals(ArrVarDetails.class)) {
//            objValue = ((List) objValue).stream().map(String::valueOf).collect(Collectors.joining(Properties.getDELIMITER()));
//        } else if (varDetailClass.equals(MapVarDetails.class)) {
//            objValue = ((Set<Map.Entry>) objValue).stream().map(e -> e.getKey() + "=" + e.getValue()).sorted().collect(Collectors.joining(Properties.getDELIMITER()));
        }
        Object finalObjValue1 = objValue;
        Optional<VarDetail> result;
        result = processedHashcodeToVarMap.getOrDefault(hashCode, new HashSet<>()).stream()
                .filter(v -> v.getClass().equals(varDetailClass))
                .filter(v -> v.sameTypeNValue(type, finalObjValue1))
                .findAny();
        if (!result.isPresent())
            result = this.classNameToVarMap.getOrDefault(className, new HashSet<>()).stream()
                    .filter(v -> v.getClass().equals(varDetailClass))
                    .filter(v -> v.sameValue(finalObjValue1))
                    .findAny();
        return result.orElse(null);
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
        if (!this.unmockableVarToDefMap.containsKey(varID))
            this.unmockableVarToDefMap.put(varID, null);
    }

    public void addNewVarDetail(VarDetail detail) {
        if (!this.allVars.containsKey(detail.getID()))
            this.allVars.put(detail.getID(), detail);
    }

    /**
     * Add a newly created VarDetail to the collection
     * Add def-relationship between method execution and VarDetail
     *
     * @param detail      Newly created VarDetail to document
     * @param executionID ID of MethodExecution that define a new VarDetail
     */
    public void addNewVarDetailDef(VarDetail detail, int executionID) {
        this.setUpVarMap(detail.getID());
        // only store it as a def if it is an object (need construction)
        if (detail instanceof ObjVarDetails)
            this.unmockableVarToDefMap.put(detail.getID(), executionID);
    }

    /**
     * Add record of a MethodExecution (with ID executionID) using a particular VarDetail (with ID detailID)
     *
     * @param detail ID of existing VarDetail
     */
    public void addVarDetailUsage(VarDetail detail) {
        this.setUpVarMap(detail.getID());
    }

    public void addMethodExecution(MethodExecution execution) {
        int executionID = execution.getID();
        this.callGraph.addVertex(executionID); // add vertex even if it has no son/ father
    }

    public void updateFinishedMethodExecution(MethodExecution execution) {
        int executionID = execution.getID();
        if (AutoBoost.getCurrentProgramState().equals(PROGRAM_STATE.CONSTRUCTOR_SEARCH)) {
            if (execution.getResultThisId() == -1 && execution.getReturnValId() == -1) {
                this.allMethodExecs.put(executionID, execution);
                return;
            }
            Class<?> createdClass = execution.getMethodInvoked().getdClass();
            if (!(execution.getMethodInvoked().getType().equals(METHOD_TYPE.CONSTRUCTOR) && getVarDetailByID(execution.getResultThisId()).getType().equals(createdClass)) && !(execution.getMethodInvoked().getType().equals(METHOD_TYPE.STATIC) && getVarDetailByID(execution.getReturnValId()).getType().equals(createdClass))) {
                this.allMethodExecs.put(executionID, execution);
                return;
            }
            if (!this.constructingMethodExes.containsKey(createdClass))
                this.constructingMethodExes.put(createdClass, new HashSet<>());
            this.constructingMethodExes.get(createdClass).add(execution);
        } else
            this.allMethodExecs.put(executionID, execution);
    }

    public void addMethodRelationship(int father, int son, int exeOrder) {
        this.callGraph.addVertex(father);
        this.callGraph.addVertex(son);
        this.callGraph.addEdge(father, son, new CallOrderEdge(exeOrder));
    }


    public void changeVertex(int original, int now) {
        if (!this.callGraph.containsVertex(original)) return;
        this.callGraph.addVertex(now);
        this.callGraph.removeAllEdges(original, now);
        this.callGraph.removeAllEdges(now, original);
        this.callGraph.outgoingEdgesOf(original).forEach(e -> addMethodRelationship(now, this.callGraph.getEdgeTarget(e), e.getLabel()));
        this.callGraph.incomingEdgesOf(original).forEach(e -> addMethodRelationship(this.callGraph.getEdgeSource(e), now, e.getLabel()));
        this.callGraph.removeVertex(original);
    }

    public List<Integer> getChildren(int father) {
        List<Integer> results = new ArrayList<>();
        this.callGraph.outgoingEdgesOf(father).stream().filter(e -> this.callGraph.getEdgeTarget(e) != father).sorted(Comparator.comparingInt(CallOrderEdge::getLabel)).forEach(e -> results.add(this.callGraph.getEdgeTarget(e)));
        return results;
    }

    public VarDetail getVarDetailByID(int varID) {
        if (!this.allVars.containsKey(varID))
            throw new IllegalArgumentException("VarDetail with ID" + varID + " does not exist.");
        return this.allVars.get(varID);
    }

    public MethodExecution getMethodExecutionByID(int exeID) {
        if (this.allMethodExecs.containsKey(exeID)) return this.allMethodExecs.get(exeID);
        Optional<MethodExecution> result = ExecutionLogger.getAllExecuting().stream().filter(e -> e.getID() == exeID).findAny();
        if (result.isPresent()) return result.get();
        else
            result = constructingMethodExes.values().stream().flatMap(Collection::stream).filter(e -> e.getID() == exeID).findAny();
        if (result.isPresent()) return result.get();
        else throw new IllegalArgumentException("MethodExecution with ID " + exeID + " does not exist");
    }

    public VarDetail getNullVar() {
        return nullVar;
    }

    private Object objectToStringWithAttr(MethodExecution execution, Object obj, LOG_ITEM process, int depth, Map<Integer, Integer> processedHashToVarIDMap) {
        if (obj == null) return null;
        return
                parser.getXML(execution, obj, process, depth, processedHashToVarIDMap);
    }

    private Object arrToString(MethodExecution execution, Object obj, LOG_ITEM process, int depth, Map<Integer, Integer> processedHashToVarIDMap) {
        if(obj == null) return null;
        if(!ArrVarDetails.availableTypeCheck(obj.getClass())) throw new IllegalArgumentException("Illegal arr to string ");
        return parser.getXML(execution, obj, process, depth, processedHashToVarIDMap);
    }
    private Object mapToString(MethodExecution execution, Object obj, LOG_ITEM process, int depth, Map<Integer, Integer> processedHashToVarIDMap) {
        if(obj == null) return null;
        if(!MapVarDetails.availableTypeCheck(obj.getClass())) throw new IllegalArgumentException("Illegal arr to string ");
        return parser.getXML(execution, obj, process, depth, processedHashToVarIDMap);
    }
    public void clear() {
        allMethodExecs.clear();
        unmockableVarToDefMap.clear();
    }

    public void replacePossibleDefExe(MethodExecution original, MethodExecution repl) {
        Set<Integer> varInvolved = new HashSet<>(original.getParams());
        varInvolved.add(original.getCalleeId());
        varInvolved.add(original.getReturnValId());
        varInvolved.add(original.getResultThisId());
        if (original.getReturnValId() != -1) {
            VarDetail returnVarDetail = getVarDetailByID(original.getReturnValId());
            if (returnVarDetail instanceof ArrVarDetails)
                varInvolved.addAll(((ArrVarDetails) returnVarDetail).getComponents());
            if (returnVarDetail instanceof MapVarDetails)
                varInvolved.addAll(((MapVarDetails) returnVarDetail).getKeyValuePairs().stream().flatMap(e -> Stream.of(e.getKey(), e.getValue())).collect(Collectors.toList()));
        }
        varInvolved.remove(-1);
        varInvolved.removeIf(v -> !unmockableVarToDefMap.containsKey(v) || unmockableVarToDefMap.get(v) == null || unmockableVarToDefMap.get(v) != original.getID());
        varInvolved.forEach(v -> unmockableVarToDefMap.replace(v, original.getID(), repl.getID()));

    }



    public Set<ObjVarDetails> getAllObjVarInvolved(VarDetail varDetail) {
        Set<ObjVarDetails> res = new HashSet<>();
        if (varDetail instanceof ObjVarDetails) res.add((ObjVarDetails) varDetail);
        if (varDetail instanceof ArrVarDetails)
            res.addAll(((ArrVarDetails) varDetail).getComponents().stream().map(this::getVarDetailByID).flatMap(v -> getAllObjVarInvolved(v).stream()).collect(Collectors.toSet()));
        if (varDetail instanceof MapVarDetails)
            res.addAll(((MapVarDetails) varDetail).getKeyValuePairs().stream().flatMap(v -> Stream.of(v.getKey(), v.getValue())).map(this::getVarDetailByID).flatMap(v -> getAllObjVarInvolved(v).stream()).collect(Collectors.toSet()));
        return res;
    }


    private boolean hasFieldAccess(MethodExecution execution, VarDetail varDetail, HashSet<MethodExecution> processed) {
        if (processed.contains(execution)) return false;
        processed.add(execution);
        if (execution.getMethodInvoked().isFieldAccess() && execution.getCalleeId() == varDetail.getID()) return true;
        else
            return getChildren(execution.getID()).stream().map(this::getMethodExecutionByID).anyMatch(e -> hasFieldAccess(e, varDetail, processed));
    }

    public boolean hasFieldAccess(MethodExecution execution, VarDetail varDetail) {
        return hasFieldAccess(execution, varDetail, new HashSet<>());
    }


    private boolean hasUsageAsCallee(MethodExecution execution, Set<Integer> vars, Set<Integer> processedExe) {
        if (processedExe.contains(execution.getID())) return false;
        processedExe.add(execution.getID());
        return getChildren(execution.getID()).stream()
                .map(this::getMethodExecutionByID)
                .anyMatch(e -> vars.contains(e.getCalleeId()) || hasUsageAsCallee(e, vars, processedExe));
    }

    public boolean hasUsageAsCallee(MethodExecution execution, Set<Integer> vars) {
        return hasUsageAsCallee(execution, vars, new HashSet<>());
    }


    /**
     * Get stack of method executions needed to create the var specified
     *
     * @param var   Variable to create
     * @param cache
     * @return stack of method executions for creation
     */
    public Stack<MethodExecution> getParentExeStack(VarDetail var, boolean cache) {
        Stack<MethodExecution> executionStack = new Stack<>();
        if (!(var instanceof ObjVarDetails)) return null;
        if (varToParentStackCache.containsKey(var)) return varToParentStackCache.get(var);
        while (var != null) {
            if (var instanceof EnumVarDetails) break;
            Integer def = getDefExeList(var.getID());
            if (def == null) return null; // i.e. can not be produced
            MethodExecution defExe = getMethodExecutionByID(def);
            if (executionStack.stream().anyMatch(e -> e.getID() == defExe.getID()))
                return null; // if its a loop def

            executionStack.push(defExe);
            if (defExe.getMethodInvoked().getType().equals(METHOD_TYPE.MEMBER))
                var = defExe.getCallee();
            else
                var = null;
        }
        if (cache)
            varToParentStackCache.put(var, executionStack);
        return executionStack;
    }

    public boolean containsFaultyDef(MethodExecution execution, boolean useCache, HashSet<Integer> processed) {
        MethodDetails details = execution.getMethodInvoked();
        if (useCache && exeToFaultyExeContainedCache.containsKey(execution))
            return exeToFaultyExeContainedCache.get(execution);
        processed.add(execution.getID());
        exeToFaultyExeContainedCache.put(execution, Properties.getSingleton().getFaultyFuncIds().stream()
                .map(instrumentResult::getMethodDetailByID)
                .anyMatch(s -> s.equals(details) || (execution.getCalleeId() != -1 && s.getName().equals(details.getName()) && getVarDetailByID(execution.getCalleeId()).getType().equals(s.getdClass())))
                || getChildren(execution.getID()).stream().filter(exeID -> !processed.contains(exeID)).anyMatch(exeID -> containsFaultyDef(exeID, useCache, processed)));
        return exeToFaultyExeContainedCache.get(execution);
    }

    /**
     * @param execution MethodExecution under check
     * @param useCache
     * @return if the execution provided is / runs faulty methods
     */
    public boolean containsFaultyDef(MethodExecution execution, boolean useCache) {
        return containsFaultyDef(execution, useCache, new HashSet<>());
    }

    /**
     * @param exeID    id of MethodExecution under check
     * @param useCache
     * @return if the execution provided is / runs faulty methods
     */
    private boolean containsFaultyDef(int exeID, boolean useCache, HashSet<Integer> processed) {
        return containsFaultyDef(getMethodExecutionByID(exeID), useCache, processed);
    }


    /**
     * Get Object Vardetail IDs relating to input
     *
     * @param varDetailID varDetail to investigate
     * @return Set of ids matching criteria
     */
    public Set<Integer> getRelatedObjVarIDs(int varDetailID) {
        if (varDetailID == -1) return new HashSet<>();
        return getRelatedObjVarIDs(getVarDetailByID(varDetailID));
    }

    /**
     * Get Object Vardetail IDs relating to input
     *
     * @param varDetail varDetail to investigate
     * @return Set of ids matching criteria
     */
    public Set<Integer> getRelatedObjVarIDs(VarDetail varDetail) {
        Set<Integer> relatedVarIDs = new HashSet<>();
        if (varDetail instanceof StringVarDetails || varDetail instanceof StringBVarDetails || varDetail instanceof PrimitiveVarDetails || varDetail instanceof EnumVarDetails)
            return relatedVarIDs;
        relatedVarIDs.add(varDetail.getID());
        if (varDetail instanceof ArrVarDetails)
            relatedVarIDs.addAll(((ArrVarDetails) varDetail).getComponents().stream().map(this::getRelatedObjVarIDs).flatMap(Collection::stream).collect(Collectors.toSet()));
        if (varDetail instanceof MapVarDetails)
            relatedVarIDs.addAll(((MapVarDetails) varDetail).getKeyValuePairs().stream()
                    .flatMap(pair -> Stream.of(pair.getKey(), pair.getValue()))
                    .map(this::getRelatedObjVarIDs)
                    .flatMap(Collection::stream)
                    .collect(Collectors.toSet()));
        return relatedVarIDs;
    }


    public Map<Class<?>, Set<MethodExecution>> getConstructingMethodExes() {
        return constructingMethodExes;
    }

    public static class CallOrderEdge extends DefaultEdge {
        private final int label;

        /**
         * Constructs a CallOrderEdge
         *
         * @param label the label of the new edge
         */
        public CallOrderEdge(int label) {
            this.label = label;
        }

        /**
         * Gets the label associated with this edge
         *
         * @return edge label
         */
        public int getLabel() {
            return label;
        }

        @Override
        public String toString() {
            return "(" + getSource() + " : " + getTarget() + " : " + label + ")";
        }
    }

    public static class IntermediateVarContent {
        private Class<? extends VarDetail> varDetailClass;
        private Class<?> varType;
        private Object varValue;
        private Object varCheckVal;
        private Class<?> valueStoredType;

        public IntermediateVarContent() {
        }

        public IntermediateVarContent(Class<? extends VarDetail> varDetailClass, Class<?> varType, Object varValue, Object varCheckVal, Class<?> valueStoredType) {
            this.varDetailClass = varDetailClass;
            this.varType = varType;
            this.varValue = varValue;
            this.varCheckVal = varCheckVal;
            this.valueStoredType = valueStoredType;
        }

        public Class<? extends VarDetail> getVarDetailClass() {
            return varDetailClass;
        }

        public Class<?> getVarType() {
            return varType;
        }

        public Object getVarValue() {
            return varValue;
        }

        public Object getVarCheckVal() {
            return varCheckVal;
        }

        public Class<?> getValueStoredType() {
            return valueStoredType;
        }

        public void setVarDetailClass(Class<? extends VarDetail> varDetailClass) {
            this.varDetailClass = varDetailClass;
        }

        public void setVarType(Class<?> varType) {
            this.varType = varType;
        }

        public void setVarValue(Object varValue) {
            this.varValue = varValue;
        }

        public void setVarCheckVal(Object varCheckVal) {
            this.varCheckVal = varCheckVal;
        }

        public void setValueStoredType(Class<?> valueStoredType) {
            this.valueStoredType = valueStoredType;
        }

        public void clear() {
            this.varDetailClass = null;
            this.varType = null;
            this.varValue = null;
            this.varCheckVal = null;
            this.valueStoredType = null;
        }
    }
}
