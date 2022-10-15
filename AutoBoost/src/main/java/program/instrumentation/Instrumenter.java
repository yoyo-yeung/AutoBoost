package program.instrumentation;

import entity.METHOD_TYPE;
import helper.Properties;
import org.apache.commons.lang3.ClassUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import program.analysis.MethodDetails;
import soot.*;
import soot.javaToJimple.LocalGenerator;
import soot.jimple.*;
import soot.jimple.internal.JIdentityStmt;
import soot.tagkit.AttributeValueException;
import soot.tagkit.Tag;
import soot.toolkits.graph.CompleteUnitGraph;
import soot.toolkits.scalar.SimpleLocalDefs;
import soot.toolkits.scalar.SimpleLocalUses;
import soot.toolkits.scalar.UnitValueBoxPair;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static helper.Helper.isCannotMockType;

public class Instrumenter extends BodyTransformer {
    private static final Logger logger = LogManager.getLogger(Instrumenter.class);
    // for information storing and retrieving
    private static final InstrumentResult instrumentResult = InstrumentResult.getSingleton();
    private static final Properties properties = Properties.getSingleton();

    // for statements adding to target program
    private static final SootClass loggerClass;
    private static final SootMethod logStartMethod;
    private static final SootMethod logEndMethod;
    private static final SootMethod logExceptionMethod;
    private static final SootMethod logFieldAccessMethod;
    private static final SootMethodRef getCurrentThreadmRef;
    private static final SootMethodRef getTIDmRef;
    private static final SootClass threadClass;

    private static final Set<Type> notMockingTypeSet = new HashSet<>();
    private static final Map<Class<?>, Set<String>> safeJavaLibMethodMap = new HashMap<>(); // adding list of java lib methods that allow using method inputs as inputs, without declaring calling method as unmockable

    static {
        loggerClass = Scene.v().loadClassAndSupport("program.execution.ExecutionLogger");
        logStartMethod = loggerClass.getMethod("int logStart(int,java.lang.Object,java.lang.Object,long)");
        logEndMethod = loggerClass.getMethod("void logEnd(int,java.lang.Object,java.lang.Object,long)");
        logExceptionMethod = loggerClass.getMethod("void logException(java.lang.Object,long)");
        logFieldAccessMethod = loggerClass.getMethod("void logFieldAccess(int,java.lang.Object,java.lang.Object,long)");
        threadClass = Scene.v().loadClassAndSupport(Thread.class.getName());
        getCurrentThreadmRef = Scene.v().makeMethodRef(threadClass, "currentThread", new ArrayList<>(), RefType.v(Thread.class.getName()), true);
        getTIDmRef = Scene.v().makeMethodRef(threadClass, "getId", new ArrayList<>(), LongType.v(), false);
    }

    static {
        for (PrimType t : new PrimType[]{BooleanType.v(), ByteType.v(), CharType.v(), DoubleType.v(), FloatType.v(), IntType.v(), LongType.v(), ShortType.v()})
            notMockingTypeSet.add(t.boxedType());
        notMockingTypeSet.add(RefType.v(String.class.getName()));
        notMockingTypeSet.add(RefType.v(StringBuilder.class.getName()));
        notMockingTypeSet.add(RefType.v(StringBuffer.class.getName()));

        safeJavaLibMethodMap.put(Collection.class, new HashSet<String>() {{
            String[] methodNames = new String[]{"add", "addAll", "contains", "containsAll", "indexOf", "lastIndexOf", "remove", "removeAll", "retainAll", "set", "replaceAll"};
            addAll(Arrays.asList(methodNames));
        }}); // as they do not access fields of inputs (known)
        safeJavaLibMethodMap.put(Map.class, new HashSet<String>() {{
            String[] methodNames = new String[]{"containsKey", "containsValue", "get", "getOrDefault", "put", "putAll", "putIfAbsent", "replace", "remove"};
            addAll(Arrays.asList(methodNames));
        }});

    }

