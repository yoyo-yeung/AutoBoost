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
    private static final ExecutionTrace singleton = new ExecutionTrace();
    private static final VarDetail nullVar;

    static {
        nullVar = new ObjVarDetails(0, Object.class, "null");
    }

    private final Map<Integer, MethodExecution> allMethodExecs;
    private final Map<Integer, VarDetail> allVars; // store all vardetail used, needed for lookups
    private final Map<Integer, Integer> varToDefMap;
    private final DirectedMultigraph<Integer, CallOrderEdge> callGraph;
    private final Map<VarDetail, Stack<MethodExecution>> varToParentStackCache = new HashMap<>(); // cache last retrieval results to save time
    private final Map<MethodExecution, Boolean> exeToFaultyExeContainedCache = new HashMap<>(); // cache to save execution time

    /**
     * Constructor of ExecutionTrace, set up all vars.
     */
    public ExecutionTrace() {
        this.allMethodExecs = new ConcurrentHashMap<>();
        this.allVars = new ConcurrentHashMap<>();
        this.varToDefMap = new HashMap<Integer, Integer>();
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

    public VarDetail getVarDetail(MethodExecution execution, Class<?> type, Object objValue, LOG_ITEM process, boolean canOnlyBeUse) {
        return getVarDetail(execution, type, objValue, process, canOnlyBeUse, new HashSet<Integer>());
    }

    /**
     * If object already stored, return existing VarDetail ID stored
     * If not, create a new VarDetail and return the corresponding ID
     *
     * @param execution
     * @param type          type of the object to be stored
     * @param objValue      object to be stored
     * @param process       the current process, e.g. logging callee? param? or return value?
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
        } else if (!type.equals(String.class)) processedHash.add(System.identityHashCode(objValue));
        Class<?> varDetailClass;
        Object checkVal = objValue;
        if (type.isEnum() || artificialEnum) varDetailClass = EnumVarDetails.class;
        else if (type.isPrimitive()) varDetailClass = PrimitiveVarDetails.class;
        else if (type.equals(String.class)) varDetailClass = StringVarDetails.class;
        else if (StringBVarDetails.availableTypeCheck(type)) {
            varDetailClass = StringBVarDetails.class;
            checkVal = getVarDetail(execution, String.class, objValue.toString(), process, true, processedHash).getID();
        } else if (ArrVarDetails.availableTypeCheck(type) && ArrVarDetails.availableTypeCheck(objValue.getClass())) {
            varDetailClass = ArrVarDetails.class;
            checkVal = getComponentStream(execution, type, objValue, process, canOnlyBeUse, processedHash).collect(Collectors.toList());
        } else if (MapVarDetails.availableTypeCheck(type) && MapVarDetails.availableTypeCheck(objValue.getClass())) {
            varDetailClass = MapVarDetails.class;
            checkVal = ((Map<?, ?>) objValue).entrySet().stream()
                    .filter(e -> !processedHash.contains(System.identityHashCode(e.getKey())) && !processedHash.contains(System.identityHashCode(e.getValue())))
                    .map(e -> new AbstractMap.SimpleEntry<Integer, Integer>(getVarDetail(execution, getClassOfObj(e.getKey()), e.getKey(), process, true, processedHash).getID(), getVarDetail(execution, getClassOfObj(e.getValue()), e.getValue(), process, true, processedHash).getID()))
                    .collect(Collectors.<Map.Entry<Integer, Integer>>toSet());
        } else if (ClassUtils.isPrimitiveWrapper(type)) {
            varDetailClass = WrapperVarDetails.class;
        } else {
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
                varDetail = new StringBVarDetails(getNewVarID(), type, (Integer) checkVal);
            else if (varDetailClass.equals(ArrVarDetails.class)) {
                varDetail = new ArrVarDetails(getNewVarID(), (List<Integer>) checkVal, objValue);
            } else if (varDetailClass.equals(MapVarDetails.class)) {
                varDetail = new MapVarDetails(getNewVarID(), (Class<? extends Map>) type, (Set<Map.Entry<Integer, Integer>>) checkVal, objValue);
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
                .map(e -> new AbstractMap.SimpleEntry<MethodExecution, Integer>(e,
                        (e.getMethodInvoked().getType().equals(METHOD_TYPE.MEMBER) && e.getResultThisId() == varDetail.getID() ? -1000 : 0) +
                                e.getMethodInvoked().getParameterCount() +
                                e.getParams().stream().map(this::getVarDetailByID).filter(p -> p.getType().isAnonymousClass()).mapToInt(i -> 1000).sum() +
                                (InstrumentResult.getSingleton().isLibMethod(e.getMethodInvoked().getId()) ? 999999 : 0) +
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
     * @param type          the type of variable (Array/subclasses of Collection/etc)
     * @param obj           the variable
     * @param process       the current logging step
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
            componentStream = IntStream.range(0, trimmedLength).filter(i -> !processedHash.contains(System.identityHashCode(Helper.getArrayElement(obj, i)))).mapToObj(i -> getVarDetail(execution, type.getComponentType(), Helper.getArrayElement(obj, i), process, canOnlyBeUse, processedHash).getID());
        } else if (Set.class.isAssignableFrom(type) && obj instanceof Set)
            componentStream = ((Set) obj).stream().filter(v -> !processedHash.contains(System.identityHashCode(v))).map(v -> getVarDetail(execution, getClassOfObj(v), v, process, true, processedHash).getID()).sorted(Comparator.comparingInt(x -> (int) x));
        else
            componentStream = ((Collection) obj).stream().filter(v -> !processedHash.contains(System.identityHashCode(v))).map(v -> getVarDetail(execution, getClassOfObj(v), v, process, canOnlyBeUse, processedHash).getID());
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
        } else if (varDetailClass.equals(ArrVarDetails.class)) {
            objValue = ((List) objValue).stream().map(String::valueOf).collect(Collectors.joining(Properties.getDELIMITER()));
        } else if (varDetailClass.equals(MapVarDetails.class)) {
            objValue = ((Set<Map.Entry>) objValue).stream().map(e -> e.getKey() + "=" + e.getValue()).sorted().collect(Collectors.joining(Properties.getDELIMITER()));
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
                .filter(v -> v.sameTypeNValue(type, finalObjValue1))
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
        InstrumentResult instrumentResult = InstrumentResult.getSingleton();
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
