package program.execution;

import entity.ACCESS;
import entity.LOG_ITEM;
import entity.METHOD_TYPE;
import helper.Helper;
import helper.Properties;
import helper.xml.XMLWriter;
import org.apache.commons.lang3.ClassUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DirectedMultigraph;
import program.analysis.MethodDetails;
import program.execution.variable.*;
import program.instrumentation.InstrumentResult;
import soot.Modifier;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static helper.Helper.*;

public class ExecutionTrace {
    private static final Logger logger = LogManager.getLogger(ExecutionTrace.class);
    private static final AtomicInteger varIDGenerator = new AtomicInteger(1);
    private final Map<Integer, MethodExecution> allMethodExecs;
    private final Map<Integer, VarDetail> allVars; // store all vardetail used, needed for lookups
    private final Map<Integer, Integer> varToDefMap;
    private final DirectedMultigraph<Integer, CallOrderEdge> callGraph;
    private static VarDetail nullVar;
    private final Map<VarDetail, Stack<MethodExecution>> varToParentStackCache = new HashMap<>(); // cache last retrieval results to save time
    private final Map<MethodExecution, Boolean> exeToFaultyExeContainedCache = new HashMap<>(); // cache to save execution time

    private static Map<Class<?>, VarDetail> defaultValVar;
    static {
        nullVar = new ObjVarDetails(0, Object.class, "null");
        defaultValVar = Arrays.stream(new Class<?>[]{char.class, boolean.class, byte.class, int.class, short.class, long.class, float.class, double.class}).flatMap(c -> Stream.of(new AbstractMap.SimpleEntry<Class<?>, VarDetail>(c, new PrimitiveVarDetails(getNewVarID(), c, Helper.getDefaultValue(c))), new AbstractMap.SimpleEntry<Class<?>, VarDetail>(ClassUtils.primitiveToWrapper(c), new WrapperVarDetails(getNewVarID(), ClassUtils.primitiveToWrapper(c), getDefaultValue(c))))).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        defaultValVar.put(String.class, nullVar);
    }
    private static final ExecutionTrace singleton = new ExecutionTrace();
    /**
     * Constructor of ExecutionTrace, set up all vars.
     */
    public ExecutionTrace() {
        this.allMethodExecs = new ConcurrentHashMap<>();
        this.allVars = new ConcurrentHashMap<>();
        this.varToDefMap = new HashMap<Integer, Integer>();
        callGraph = new DirectedMultigraph<>(CallOrderEdge.class);
        allVars.put(nullVar.getID(), nullVar);
        defaultValVar.values().stream().forEach(v -> allVars.put(v.getID(), v));
    }