    private List<Unit> createArrForParams(LocalGenerator localGenerator, Local paramArrLocal, List<Type> paramTypes, List<?> params) {
        List<Unit> toInsert = new ArrayList<>();
        if (params.size() == 0) return toInsert;
        Unit defArrUnit = Jimple.v().newAssignStmt(paramArrLocal, Jimple.v().newNewArrayExpr(RefType.v(Object.class.getName()), IntConstant.v(params.size())));
        toInsert.add(defArrUnit);
        IntStream.range(0, params.size()).forEach(i -> {
            Value paramLocal = (Value) params.get(i);
            // cast to object type
            if (paramTypes.get(i) instanceof PrimType) {
                Local castLocal = localGenerator.generateLocal(((PrimType) paramTypes.get(i)).boxedType());
                SootMethodRef ref = Scene.v().makeMethodRef(((PrimType) paramTypes.get(i)).boxedType().getSootClass(), "valueOf",
                        Collections.singletonList(paramTypes.get(i)), castLocal.getType(), true);
                Unit castUnit = Jimple.v().newAssignStmt(castLocal, Jimple.v().newStaticInvokeExpr(ref, paramLocal));
                toInsert.add(castUnit);
                toInsert.add(Jimple.v().newAssignStmt(Jimple.v().newArrayRef(paramArrLocal, IntConstant.v(i)), castLocal));
            } else
                toInsert.add(Jimple.v().newAssignStmt(Jimple.v().newArrayRef(paramArrLocal, IntConstant.v(i)), paramLocal));
        });
        toInsert.forEach(t -> t.addTag(CUSTOM_TAGS.NEWLY_ADDED_TAG.getTag()));

        return toInsert;

    }

    @Override
    protected void internalTransform(Body body, String phaseName, Map<String, String> options) {
        // for iterating statements in the original program
        PatchingChain<Unit> units = body.getUnits();
        Iterator<?> stmtIt = units.snapshotIterator();

        if (!body.getMethod().getDeclaringClass().getPackageName().startsWith(properties.getPUT())) return;
        // method info
        SootMethod currentSootMethod = body.getMethod();
        if (currentSootMethod.getName().contains("$") || currentSootMethod.isStaticInitializer())
            return; // if name contains $, not written manually
        SootClass currentDeclaringClass = currentSootMethod.getDeclaringClass();
        instrumentResult.addClassPublicFields(currentDeclaringClass.getName(), currentDeclaringClass); // store set of public static fields before modifying them for storing at run time
        currentDeclaringClass.getFields()
                .forEach(f -> f.setModifiers(f.getModifiers() & ~Modifier.TRANSIENT & ~Modifier.PRIVATE & ~Modifier.PROTECTED | Modifier.PUBLIC)); // set all fields as non transient and public such that their value can be stored during runtime
        MethodDetails methodDetails = new MethodDetails(currentSootMethod);

        // store ID of faulty methods
        if (properties.getFaultyFunc().contains(currentSootMethod.getSignature()))
            properties.addFaultyFuncId(methodDetails.getId());

        SimpleLocalDefs localDefs = new SimpleLocalDefs(new CompleteUnitGraph(body));
        SimpleLocalUses localUses = new SimpleLocalUses(body, localDefs);
        // Prepare for statements adding
        LocalGenerator localGenerator = new LocalGenerator(currentSootMethod.getActiveBody());
        boolean paramLogged = false, threadRetrieved = false;
        Value exeIDLocal = localGenerator.generateLocal(IntType.v()), threadIDLocal = localGenerator.generateLocal(LongType.v());
        Local threadClassLocal = localGenerator.generateLocal(threadClass.getType());
        Comparator<Unit> unitComparator = (o1, o2) -> o1.equals(o2) ? 0 : 1;
        Queue<DefinitionStmt> inputUnits = new PriorityQueue<>(unitComparator); // for tracking input arguments and this instance separately
        while (stmtIt.hasNext()) {
            Stmt stmt = (Stmt) stmtIt.next();
            if (stmt instanceof JIdentityStmt) {
                if (((JIdentityStmt) stmt).getRightOp() instanceof ParameterRef && needTrackVar(((JIdentityStmt) stmt).getLeftOp().getType()))
                    inputUnits.add((DefinitionStmt) stmt);

                if (((JIdentityStmt) stmt).getRightOp() instanceof ThisRef) {
                    addTagForDangerousCallsToLog(localUses, getFieldRefsOfUnit(localUses, stmt, new PriorityQueue<>(unitComparator)));
                }
                continue;
            }
            // set thread id
            if (!threadRetrieved) {
                getThreadIDRetrievalStmts(threadClassLocal, threadIDLocal)
                        .forEach(s -> units.insertBeforeNoRedirect(s, stmt));
                threadRetrieved = true;
            }
            // log start of method call
            if (!paramLogged) {
                addTagForDangerousCallsToLog(localUses, inputUnits);
                getLogStartStmts(currentSootMethod, localGenerator, methodDetails, threadIDLocal, exeIDLocal, methodDetails.getType().equals(METHOD_TYPE.MEMBER) ? currentSootMethod.getActiveBody().getThisLocal() : NullConstant.v(), currentSootMethod.getActiveBody().getParameterLocals())
                        .forEach(s -> units.insertBeforeNoRedirect(s, stmt));
                paramLogged = true;
            }
            // log exception
            if (stmt instanceof ThrowStmt)
                getLogThrowExcStmts(((ThrowStmt) stmt).getOp(), threadIDLocal)
                        .forEach(s -> units.insertBefore(s, stmt));
            // log normal return of methods
            if (stmt instanceof ReturnStmt || stmt instanceof ReturnVoidStmt)
                getLogEndStmts(currentSootMethod, localGenerator, methodDetails, threadIDLocal, exeIDLocal, methodDetails.getType().equals(METHOD_TYPE.CONSTRUCTOR) || methodDetails.getType().equals(METHOD_TYPE.MEMBER) ? currentSootMethod.getActiveBody().getThisLocal() : NullConstant.v(), stmt)
                        .forEach(s -> units.insertBefore(s, stmt));
            // log method invoke if they were marked
            if (stmt.containsInvokeExpr() && (stmt.hasTag(CUSTOM_TAGS.INV_TO_LOG_TAG.getTag().getName()) || stmt.hasTag(CUSTOM_TAGS.DAN_LIB_CALL_TO_LOG_TAG.getTag().getName()))) {
                InvokeExpr invokeExpr = stmt.getInvokeExpr();
                MethodDetails invokedMethodDetails = instrumentResult.findExistingLibMethod(invokeExpr.getMethod().getSignature());
                if (invokedMethodDetails == null) {
                    invokedMethodDetails = new MethodDetails(invokeExpr.getMethod());
                    instrumentResult.addLibMethod(invokedMethodDetails);
                }
                Local invokedIDLocal = localGenerator.generateLocal(IntType.v());
                getLogStartStmts(invokeExpr.getMethod(), localGenerator, invokedMethodDetails, threadIDLocal, invokedIDLocal, invokedMethodDetails.getType().equals(METHOD_TYPE.MEMBER) && invokeExpr instanceof InstanceInvokeExpr ? ((InstanceInvokeExpr) invokeExpr).getBase() : NullConstant.v(), invokeExpr.getArgs()).forEach(s -> units.insertBefore(s, stmt));
//
                reverse(getLogInvEndStmts(invokeExpr.getMethod(), localGenerator, invokedMethodDetails, threadIDLocal, invokedIDLocal, (invokedMethodDetails.getType().equals(METHOD_TYPE.CONSTRUCTOR) || invokedMethodDetails.getType().equals(METHOD_TYPE.MEMBER)) && invokeExpr instanceof InstanceInvokeExpr ? ((InstanceInvokeExpr) invokeExpr).getBase() : NullConstant.v(), stmt)).forEach(s -> units.insertAfter(s, stmt));
            }

            // log field access if they were marked
            if (stmt.containsFieldRef() && stmt.getFieldRef() instanceof InstanceFieldRef && stmt instanceof DefinitionStmt && stmt.hasTag(CUSTOM_TAGS.DAN_FIELD_ACCESS_TO_LOG_TAG.getTag().getName())) {
                MethodDetails fieldAccessDetails = getFieldAccessingMethodDetails((InstanceFieldRef) stmt.getFieldRef());
                reverse(getLogFieldAccessStmts((InstanceFieldRef) stmt.getFieldRef(), ((DefinitionStmt) stmt).getLeftOp(), localGenerator, fieldAccessDetails, threadIDLocal)).forEach(s -> units.insertAfter(s, stmt));
            }

//            if(stmt.containsInvokeExpr() && stmt.getInvokeExpr().getMethodRef().getDeclaringClass().getPackageName().startsWith(properties.getPUT()))
//                addLoggingToUnmockableParamCreation(localGenerator, currentSootMethod, localUses, localDefs, units, threadIDLocal, stmt, stmt.getInvokeExpr());
        }
        instrumentResult.addMethod(methodDetails);
    }

