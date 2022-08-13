package program.instrumentation;

import entity.METHOD_TYPE;
import helper.Properties;
import org.apache.commons.lang3.ClassUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import program.analysis.MethodDetails;
import program.execution.variable.MapVarDetails;
import program.execution.variable.StringBVarDetails;
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

import java.lang.reflect.Array;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
        threadClass = Scene.v().loadClassAndSupport(Thread.class.getName());
        getCurrentThreadmRef = Scene.v().makeMethodRef(threadClass, "currentThread", new ArrayList<>(), RefType.v(Thread.class.getName()), true);
        getTIDmRef = Scene.v().makeMethodRef(threadClass, "getId", new ArrayList<>(), LongType.v(), false);

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

        // Prepare for statements adding
        LocalGenerator localGenerator = new LocalGenerator(currentSootMethod.getActiveBody());
        boolean paramLogged = false, threadRetrieved = false;
        Value exeIDLocal = localGenerator.generateLocal(IntType.v()), threadIDLocal = localGenerator.generateLocal(LongType.v());
        Local threadClassLocal = localGenerator.generateLocal(threadClass.getType());
        Comparator<Unit> unitComparator = (o1, o2) -> o1.equals(o2) ? 0 : 1;
        Queue<DefinitionStmt> inputUnits = new PriorityQueue<>(unitComparator); // for tracking input arguments and this instance separately
        while (stmtIt.hasNext()) {
            Stmt stmt = (Stmt) stmtIt.next();
            if(stmt instanceof JIdentityStmt) continue;
            if(!threadRetrieved) {
                getThreadIDRetrievalStmts(threadClassLocal, threadIDLocal)
                        .forEach(s -> units.insertBeforeNoRedirect(s, stmt));
                threadRetrieved = true;
            }
            if(!paramLogged) {
                getLogStartStmts(currentSootMethod, localGenerator, methodDetails, threadIDLocal, exeIDLocal, methodDetails.getType().equals(METHOD_TYPE.MEMBER)? currentSootMethod.getActiveBody().getThisLocal(): NullConstant.v(), currentSootMethod.getActiveBody().getParameterLocals())
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

            if(stmt.containsInvokeExpr() && toLogInvokedMethod(stmt.getInvokeExpr(), currentSootMethod, stmt.getInvokeExpr().getMethodRef())) {
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


//            if(stmt.containsFieldRef())
//                logger.debug(methodDetails.toString() + "\t" + stmt.getFieldRef());
        }

        instrumentResult.addMethod(methodDetails);
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
        DAN_LIB_CALL_TO_LOG_TAG("DAN_LIB_CALL_TO_LOG_TAG");
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
    }

}