    /**
     * @return singleton of class, used across classes of program
     */
    public static ExecutionTrace getSingleton() {
        return singleton;
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
    public Map<Integer, Integer> getVarToDefMap() {
        return varToDefMap;
    }

    /**
     * Find method execution where the provided VarDetail is defined
     *
     * @param varID
     * @return ID of MethodExecution
     */
    public Integer getDefExeList(Integer varID) {
        return this.varToDefMap.getOrDefault(varID, null);
    }



    /**
     * @return new ID for VarDetail
     */
    public static int getNewVarID() {
        return varIDGenerator.incrementAndGet();
    }

    public VarDetail getVarDetail(MethodExecution execution, Class<?> type, Object objValue, LOG_ITEM process, boolean canOnlyBeUse) {
        return getVarDetail(execution, type, objValue, process, canOnlyBeUse, new HashSet<Integer>());
    }

    /**
     * If object already stored, return existing VarDetail ID stored
     * If not, create a new VarDetail and return the corresponding ID
     *
     * @param execution
     * @param type       type of the object to be stored
     * @param objValue   object to be stored
     * @param process    the current process, e.g. logging callee? param? or return value?
     * @param canOnlyBeUse
     * @param processedHash
     * @return VarDetail storing the provided object
     */
    public VarDetail getVarDetail(MethodExecution execution, Class<?> type, Object objValue, LOG_ITEM process, boolean canOnlyBeUse, Set<Integer> processedHash) {
        boolean artificialEnum = false;
        if (objValue == null) return nullVar;
        if (type.isEnum()) {
            objValue = ((Enum) objValue).name();
        } else if (!type.isArray() && !type.equals(String.class) && !StringBVarDetails.availableTypeCheck(type) && !ClassUtils.isPrimitiveOrWrapper(type) && !((ArrVarDetails.availableTypeCheck(type) && ArrVarDetails.availableTypeCheck(objValue.getClass())) || (MapVarDetails.availableTypeCheck(type) && MapVarDetails.availableTypeCheck(objValue.getClass())))) {
            Object finalObjValue1 = objValue;

            Class<?> finalType = type;
            Optional<String> match = Arrays.stream(type.getDeclaredFields()).filter(f -> f.getType().equals(finalType)).filter(f -> {
                try {
                    return (!InstrumentResult.getSingleton().getClassPublicFieldsMap().containsKey(finalType.getName()) && Modifier.isPublic(finalType.getField(f.getName()).getModifiers())) || InstrumentResult.getSingleton().getClassPublicFieldsMap().get(finalType.getName()).contains(f.getName());
                } catch (NoSuchFieldException e) {
                    return false;
                }
            }).filter(f -> {
                try {
                    Object val = f.get(finalObjValue1);
                    return val != null && System.identityHashCode(val) == System.identityHashCode(finalObjValue1);
                } catch (IllegalAccessException e) {
                    return false;
                }
            }).map(Field::getName).findAny();

            if (match.isPresent()) {
                artificialEnum = true;
                objValue = match.get();
            } else if (type.equals(Class.class)) {
                artificialEnum = true;
                if (((Class) objValue).isArray()) objValue = Array.class;
                objValue = ((Class) objValue).getName();
            } else {
                processedHash.add(System.identityHashCode(objValue));
                objValue = toStringWithAttr(objValue);
//                logger.debug(objValue);
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
        }else  if(!type.equals(String.class))       processedHash.add(System.identityHashCode(objValue));
        Class<?> varDetailClass;
        Object checkVal = objValue;
        if (type.isEnum() || artificialEnum) varDetailClass = EnumVarDetails.class;
        else if (type.isPrimitive()) varDetailClass = PrimitiveVarDetails.class;
        else if (type.equals(String.class)) varDetailClass = StringVarDetails.class;
        else if (StringBVarDetails.availableTypeCheck(type)) {
            varDetailClass = StringBVarDetails.class;
            checkVal = getVarDetail(execution, String.class, objValue.toString(), process, true, processedHash).getID();
        }
        else if(ArrVarDetails.availableTypeCheck(type) && ArrVarDetails.availableTypeCheck(objValue.getClass())) {
            varDetailClass = ArrVarDetails.class;
            checkVal = getComponentStream(execution, type, objValue, process, canOnlyBeUse, processedHash).collect(Collectors.toList());
        }
        else if(MapVarDetails.availableTypeCheck(type) && MapVarDetails.availableTypeCheck(objValue.getClass())) {
            varDetailClass = MapVarDetails.class;
            checkVal = ((Map<?, ?>) objValue).entrySet().stream()
                    .filter(e -> !processedHash.contains(System.identityHashCode(e.getKey())) && !processedHash.contains(System.identityHashCode(e.getValue())))
                    .map(e -> new AbstractMap.SimpleEntry<Integer, Integer>(getVarDetail(execution, getClassOfObj(e.getKey()), e.getKey(), process, true, processedHash).getID(), getVarDetail(execution, getClassOfObj(e.getValue()), e.getValue(), process, true, processedHash).getID()))
                    .collect(Collectors.<Map.Entry<Integer, Integer>>toSet());
        }
        else if (ClassUtils.isPrimitiveWrapper(type)) {
            varDetailClass = WrapperVarDetails.class;
        }
        else {
            varDetailClass = ObjVarDetails.class;
        }
        VarDetail varDetail = findExistingVarDetail(type, varDetailClass, checkVal);
        if (varDetail == null) {
            if (varDetailClass.equals(EnumVarDetails.class))
                varDetail = new EnumVarDetails(getNewVarID(), type, (String) objValue);
            else if (varDetailClass.equals(PrimitiveVarDetails.class)) {
                varDetail = new PrimitiveVarDetails(getNewVarID(), type, objValue);
            } else if (varDetailClass.equals(StringVarDetails.class))
                varDetail = new StringVarDetails(getNewVarID(), (String) objValue);
            else if (varDetailClass.equals(StringBVarDetails.class))
                varDetail = new StringBVarDetails(getNewVarID(), type, (Integer)checkVal);
            else if (varDetailClass.equals(ArrVarDetails.class)) {
                int defaultComponentID = 0;
                int arrSize = 0;
                if(objValue.getClass().isArray()) {
                    arrSize = Array.getLength(objValue);
                    if(ClassUtils.isPrimitiveOrWrapper(objValue.getClass().getComponentType()) || objValue.getClass().getComponentType().equals(String.class)) {
                        Class<?> componentType = objValue.getClass().getComponentType();
                        defaultComponentID = this.defaultValVar.get(componentType).getID();
                    }
                }
                else if(objValue instanceof Collection) arrSize = ((Collection<?>) objValue).size();


                varDetail = new ArrVarDetails(getNewVarID(), (List<Integer>) checkVal, objValue, arrSize, defaultComponentID);
            } else if (varDetailClass.equals(MapVarDetails.class)) {
                varDetail = new MapVarDetails(getNewVarID(), type, (Set<Map.Entry<Integer, Integer>>) checkVal, objValue);
            } else if (varDetailClass.equals(WrapperVarDetails.class)) {
                varDetail = new WrapperVarDetails(getNewVarID(), type, objValue);
            } else {
                // other cases
                varDetail = new ObjVarDetails(getNewVarID(), type, objValue);
            }
            assert varDetail != null; // if null, then fail to generate test
            addNewVarDetail(varDetail);
            if (!canOnlyBeUse) {
                if (setCurrentExeAsDef(varDetail, process, execution)) addNewVarDetailDef(varDetail, execution.getID());
                else addVarDetailUsage(varDetail, execution.getID());
            }
        } else {
            if (!canOnlyBeUse) {
                if (setCurrentExeAsDef(varDetail, process, execution))
                    addNewVarDetailDef(varDetail, execution.getID());
                else
                    addVarDetailUsage(varDetail, execution.getID());
            }
        }
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
        if(methodDetails.getType().equals(METHOD_TYPE.MEMBER) && (getParentExeStack(execution.getCallee(), false) == null || getParentExeStack(execution.getCallee(), false).contains(execution)))
            return false;
        if(Helper.isCannotMockType(varDetail.getType()) && methodDetails.getDeclaringClass().getPackageName().startsWith(Properties.getSingleton().getPUT())) return false; // would not use as def if it is an un-mock-able param type created by PUT methods
        if(Properties.getSingleton().getFaultyFuncIds().contains(execution.getMethodInvoked().getId()) || containsFaultyDef(execution, true)) return false;
        MethodExecution existingDef = getDefExeList(varDetail.getID()) == null ? null : getMethodExecutionByID(getDefExeList(varDetail.getID()));
        if (existingDef == null) return true;
        if (existingDef.sameCalleeParamNMethod(execution)) return false; // would compare method, callee, params
        MethodDetails existingDefDetails = existingDef.getMethodInvoked();
        if (existingDefDetails.getType().getRank() < methodDetails.getType().getRank()) return false;

        return Stream.of(execution, existingDef)
                .map(e -> new AbstractMap.SimpleEntry<MethodExecution, Integer>(e,
                        (e.getMethodInvoked().getType().equals(METHOD_TYPE.MEMBER) && e.getResultThisId() == varDetail.getID() ? -1000 : 0) +
                                e.getMethodInvoked().getParameterCount() +
                                e.getParams().stream().map(this::getVarDetailByID).filter(p -> p.getType().isAnonymousClass() ).mapToInt(i->1000).sum() +
                                (InstrumentResult.getSingleton().isLibMethod(e.getMethodInvoked().getId()) ? 999999 : 0) +
                                (e.getMethodInvoked().getType().equals(METHOD_TYPE.CONSTRUCTOR) || e.getMethodInvoked().getType().equals(METHOD_TYPE.STATIC) ? -2000: 0 )
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
     * @param type      the type of variable (Array/subclasses of Collection/etc)
     * @param obj       the variable
     * @param process   the current logging step
     * @param canOnlyBeUse
     * @param processedHash
     * @return Stream of VarDetail IDs
     */
    private Stream<Integer> getComponentStream(MethodExecution execution, Class<?> type, Object obj, LOG_ITEM process, boolean canOnlyBeUse, Set processedHash) {
        if (!ArrVarDetails.availableTypeCheck(type))
            throw new IllegalArgumentException("Provided Obj cannot be handled.");
        Stream<Integer> componentStream;
        if (type.isArray()) {
            int trimmedLength = Array.getLength(obj);
            componentStream = IntStream.range(0, trimmedLength).filter(i -> !processedHash.contains(System.identityHashCode(Helper.getArrayElement(obj,i)))).mapToObj(i -> getVarDetail(execution, type.getComponentType(), Helper.getArrayElement(obj, i), process, canOnlyBeUse, processedHash).getID());
        }
        else if (Set.class.isAssignableFrom(type) && obj instanceof Set)
            componentStream = ((Set) obj).stream().filter(v -> ! processedHash.contains(System.identityHashCode(v))).map(v -> getVarDetail(execution, getClassOfObj(v), v, process, true, processedHash).getID()).sorted(Comparator.comparingInt(x -> (int)x));
        else
            componentStream = ((Collection) obj).stream().filter(v-> !processedHash.contains(System.identityHashCode(v))).map(v -> getVarDetail(execution, getClassOfObj(v), v, process, canOnlyBeUse, processedHash).getID());
        return componentStream;
    }

    /**
     * If the obj was defined and stored before, return ID of the corresponding ObjVarDetails for reuse. Else return -1
     *
     * @param varDetailClass
     * @param objValue
     * @return ObjVarDetails if the obj was defined and stored before, null if not
     */
    private VarDetail findExistingVarDetail(Class<?> type, Class<?> varDetailClass, Object objValue) {
        if (varDetailClass.equals(EnumVarDetails.class)) {
            if (type.equals(Class.class))
                objValue = objValue.toString().replace("$", ".") + ".class";
        }else if (varDetailClass.equals(ArrVarDetails.class)) {
            objValue = ((List)objValue).stream().map(String::valueOf).collect(Collectors.joining(Properties.getDELIMITER())) ;
        }
        else if (varDetailClass.equals(MapVarDetails.class)) {
            objValue = ((Set<Map.Entry>)objValue).stream().map(e -> e.getKey()+"="+e.getValue()).sorted().collect(Collectors.joining(Properties.getDELIMITER()));
        }
        // high chance of having the same value
//        if (process.equals(LOG_ITEM.RETURN_THIS) && ExecutionLogger.getLatestExecution().getCalleeId() != -1) {
//            VarDetail calleeDetails = getVarDetailByID(ExecutionLogger.getLatestExecution().getCalleeId());
//            if (calleeDetails.sameValue(type, objValue))
//                return calleeDetails.getID();
//        }
        Object finalObjValue1 = objValue;
        Optional<VarDetail> result;
        result = this.allVars.values().stream()
                .filter(Objects::nonNull)
                .filter(v -> v.getClass().equals(varDetailClass))
                .filter(v -> v.sameValue(type, finalObjValue1))
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
        if (!this.varToDefMap.containsKey(varID))
            this.varToDefMap.put(varID, null);
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
            this.varToDefMap.put(detail.getID(), executionID);
    }

    /**
     * Add record of a MethodExecution (with ID executionID) using a particular VarDetail (with ID detailID)
     *
     * @param detail      ID of existing VarDetail
     * @param executionID ID of a method execution
     */
    public void addVarDetailUsage(VarDetail detail, int executionID) {
        this.setUpVarMap(detail.getID());
    }

    public void addMethodExecution(MethodExecution execution) {
        int executionID = execution.getID();
        this.callGraph.addVertex(executionID); // add vertex even if it has no son/ father
    }

    public void updateFinishedMethodExecution(MethodExecution execution) {
        int executionID = execution.getID();
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

    public void removeVertex(int vertex) {
        if (!this.callGraph.containsVertex(vertex)) return;
        this.callGraph.removeVertex(vertex);
    }

    public List<Integer> getChildren(int father) {
        List<Integer> results = new ArrayList<>();
        this.callGraph.outgoingEdgesOf(father).stream().filter(e -> this.callGraph.getEdgeTarget(e) != father).sorted(Comparator.comparingInt(CallOrderEdge::getLabel)).forEach(e -> {
            results.add(this.callGraph.getEdgeTarget(e));
        });
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
        else throw new IllegalArgumentException("MethodExecution with ID " + exeID + " does not exist");
    }

    public VarDetail getNullVar() {
        return nullVar;
    }

    private String toStringWithAttr(Object obj) {
        if (obj == null) return "null";
        StringBuilder result = new StringBuilder(1000);
//        toCustomString("this", obj, 7, result, new HashMap<>());
//        result.trimToSize();
//        logger.debug();
//        logger.debug(result.toString());
        return new XMLWriter().getXML(obj);
    }

    public void clear() {
        allMethodExecs.clear();
        varToDefMap.clear();
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
        varInvolved.removeIf(v -> !varToDefMap.containsKey(v) || varToDefMap.get(v) == null || varToDefMap.get(v) != original.getID());
        varInvolved.forEach(v -> varToDefMap.replace(v, original.getID(), repl.getID()));

    }

    public DirectedMultigraph<Integer, CallOrderEdge> getCallGraph() {
        return callGraph;
    }

    /**
     * Update canTest field in each execution storing if the execution can be tested
     */
    public void checkTestabilityOfExecutions() {
        logger.info("Checking if executions can be tested ");
        Queue<MethodExecution> executionQueue = this.allMethodExecs.values().stream().sorted(Comparator.comparingInt(MethodExecution::getID)).collect(Collectors.toCollection(LinkedList::new));
        Set<Integer> checked = new HashSet<>();
        Map<Integer, Set<Integer>> exeToInputVarsMap = new HashMap<>();
        Map<Integer, Set<Integer>> exeToUnmockableInputVarMap = new HashMap<>();
        while (!executionQueue.isEmpty()) {
            MethodExecution execution = executionQueue.poll();
            if(execution.getCalleeId()!=-1 && getParentExeStack(execution.getCallee(), true)==null) {
                execution.setCanTest(false);
                checked.add(execution.getID());
                continue;
            }
            if (execution.getCalleeId() != -1 && getDefExeList(execution.getCalleeId()) != null && !checked.contains(getDefExeList(execution.getCalleeId()))) {
                executionQueue.add(execution); // if the callee is not checked yet, wait
                continue;
            }
            logger.debug("checking " +  execution.getID() + "\t" + execution.getMethodInvoked().toString() );
            checked.add(execution.getID());
            execution.setCanTest(canTestExecution(execution) && canTestCallee(execution) && compatibilityCheck(execution));
            if (!execution.isCanTest()) continue;

            Set<Integer> inputsAndDes = getInputAndDes(execution);
            Set<Integer> unmockableInputs = getUnmockableInputs(execution);
            exeToInputVarsMap.put(execution.getID(), inputsAndDes);
            exeToUnmockableInputVarMap.put(execution.getID(), unmockableInputs);
            if (execution.getCalleeId() != -1 && execution.getCallee() instanceof ObjVarDetails) {
                inputsAndDes.addAll(getParentExeStack(execution.getCallee(), true).stream().map(e -> exeToInputVarsMap.getOrDefault(e.getID(), new HashSet<>())).flatMap(Collection::stream).collect(Collectors.toSet())); // may change to accumulative putting to Map if it takes LONG
                unmockableInputs.addAll(getParentExeStack(execution.getCallee(), true).stream().map(e -> exeToUnmockableInputVarMap.getOrDefault(e.getID(), new HashSet<>())).flatMap(Collection::stream).collect(Collectors.toSet())); // may change to accumulative putting to Map if it takes LONG
            }
            execution.setCanTest(IntStream.range(0, execution.getParams().size()).noneMatch(pID -> {
                VarDetail p = this.getVarDetailByID(execution.getParams().get(pID));
                Class<?> paramDeclaredType = sootTypeToClass(execution.getMethodInvoked().getParameterTypes().get(pID));
                return isUnmockableParam(execution, paramDeclaredType, p);
            }) && !hasUnmockableUsage(execution, inputsAndDes, unmockableInputs));

            if(!execution.getRequiredPackage().isEmpty() && !execution.getRequiredPackage().startsWith(Properties.getSingleton().getPUT()))
                execution.setCanTest(false);
            logger.debug(executionQueue.size() +" checks remaining");
        }
    }

    private boolean hasUsageAsCallee(MethodExecution execution, Set<Integer> vars) {
        return getChildren(execution.getID()).stream()
                .map(this::getMethodExecutionByID)
                .anyMatch(e -> vars.contains(e.getCalleeId()) || hasUsageAsCallee(e, vars));
    }
    private boolean hasUnmockableUsage(MethodExecution execution, Set<Integer> allInputVars, Set<Integer> allUnmockableVars) {
        return hasUnmockableUsage(execution, allInputVars, allUnmockableVars, new HashSet<>());
    }

    /**
     * Check if there exist unmockable usage of vars specified in the provided execution
     *
     * @param execution Method Execution under investigation
     * @param allInputVars      VarDetail IDs to check
     * @return if there is unmockable usages of vars in execution
     */
    private boolean hasUnmockableUsage(MethodExecution execution, Set<Integer> allInputVars, Set<Integer> allUnmockableVars, Set<MethodExecution> covered) {
        logger.debug("Checking has unmockable usages " + execution.toSimpleString());
        if(covered.contains(execution)) return false;
        covered.add(execution);
        return getChildren(execution.getID()).stream()
                .map(this::getMethodExecutionByID)
                .anyMatch(e -> {
                    MethodDetails methodDetails = e.getMethodInvoked();
                    if (methodDetails.isFieldAccess() && allInputVars.contains(e.getCalleeId()))
                        return true;
                    if(allInputVars.contains(e.getCalleeId()) && IntStream.range(0, e.getParams().size()).anyMatch(pID -> {
                        VarDetail p = this.getVarDetailByID(e.getParams().get(pID));
                        Class<?> pDeclaredType = sootTypeToClass(e.getMethodInvoked().getParameterTypes().get(pID));
                        return isUnmockableParam(execution, pDeclaredType, p);
                    }))
                        return true;

                    if(allInputVars.contains(e.getCalleeId())) {
                        try {
                            Method toMock = methodDetails.getdClass().getDeclaredMethod(methodDetails.getName(), methodDetails.getParameterTypes().stream().map(Helper::sootTypeToClass).toArray(Class<?>[]::new));

                            if(methodDetails.getName().equals("equals") || methodDetails.getName().equals("hashCode") || methodDetails.getName().equals("getClass"))
                                return true;
                            if(e.getReturnValId()!=-1) {
                                VarDetail returnVal = this.getVarDetailByID(e.getReturnValId());
                                if(!canProvideReturnVal(execution, returnVal)) return true;
                            }
                        } catch (NoSuchMethodException ignored) {
                        }
                    }
                    if(allUnmockableVars.contains(e.getCalleeId())) return true;
                    if (InstrumentResult.getSingleton().isLibMethod(methodDetails.getId()) && e.getParams().stream().anyMatch(allInputVars::contains))
                        return true;

                    return hasUnmockableUsage(e, allInputVars, allUnmockableVars, covered);
                });
    }

    private boolean canProvideReturnVal(MethodExecution execution, VarDetail returnVal) {
        if (returnVal instanceof EnumVarDetails && returnVal.getType().equals(Class.class)) {
            String neededPackage = null;
            try {
                neededPackage = getRequiredPackage(ClassUtils.getClass(((EnumVarDetails) returnVal).getValue()));
            } catch (ClassNotFoundException ignored) {
            }
            if(neededPackage == null || (!neededPackage.isEmpty() && !execution.getRequiredPackage().equals(neededPackage))) return false;
            execution.setRequiredPackage(neededPackage);
        }
        Class<?> declaredReturnType = execution.getReturnValId() == -1 ? null : execution.getMethodInvoked().getReturnType();
        if(returnVal instanceof ObjVarDetails && !returnVal.equals(nullVar) && ((declaredReturnType == null && getRequiredPackage(returnVal.getType()) == null) || (declaredReturnType!=null && !declaredReturnType.isAssignableFrom(getAccessibleSuperType(returnVal.getType(), execution.getRequiredPackage()))))) return false;
        if(returnVal instanceof ArrVarDetails) return ((ArrVarDetails) returnVal).getComponents().stream().map(this::getVarDetailByID).allMatch(c -> canProvideReturnVal(execution, c));
        if(returnVal instanceof MapVarDetails) return ((MapVarDetails) returnVal).getKeyValuePairs().stream().flatMap(c-> Stream.of(c.getKey(), c.getValue())).map(this::getVarDetailByID).allMatch(c-> canProvideReturnVal(execution, c));
        return true;
    }
    private boolean isUnmockableParam(MethodExecution execution, Class<?> paramDeclaredType, VarDetail p) {
        if(p instanceof ObjVarDetails && !p.equals(this.getNullVar()) && ((paramDeclaredType == null && getRequiredPackage(p.getType()) == null) || (paramDeclaredType!=null && !paramDeclaredType.isAssignableFrom(getAccessibleSuperType(p.getType(), execution.getRequiredPackage()))))) return true;
        if(p instanceof ObjVarDetails && p.getID()!=execution.getCalleeId() && !execution.getParams().contains(p.getID()) && !p.equals(nullVar)) return true;
        if(p instanceof ObjVarDetails && !p.equals(this.getNullVar()) && Helper.isCannotMockType(p.getType()) && hasUsageAsCallee(execution, Collections.singleton(p.getID()))) {
            Stack<MethodExecution> parentStack = getParentExeStack(p, true);
            if(parentStack == null || parentStack.stream().anyMatch(e -> e.getMethodInvoked().getDeclaringClass().getPackageName().startsWith(Properties.getSingleton().getPUT()))) {
                return true;
            }
        }
        if(p instanceof ArrVarDetails) return ((ArrVarDetails) p).getComponents().stream().map(this::getVarDetailByID).anyMatch(c -> isUnmockableParam(execution, paramDeclaredType == null ? null : paramDeclaredType.getComponentType(), c));
        if(p instanceof MapVarDetails) return ((MapVarDetails) p).getKeyValuePairs().stream().flatMap(c-> Stream.of(c.getKey(), c.getValue())).map(this::getVarDetailByID).anyMatch(c-> isUnmockableParam(execution, null, c));
        if(p instanceof EnumVarDetails && p.getType().equals(Class.class)) {
            try {
                String requiredPackage = getRequiredPackage(ClassUtils.getClass(((EnumVarDetails) p).getValue()));
                if(requiredPackage == null || (!execution.getRequiredPackage().isEmpty() && !execution.getRequiredPackage().equals(requiredPackage)) ) return true;
                else execution.setRequiredPackage(requiredPackage);
            } catch (ClassNotFoundException ignored) {

            }
        }
        return false;
    }
//    private boolean isUnmockableParam(MethodExecution execution, VarDetail p) {
//        if(p instanceof ObjVarDetails && (p.getType().isAnonymousClass() || p.getTypeSimpleName().startsWith("$"))) return true;
//        if(p instanceof ObjVarDetails && p.getID()!=execution.getCalleeId() && !execution.getParams().contains(p.getID()) && !p.equals(nullVar)) return true;
//        if(p instanceof ArrVarDetails) return ((ArrVarDetails) p).getComponents().stream().map(this::getVarDetailByID).anyMatch(c -> isUnmockableParam(execution, c));
//        if(p instanceof MapVarDetails) return ((MapVarDetails) p).getKeyValuePairs().stream().flatMap(c-> Stream.of(c.getKey(), c.getValue())).map(this::getVarDetailByID).anyMatch(c-> isUnmockableParam(execution, c));
//        if(p instanceof EnumVarDetails && p.getType().equals(Class.class)) {
//            try {
//                String requiredPackage = getRequiredPackage(ClassUtils.getClass(((EnumVarDetails) p).getValue()));
//                if(requiredPackage == null || (!execution.getRequiredPackage().isEmpty() && !execution.getRequiredPackage().equals(requiredPackage)) ) return true;
//                else execution.setRequiredPackage(requiredPackage);
//            } catch (ClassNotFoundException ignored) {
//
//            }
//        }
//        return false;
//    }
//
    /**
     * Get stack of method executions needed to create the var specified
     *
     * @param var Variable to create
     * @param cache
     * @return stack of method executions for creation
     */
    public Stack<MethodExecution> getParentExeStack(VarDetail var, boolean cache) {
        Stack<MethodExecution> executionStack = new Stack<>();
        if (!(var instanceof ObjVarDetails)) return null;
        if(varToParentStackCache.containsKey(var)) return varToParentStackCache.get(var);
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
        if(cache)
            varToParentStackCache.put(var, executionStack);
        return executionStack;
    }

    public boolean containsFaultyDef(MethodExecution execution, boolean useCache, HashSet<Integer> processed) {
        InstrumentResult instrumentResult = InstrumentResult.getSingleton();
        MethodDetails details = execution.getMethodInvoked();
        if(useCache && exeToFaultyExeContainedCache.containsKey(execution)) return exeToFaultyExeContainedCache.get(execution);
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
     * @param exeID id of MethodExecution under check
     * @param useCache
     * @return if the execution provided is / runs faulty methods
     */
    private boolean containsFaultyDef(int exeID, boolean useCache, HashSet<Integer> processed) {
        return containsFaultyDef(getMethodExecutionByID(exeID), useCache, processed);
    }


    /**
     * @param execution MethodExecution under check
     * @return if the execution can be tested based on requirements on the execution itself
     */
    private boolean canTestExecution(MethodExecution execution) {
        MethodDetails methodDetails = execution.getMethodInvoked();
        logger.debug("Checking can test execution " + execution.toSimpleString());
        if (containsFaultyDef(execution, true) || getAllMethodExecs().values().stream().anyMatch(e -> e.sameCalleeParamNMethod(execution) && !e.sameContent(execution)))
            return false;
        if (methodDetails.getAccess().equals(ACCESS.PRIVATE) || methodDetails.getName().startsWith("access$"))
            return false;
        switch (methodDetails.getType()) {
            case STATIC_INITIALIZER:
                return false;
            case CONSTRUCTOR:
                if (methodDetails.getDeclaringClass().isAbstract()) return false;
            case STATIC:
                String neededPackage = getRequiredPackage(methodDetails.getdClass());
                if (neededPackage == null) return false;
                execution.setRequiredPackage(neededPackage);
        }
        if(methodDetails.getAccess().equals(ACCESS.PROTECTED)) {
            if(execution.getRequiredPackage().isEmpty())
                execution.setRequiredPackage(methodDetails.getDeclaringClass().getPackageName());
            else if(!execution.getRequiredPackage().equals(methodDetails.getDeclaringClass().getPackageName()))
                return false;
        }
        return true;
    }

    /**
     * @param execution MethodExecution under check
     * @return if the execution can be tested based on its callee requirement
     */
    private boolean canTestCallee(MethodExecution execution) {
        if (execution.getCalleeId() == -1) return true;
        logger.debug("Checking can test callee " + execution.getCallee().getID() + "\t " + execution.getCallee().getType().getName());
        if (execution.getCallee() instanceof ObjVarDetails) {
            if(execution.getCallee().getType().isAnonymousClass()) return false;
            Stack<MethodExecution> parentStack = getParentExeStack(execution.getCallee(), true);
            if (parentStack == null || parentStack.contains(execution) || !parentStack.stream().allMatch(MethodExecution::isCanTest))
                return false;
        }
        if (execution.getCallee() instanceof EnumVarDetails) {
            EnumVarDetails callee = (EnumVarDetails) execution.getCallee();
            // is field
            if (!callee.getType().isEnum()) {
                try {
                    if (!callee.getType().getPackage().getName().startsWith(Properties.getSingleton().getPUT()) && !Modifier.isPublic(callee.getType().getField(callee.getValue()).getModifiers()))
                        return false;
                } catch (NoSuchFieldException e) {
                    return false;
                }
                if (callee.getType().getPackage().getName().startsWith(Properties.getSingleton().getPUT()) && !InstrumentResult.getSingleton().getClassPublicFieldsMap().getOrDefault(callee.getType().getName(), new HashSet<>()).contains(callee.getValue()))
                    return false;
            }
            execution.setRequiredPackage(getRequiredPackage(callee.getType()));
            return execution.getRequiredPackage() != null && compatibilityCheck(execution, execution.getRequiredPackage());
        }
        return true;
    }

    /**
     * @param execution MethodExecution under check
     * @return if the package required (if any) by its callee (if any) is compatible with that of the execution
     */
    private boolean compatibilityCheck(MethodExecution execution) {
        logger.debug("checking compatibility for " + execution.toSimpleString());
        if (execution.getCalleeId() == -1 || !(execution.getCallee() instanceof ObjVarDetails)) return true;
        if (getDefExeList(execution.getCalleeId()) == null) return false;
        MethodExecution calleeDef = getMethodExecutionByID(getDefExeList(execution.getCalleeId()));
        ObjVarDetails callee = (ObjVarDetails) execution.getCallee();
        if (calleeDef.getRequiredPackage().isEmpty() || calleeDef.getRequiredPackage().equals(callee.getType().getPackage().getName()))
            return true;
        if (!compatibilityCheck(execution, calleeDef.getRequiredPackage()))
            return false;
        execution.setRequiredPackage(calleeDef.getRequiredPackage());
        return true;
    }

    /**
     * @param execution       MethodExecution under check
     * @param requiredPackage package required
     * @return if the package required is compatible with that of the execution
     */
    private boolean compatibilityCheck(MethodExecution execution, String requiredPackage) {
        if (execution.getCallee().getType().getPackage().getName().equals(requiredPackage) || requiredPackage.isEmpty())
            return true;
        MethodDetails details = execution.getMethodInvoked();
        List<Class<?>> superClasses = ClassUtils.getAllSuperclasses(execution.getCallee().getType());
        superClasses = superClasses.subList(0, superClasses.indexOf(details.getdClass()) + 1);
        return superClasses.stream().anyMatch(c -> accessibilityCheck(c, requiredPackage));
    }

    /**
     * Get inputs and their descendants in a method execution
     *
     * @param execution method execution under review
     * @return set of ids of vardetails being used as inputs or descendants of inputs in the execution
     */
    private Set<Integer> getInputAndDes(MethodExecution execution) {
        Set<Integer> inputsAndDes = execution.getParams().stream()
                .map(this::getVarDetailByID)
                .map(this::getRelatedObjVarIDs)
                .flatMap(Collection::stream)
                .map(this::getVarDetailByID)
                .filter(p -> !Helper.isCannotMockType(p.getType())) // do not track the var if they are NOT to be mocked
                .map(VarDetail::getID)
                .collect(Collectors.toSet());

        return getDes(execution, new HashSet<>(inputsAndDes));
    }

    private Set<Integer> getUnmockableInputs(MethodExecution execution) {
        return  execution.getParams().stream()
                .map(this::getVarDetailByID)
                .map(this::getRelatedObjVarIDs)
                .flatMap(Collection::stream)
                .map(this::getVarDetailByID)
                .filter(p -> !p.equals(nullVar))
                .filter(p -> Helper.isCannotMockType(p.getType()) && (getParentExeStack(p, true) == null || getParentExeStack(p, true).stream().anyMatch(e -> e.getMethodInvoked().getDeclaringClass().getPackageName().startsWith(Properties.getSingleton().getPUT())))) // do not track the var if they are NOT to be mocked
                .map(VarDetail::getID)
                .collect(Collectors.toSet());
    }

    /**
     * Get descendants of provided inputs in the provided execution
     * dfs approach
     *
     * @param execution    Method execution under review
     * @param inputsAndDes ids of Set of inputs to investigate
     * @return Set of ids of vardetails found in execution matching criteria
     */
    private Set<Integer> getDes(MethodExecution execution, Set<Integer> inputsAndDes) {
        return getDes(execution, inputsAndDes, new HashSet<>());
    }
    /**
     * Get descendants of provided inputs in the provided execution
     * dfs approach
     *
     * @param execution    Method execution under review
     * @param inputsAndDes ids of Set of inputs to investigate
     * @return Set of ids of vardetails found in execution matching criteria
     */
    private Set<Integer> getDes(MethodExecution execution, Set<Integer> inputsAndDes, Set<MethodExecution> covered) {
        if (inputsAndDes.size() == 0 || covered.contains(execution)) return inputsAndDes;
        covered.add(execution);
        getChildren(execution.getID())
                .forEach(cID -> {
                    MethodExecution c = getMethodExecutionByID(cID);
                    if (c.getCalleeId() != -1 && inputsAndDes.contains(c.getCalleeId())) { // only consider callee, if it is param, it either would call sth inside that makes impact / its lib method that would be considered invalid later
                        inputsAndDes.addAll(getRelatedObjVarIDs(c.getResultThisId()));
                        inputsAndDes.addAll(getRelatedObjVarIDs(c.getReturnValId()));
                    }
                    inputsAndDes.addAll(getDes(c, inputsAndDes, covered));
                });
        return inputsAndDes;
    }

    /**
     * Get Object Vardetail IDs relating to input
     *
     * @param varDetailID varDetail to investigate
     * @return Set of ids matching criteria
     */
    private Set<Integer> getRelatedObjVarIDs(int varDetailID) {
        if (varDetailID == -1) return new HashSet<>();
        return getRelatedObjVarIDs(getVarDetailByID(varDetailID));
    }

    /**
     * Get Object Vardetail IDs relating to input
     *
     * @param varDetail varDetail to investigate
     * @return Set of ids matching criteria
     */
    private Set<Integer> getRelatedObjVarIDs(VarDetail varDetail) {
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
}