    private void addLoggingToUnmockableParamCreation(LocalGenerator localGenerator, SootMethod currentMethod, SimpleLocalUses localUses, SimpleLocalDefs localDefs, PatchingChain<Unit> units, Value threadIDLocal, Stmt stmt, InvokeExpr expr) {
        expr.getArgs().stream()
                .filter(a -> isCannotMockType(a.getType())).flatMap(a -> getAllStmtsToLogRelatedToDef(stmt, localUses, localDefs, a, new HashSet<>()).stream())
                .distinct()
                .filter(s -> !CUSTOM_TAGS.containsCustomTag(s))
                .forEach(s -> {
                    logger.debug(currentMethod + "\t" + expr);
                    if(s.hasTag(CUSTOM_TAGS.PARAM_TRACK_TO_LOG_TAG.name())) return;
                    if(s instanceof AssignStmt && ((AssignStmt) s).getRightOp() instanceof InstanceFieldRef && s.getFieldRef() instanceof InstanceFieldRef)
                        reverse(getLogFieldAccessStmts((InstanceFieldRef) s.getFieldRef(), ((AssignStmt) s).getLeftOp(), localGenerator, getFieldAccessingMethodDetails((InstanceFieldRef) s.getFieldRef()), threadIDLocal)).forEach(v -> units.insertAfter(v, s));
                    else if (s.containsInvokeExpr()) {
                        InvokeExpr invokeExpr = s.getInvokeExpr();
                        MethodDetails invoked = instrumentResult.findExistingLibMethod(invokeExpr.getMethod().getSignature());
                        if(invoked == null ) {
                            invoked = new MethodDetails(invokeExpr.getMethod());
                            instrumentResult.addLibMethod(invoked);
                        }
                        Local invokedIDLocal = localGenerator.generateLocal(IntType.v());
                        getLogStartStmts(invokeExpr.getMethod(), localGenerator, invoked, threadIDLocal, invokedIDLocal, invoked.getType().equals(METHOD_TYPE.MEMBER) && invokeExpr instanceof InstanceInvokeExpr ? ((InstanceInvokeExpr) invokeExpr).getBase() : NullConstant.v(), invokeExpr.getArgs()).forEach(v -> units.insertBefore(v, s));
//
                        reverse(getLogInvEndStmts(invokeExpr.getMethod(), localGenerator, invoked, threadIDLocal, invokedIDLocal, (invoked.getType().equals(METHOD_TYPE.CONSTRUCTOR) || invoked.getType().equals(METHOD_TYPE.MEMBER)) && invokeExpr instanceof InstanceInvokeExpr ? ((InstanceInvokeExpr) invokeExpr).getBase() : NullConstant.v(), s)).forEach(v -> units.insertAfter(v, s));
                    }
                    s.addTag(CUSTOM_TAGS.PARAM_TRACK_TO_LOG_TAG.getTag());
                });

    }

    private List<Stmt> getAllStmtsToLogRelatedToDef(Stmt currentStmt, SimpleLocalUses localUses, SimpleLocalDefs localDefs, Value local, HashSet<Value>checked) {
        List<Stmt> toLog = new ArrayList<>();
        if(checked.contains(local) || !(local instanceof Local)) return toLog;
        checked.add(local);
        localDefs.getDefsOf((Local) local).stream()
                .map(s -> (Stmt)s)
                .distinct()
                .filter(s -> !(s instanceof JIdentityStmt))
                .filter(s -> s instanceof AssignStmt) // just in case
                .map(s -> (AssignStmt)s)
                .filter(s -> !(s.getRightOp() instanceof InvokeExpr) || !s.getInvokeExpr().getMethodRef().getDeclaringClass().getPackageName().startsWith(properties.getPUT())) // either NOT invoking method / the invoked method is not one that would be instrumented in the first place
                .filter(s-> !s.getRightOp().getType().toQuotedString().replace("'","").startsWith(properties.getPUT())) // the assigned value should NOT be of PUT type as they can then be mocked
                .filter(s -> !(s.getRightOp() instanceof InstanceFieldRef && ((InstanceFieldRef) s.getFieldRef()).getBase().getType().toQuotedString().replace("'", "").startsWith(properties.getPUT()))) // avoid creation using PUT classes + methods
                .filter(s -> !(s.getRightOp() instanceof NewArrayExpr || s.getRightOp() instanceof Constant || s.getRightOp() instanceof StaticFieldRef || s.getRightOp() instanceof NewExpr))
                .forEach(s -> {
                    if(s.getRightOp() instanceof CastExpr || s.getRightOp() instanceof ArrayRef || s.getRightOp() instanceof Immediate) {
                        if(s.getRightOp() instanceof CastExpr)
                            toLog.addAll(getAllStmtsToLogRelatedToDef(s, localUses, localDefs, ((CastExpr) s.getRightOp()).getOpBox().getValue(), checked));
                        else if(s.getRightOp() instanceof ArrayRef)
                            toLog.addAll(getAllStmtsToLogRelatedToDef(s, localUses, localDefs, ((ArrayRef) s.getRightOp()).getBase(), checked));
                        else if(s.getRightOp() instanceof Immediate)
                            toLog.addAll(getAllStmtsToLogRelatedToDef(s, localUses, localDefs, s.getRightOp(), checked));
                    }
                    else {
                        if(s.getRightOp() instanceof InvokeExpr) {
                            if (s.getRightOp() instanceof InstanceInvokeExpr)
                                toLog.addAll(getAllStmtsToLogRelatedToDef(s, localUses, localDefs, ((InstanceInvokeExpr) s.getRightOp()).getBase(), checked));
                            toLog.addAll(((InvokeExpr) s.getRightOp()).getArgs().stream().filter(a -> isCannotMockType(a.getType())).flatMap(a -> getAllStmtsToLogRelatedToDef(s, localUses, localDefs, a, checked).stream()).collect(Collectors.toList()));
                        }
                        else if(s.getRightOp() instanceof InstanceFieldRef)
                            toLog.addAll(getAllStmtsToLogRelatedToDef(s, localUses, localDefs, ((InstanceFieldRef) s.getRightOp()).getBase(), checked));
                        toLog.add(s);
                    }

                    for (UnitValueBoxPair use : localUses.getUsesOf( s)) {
                        if (use.getUnit().equals(currentStmt)) {
                            break;
                        }
                        Stmt useStmt = (Stmt) use.getUnit();
                        if (!useStmt.containsInvokeExpr() || !(useStmt.getInvokeExpr() instanceof InstanceInvokeExpr) || !((InstanceInvokeExpr) useStmt.getInvokeExpr()).getBase().equals(use.getValueBox().getValue()))
                            continue;
                        toLog.add(useStmt);
                    }
                });

        return toLog;
    }

    private void addTagForDangerousCallsToLog(SimpleLocalUses localUses, Queue<DefinitionStmt> inputs) {
        Set<Unit> checked = new HashSet<>();
        while (!inputs.isEmpty()) {

            Unit u = inputs.poll();
            if (checked.contains(u)) continue;
            checked.add(u);
            List<UnitValueBoxPair> uses = localUses.getUsesOf(u);
            // add descendants to check list
            uses.stream()
                    .map(UnitValueBoxPair::getUnit)
                    .filter(c -> c instanceof DefinitionStmt)
                    .map(c -> (DefinitionStmt) c)
                    .filter(c -> needTrackVar(c.getLeftOp().getType())) // no need to keep track of primitive/string/wrapper value as we don't need to mock their behavior
                    .forEach(inputs::add);

            // cannot mock if fields of inputs are accessed directly
            // cannot mock if this.field is a mocked input + field of it is accessed directly
            Set<Unit> fieldAccessStream = uses.stream()
                    .filter(c -> ((Stmt) c.getUnit()).containsFieldRef())
                    .filter(c -> ((Stmt) c.getUnit()).getFieldRef() instanceof InstanceFieldRef)
                    .filter(c -> ((InstanceFieldRef) ((Stmt) c.getUnit()).getFieldRef()).getBase().equals(c.getValueBox().getValue()))  // if contains current unit .field
                    .map(UnitValueBoxPair::getUnit)
                    .filter(c -> !(c instanceof DefinitionStmt) || !((DefinitionStmt) c).getLeftOp().equals(((DefinitionStmt) c).getFieldRef())) // if it is being accessed, not defined
                    .collect(Collectors.toSet());

            if (fieldAccessStream.size() > 0)
//                if (logOnly)
                fieldAccessStream.forEach(c -> c.addTag(CUSTOM_TAGS.DAN_FIELD_ACCESS_TO_LOG_TAG.getTag()));
//                else return false;


            // cannot mock if the item is used as an input to un-logged methods (the method may call .field to get values)
            Set<Unit> methodInputStream = uses.stream()
                    .filter(c -> ((Stmt) c.getUnit()).containsInvokeExpr())
                    .filter(c -> ((Stmt) c.getUnit()).getInvokeExpr().getArgs().contains(c.getValueBox().getValue()))
                    .filter(s -> {
                        SootMethodRef c = ((Stmt) s.getUnit()).getInvokeExpr().getMethodRef();
                        try {
                            if (!c.getDeclaringClass().getPackageName().startsWith("java"))
                                return true; // move on to the next checking if not java package
                            Class<?> declaringClass = ClassUtils.getClass(c.getDeclaringClass().getName());
                            if (safeJavaLibMethodMap.entrySet().stream().anyMatch(e -> e.getKey().isAssignableFrom(declaringClass) && e.getValue().contains(c.getName())))
                                return false; // declared as can mock (statement specific) if the method called is excluded manually
                        } catch (ClassNotFoundException e) {
                            logger.error(e.getMessage());
                        }
                        return true;
                    })
                    .filter(s -> {
                        SootMethodRef c = ((Stmt) s.getUnit()).getInvokeExpr().getMethodRef();
                        return !(c.getDeclaringClass().getPackageName().startsWith(properties.getPUT()));
                    })
                    .map(UnitValueBoxPair::getUnit)
                    .collect(Collectors.toSet());
//            if (methodInputStream.size() > 0)
//                if (logOnly)
                    methodInputStream.forEach(c -> c.addTag(CUSTOM_TAGS.DAN_LIB_CALL_TO_LOG_TAG.getTag()));
//                else return false;

            // not sure if this is needed, may change to also check the other opr is constant and non-null?
            // cannot mock if the item is directly compared to values using !=/ ==  and etc.
        /*

            if(uses.stream()
                    .filter(c -> c.getUnit() instanceof IfStmt)
                    .anyMatch(c -> {
                        ConditionExpr conditionExpr = (ConditionExpr) ((IfStmt) c.getUnit()).getCondition();
                        Value value = c.getValueBox().getValue();
                        return conditionExpr.getOp1().equals(value) || conditionExpr.getOp2().equals(value) && !(conditionExpr.getOp1().equals(NullConstant.v()) || conditionExpr.getOp2().equals(NullConstant.v()));
                    }))
                return false;
        */

            // Log calling of lib methods if they are called by inputs / descendant for mocking
            // if void, use for tracking content of var (for tracing)
            // else, for mocking  + tracking
            uses.stream()
                    .filter(c -> ((Stmt) c.getUnit()).containsInvokeExpr() && ((Stmt) c.getUnit()).getInvokeExpr() instanceof InstanceInvokeExpr)
                    .filter(c -> ((InstanceInvokeExpr) ((Stmt) c.getUnit()).getInvokeExpr()).getBase().equals(c.getValueBox().getValue()))
                    .filter(c -> !((Stmt) c.getUnit()).getInvokeExpr().getMethodRef().getDeclaringClass().getPackageName().startsWith(properties.getPUT()))
                    .forEach(c -> c.getUnit().addTag(CUSTOM_TAGS.INV_TO_LOG_TAG.getTag()));


        }
    }

    /**
     * Use for getting this.field to track
     *
     * @param localUses localUses provided by soot
     * @param unit      this unit
     * @return a queue with fields to track
     */
    private Queue<DefinitionStmt> getFieldRefsOfUnit(SimpleLocalUses localUses, Unit unit, Queue<DefinitionStmt> output) {
        localUses.getUsesOf(unit).stream()
                .filter(u -> ((Stmt) u.getUnit()).containsFieldRef() && ((Stmt) u.getUnit()).getFieldRef() instanceof InstanceFieldRef)
                .filter(u -> ((InstanceFieldRef) ((Stmt) u.getUnit()).getFieldRef()).getBase().equals(u.getValueBox().getValue()))
                .filter(u -> u.getUnit() instanceof DefinitionStmt)
                .map(u -> (DefinitionStmt) u.getUnit())
                .filter(u -> u.getRightOpBox().equals(u.getFieldRefBox()))
                .filter(u -> needTrackVar(u.getFieldRef().getField().getType()))
                .forEach(output::add);
        return output;
    }

    private boolean needTrackVar(Type t) {
        if (t instanceof PrimType) return false;
        if (notMockingTypeSet.contains(t)) return false;
        if (t instanceof ArrayType) return needTrackVar(((ArrayType) t).getElementType());
        return true;
    }

    private List<Stmt> getThreadIDRetrievalStmts(Local threadClassLocal, Value threadIDLocal) {
        List<Stmt> toInsert = new ArrayList<>();
        toInsert.add(Jimple.v().newAssignStmt(threadClassLocal, Jimple.v().newStaticInvokeExpr(getCurrentThreadmRef)));
        toInsert.add(Jimple.v().newAssignStmt(threadIDLocal, Jimple.v().newVirtualInvokeExpr(threadClassLocal, getTIDmRef)));
        toInsert.forEach(t -> t.addTag(CUSTOM_TAGS.NEWLY_ADDED_TAG.getTag()));

        return toInsert;
    }

    private List<Unit> getParamLocalSetupStmts(SootMethod method, LocalGenerator localGenerator, Value paramLocal, List<Type> paramTypes, List<?> originalParamLocals) {
        List<Unit> toInsert = new ArrayList<>();
        if (method.getParameterCount() == 0)
            return toInsert;
        toInsert.addAll(createArrForParams(localGenerator, (Local) paramLocal, paramTypes, originalParamLocals));
        toInsert.forEach(t -> t.addTag(CUSTOM_TAGS.NEWLY_ADDED_TAG.getTag()));

        return toInsert;
    }

    private MethodDetails getFieldAccessingMethodDetails(InstanceFieldRef fieldRef) {
        SootClass declaringClass = fieldRef.getField().getDeclaringClass();
        String fieldName = fieldRef.getField().getName();
        MethodDetails result = instrumentResult.findExistingFieldAccessMethod(declaringClass.getName(), fieldName);
        if (result == null) {
            result = MethodDetails.getFieldAccessingMethodDetails(declaringClass, fieldName, fieldRef.getType());
            instrumentResult.addFieldAccessMethod(result);
        }
        return result;
    }

    private List<Unit> getLogFieldAccessStmts(InstanceFieldRef fieldRef, Value originalReturnVal, LocalGenerator localGenerator, MethodDetails methodDetails, Value threadIDLocal) {
        Value base = fieldRef.getBase();
        Value returnVal = fieldRef.getType() instanceof PrimType ? localGenerator.generateLocal(((PrimType) fieldRef.getType()).boxedType()) : originalReturnVal;
        List<Unit> toInsert = new ArrayList<>(getReturnLocalSetupStmt(localGenerator, fieldRef.getType(), returnVal, originalReturnVal));
        Expr logExpr = Jimple.v().newStaticInvokeExpr(logFieldAccessMethod.makeRef(), IntConstant.v(methodDetails.getId()), base, returnVal, threadIDLocal);
        toInsert.add(Jimple.v().newInvokeStmt(logExpr));
        toInsert.forEach(t -> t.addTag(CUSTOM_TAGS.NEWLY_ADDED_TAG.getTag()));
        return toInsert;
    }

    private List<Unit> getLogStartStmts(SootMethod method, LocalGenerator localGenerator, MethodDetails methodDetails, Value threadIDLocal, Value exeIDLocal, Value thisLocal, List<?> originalParamLocals) {
        Value paramLocal = method.getParameterCount() == 0 ? NullConstant.v() : localGenerator.generateLocal(ArrayType.v(RefType.v(Object.class.getName()), method.getParameterCount()));
        List<Unit> toInsert = new ArrayList<>(getParamLocalSetupStmts(method, localGenerator, paramLocal, method.getParameterTypes(), originalParamLocals));
        Expr logExpr = Jimple.v().newStaticInvokeExpr(logStartMethod.makeRef(), IntConstant.v(methodDetails.getId()), thisLocal, paramLocal, threadIDLocal);

        toInsert.add(Jimple.v().newAssignStmt(exeIDLocal, logExpr));
        toInsert.forEach(t -> t.addTag(CUSTOM_TAGS.NEWLY_ADDED_TAG.getTag()));

        return toInsert;
    }

    private List<Unit> getLogThrowExcStmts(Value throwing, Value threadIDLocal) {
        List<Unit> toInsert = new ArrayList<>();
        Expr logExpr = Jimple.v().newStaticInvokeExpr(logExceptionMethod.makeRef(), throwing, threadIDLocal);
        toInsert.add(Jimple.v().newInvokeStmt(logExpr));
        toInsert.forEach(t -> t.addTag(CUSTOM_TAGS.NEWLY_ADDED_TAG.getTag()));
        return toInsert;
    }

    private List<Unit> getReturnLocalSetupStmt(LocalGenerator localGenerator, Type returnType, Value returnLocal, Value originalReturnLocal) {
        List<Unit> toInsert = new ArrayList<>();
        if (!(returnType instanceof PrimType)) return toInsert;
        SootMethodRef ref = Scene.v().makeMethodRef(((PrimType) returnType).boxedType().getSootClass(), "valueOf",
                Collections.singletonList(returnType), returnLocal.getType(), true);
        toInsert.add(Jimple.v().newAssignStmt(returnLocal, Jimple.v().newStaticInvokeExpr(ref, originalReturnLocal)));
        toInsert.forEach(t -> t.addTag(CUSTOM_TAGS.NEWLY_ADDED_TAG.getTag()));
        return toInsert;
    }

    private List<Unit> getLogEndStmts(SootMethod method, LocalGenerator localGenerator, MethodDetails methodDetails, Value threadIDLocal, Value exeIDLocal, Value thisLocal, Stmt retStmt) {
        List<Unit> toInsert = new ArrayList<>();
        Value returnVal = NullConstant.v();
        if (retStmt instanceof ReturnStmt) {
            returnVal = (method.getReturnType() instanceof PrimType) ? localGenerator.generateLocal(((PrimType) method.getReturnType()).boxedType()) : ((ReturnStmt) retStmt).getOp();
            toInsert.addAll(getReturnLocalSetupStmt(localGenerator, method.getReturnType(), returnVal, ((ReturnStmt) retStmt).getOp()));
        }
        Expr logExpr = Jimple.v().newStaticInvokeExpr(logEndMethod.makeRef(), exeIDLocal, thisLocal, returnVal, threadIDLocal);
        toInsert.add(Jimple.v().newInvokeStmt(logExpr));
        toInsert.forEach(t -> t.addTag(CUSTOM_TAGS.NEWLY_ADDED_TAG.getTag()));
        return toInsert;
    }

    private List<Unit> getLogInvEndStmts(SootMethod method, LocalGenerator localGenerator, MethodDetails methodDetails, Value threadIDLocal, Value exeIDLocal, Value thisLocal, Stmt callStmt) {
        List<Unit> toInsert = new ArrayList<>();
        Value returnVal = NullConstant.v();
        if (!methodDetails.getReturnSootType().equals(VoidType.v()) && callStmt instanceof AssignStmt) {
            returnVal = (method.getReturnType() instanceof PrimType) ? localGenerator.generateLocal(((PrimType) method.getReturnType()).boxedType()) : ((AssignStmt) callStmt).getLeftOp();
            toInsert.addAll(getReturnLocalSetupStmt(localGenerator, method.getReturnType(), returnVal, ((AssignStmt) callStmt).getLeftOp()));
        }

        Expr logExpr = Jimple.v().newStaticInvokeExpr(logEndMethod.makeRef(), exeIDLocal, thisLocal, returnVal, threadIDLocal);
        toInsert.add(Jimple.v().newInvokeStmt(logExpr));
        toInsert.forEach(t -> t.addTag(CUSTOM_TAGS.NEWLY_ADDED_TAG.getTag()));
        return toInsert;
    }

    private List<Unit> reverse(List<Unit> units) {
        List<Unit> reversedUnits = new ArrayList<>(units);
        Collections.reverse(reversedUnits);
        return reversedUnits;
    }

    // archive
   /* private boolean toLogInvokedMethod(InvokeExpr invokeExpr, SootMethod currentMethod, SootMethodRef invokedMethod) {
//        if(!invokedMethod.getDeclaringClass().getPackageName().startsWith(properties.getPUT())) logger.debug(invokedMethod);
        //  if not calling library class
        if (!invokedMethod.getDeclaringClass().isJavaLibraryClass()) return false;
        // if invoking constructor of interface
        if (invokedMethod.isConstructor() && invokedMethod.getDeclaringClass().isInterface()) return false;
        // if just calling parent constructor
        if (currentMethod.isConstructor() && invokedMethod.isConstructor() && currentMethod.getDeclaringClass().getSuperclass().equals(invokedMethod.getDeclaringClass()))
            return false;
        try {
            Class<?> declaringClass = ClassUtils.getClass(invokedMethod.getDeclaringClass().getName());
            if (ClassUtils.isPrimitiveOrWrapper(declaringClass) || declaringClass.equals(Arrays.class) || declaringClass.equals(Array.class) || declaringClass.equals(Thread.class) || declaringClass.getPackage().getName().startsWith("java.util.concurrent"))
                return false;
            if (declaringClass.equals(String.class) || StringBVarDetails.availableTypeCheck(declaringClass))
                return false;
            if (MapVarDetails.availableTypeCheck(declaringClass)) return false;
            if (Collection.class.isAssignableFrom(declaringClass) && declaringClass.getName().startsWith("java") && ((invokedMethod.getReturnType() instanceof PrimType || invokedMethod.getReturnType().equals(VoidType.v())) || invokedMethod.getReturnType().toString().equals(Object.class.getName())))
                return false;
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        try {
            if (invokedMethod.isStatic() && (invokedMethod.getReturnType() instanceof PrimType || invokedMethod.getReturnType().equals(RefType.v("java.lang.String")) || ClassUtils.isPrimitiveOrWrapper(ClassUtils.getClass(invokedMethod.getReturnType().toString()))))
                return false;
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        return (invokedMethod.isConstructor() || (invokeExpr instanceof InstanceInvokeExpr) || invokedMethod.isStatic());
    }*/

    private enum CUSTOM_TAGS {
        NEWLY_ADDED_TAG("NEWLY_ADDED_TAG"),
        INV_TO_LOG_TAG("INV_TO_LOG_TAG"),
        DAN_FIELD_ACCESS_TO_LOG_TAG("DAN_FIELD_ACCESS_TO_LOG_TAG"),
        DAN_LIB_CALL_TO_LOG_TAG("DAN_LIB_CALL_TO_LOG_TAG"),
        PARAM_TRACK_TO_LOG_TAG("PARAM_TRACK_TO_LOG_TAG");
        private final Tag tag;

        CUSTOM_TAGS(String name) {
            tag = new Tag() {
                @Override
                public String getName() {
                    return name;
                }

                @Override
                public byte[] getValue() throws AttributeValueException {
                    return new byte[0];
                }
            };
        }

        public Tag getTag() {
            return tag;
        }

        public static boolean containsCustomTag(Stmt stmt) {
            return Arrays.stream(CUSTOM_TAGS.values()).anyMatch(t -> stmt.hasTag(t.getTag().getName()));
        }
    }

}
