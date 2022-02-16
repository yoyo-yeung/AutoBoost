package program.instrumentation;

import entity.LOG_ITEM;
import entity.METHOD_TYPE;
import helper.Properties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import program.analysis.MethodDetails;
import soot.*;
import soot.javaToJimple.LocalGenerator;
import soot.jimple.*;
import soot.tagkit.AttributeValueException;
import soot.tagkit.LineNumberTag;
import soot.tagkit.Tag;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Instrumenter extends BodyTransformer {
    private static final Logger logger = LogManager.getLogger(Instrumenter.class);
    private static final SootClass loggerClass;
    private static final SootMethod startLogMethod;
    private static final String[] supportedType = {Object.class.getName(), byte.class.getName(), short.class.getName(), int.class.getName(), long.class.getName(), float.class.getName(), double.class.getName(), boolean.class.getName(), char.class.getName(), String.class.getName()};
    private static final Tag NEWLY_ADDED_TAG = new Tag() {
        @Override
        public String getName() {
            return "NEWLY_ADDED";
        }

        @Override
        public byte[] getValue() throws AttributeValueException {
            return new byte[0];
        }
    };
    private static Map<String, SootMethod> logMethodMap = new HashMap<>();

    static {
        loggerClass = Scene.v().loadClassAndSupport("program.execution.ExecutionLogger");
        startLogMethod = loggerClass.getMethod("void start(int,java.lang.String)");
        logMethodMap = Arrays.stream(supportedType).collect(Collectors.toMap(t -> t, t -> loggerClass.getMethod("void log(int,java.lang.String," + t + ")")));
    }
    private List<Unit> createArrForParams( LocalGenerator localGenerator, Local paramArrLocal,  SootMethod method, List<?> params) {
        if(params.size() <= 0 ) return new ArrayList<>();
        List<Unit> toInsert = new ArrayList<>();
//        Local paramArrLocal = localGenerator.generateLocal(ArrayType.v(RefType.v(Object.class.getName()), params.size()));
        Unit defArrUnit = Jimple.v().newAssignStmt(paramArrLocal, Jimple.v().newNewArrayExpr(RefType.v(Object.class.getName()), IntConstant.v(params.size())));
        defArrUnit.addTag(NEWLY_ADDED_TAG);
        toInsert.add(defArrUnit);
        IntStream.range(0, params.size()).forEach(i -> {
            Value paramLocal = (Value) params.get(i);
            if(paramLocal.getType() instanceof PrimType) {
                Local castLocal = localGenerator.generateLocal(((PrimType)paramLocal.getType()).boxedType());
                SootMethodRef ref = Scene.v().makeMethodRef(((PrimType) paramLocal.getType()).boxedType().getSootClass(), "valueOf",
                        Collections.singletonList(paramLocal.getType()), castLocal.getType(), true);
                Unit castUnit = Jimple.v().newAssignStmt(castLocal, Jimple.v().newStaticInvokeExpr(ref, paramLocal));
                castUnit.addTag(NEWLY_ADDED_TAG);
                toInsert.add(castUnit);
                toInsert.add( Jimple.v().newAssignStmt(Jimple.v().newArrayRef(paramArrLocal, IntConstant.v(i)), castLocal));
            }
            else
                toInsert.add(Jimple.v().newAssignStmt(Jimple.v().newArrayRef(paramArrLocal, IntConstant.v(i)), paramLocal));
        });
        return toInsert;

    }
    // future task : add casting of primitive value to wrapper type to for code reuse
    @Override
    protected void internalTransform(Body body, String phaseName, Map<String, String> options) {
        InstrumentResult result = InstrumentResult.getSingleton();
        Stmt stmt;
        PatchingChain<Unit> units = body.getUnits();
        Iterator<?> stmtIt = units.snapshotIterator();

        SootMethod sootMethod = body.getMethod();
        SootClass declaringClass = sootMethod.getDeclaringClass();
        declaringClass.getFields().forEach(f -> f.setModifiers(f.getModifiers() & ~Modifier.TRANSIENT & ~Modifier.PRIVATE & ~Modifier.PROTECTED | Modifier.PUBLIC));

        MethodDetails methodDetails = new MethodDetails(sootMethod);
        LocalGenerator localGenerator = new LocalGenerator(sootMethod.getActiveBody());
        int methodId = methodDetails.getId();
        InvokeExpr loggingExpr;
        Stmt loggingStmt;
        boolean paramLogged = false;
        int startLineNo = sootMethod.getJavaSourceStartLineNumber();
        int endLineNo = startLineNo;
        while (stmtIt.hasNext()) {
            stmt = (Stmt) stmtIt.next();
            Stmt finalStmt = stmt;
//            logger.debug(stmt.getTags().stream().map(e -> e.getName() + "," + e.getValue()).collect(Collectors.joining(",")));
            if (stmt.hasTag("LineNumberTag") && endLineNo < ((LineNumberTag) finalStmt.getTag("LineNumberTag")).getLineNumber())
                endLineNo = ((LineNumberTag) finalStmt.getTag("LineNumberTag")).getLineNumber();
            if (stmt instanceof soot.jimple.internal.JIdentityStmt) {
                continue;
            }

            // log if the method is NOT static initializer and NOT enum class
            if (declaringClass.isEnum())
                continue;
            if (!paramLogged) {
                // log start of method
                loggingExpr = Jimple.v().newStaticInvokeExpr(startLogMethod.makeRef(), IntConstant.v(methodId), StringConstant.v(LOG_ITEM.START_CALL.toString()));
                loggingStmt = Jimple.v().newInvokeStmt(loggingExpr);
                loggingStmt.addTag(NEWLY_ADDED_TAG);
                units.insertBeforeNoRedirect(loggingStmt, stmt);
                // log callee details if it is a member function
                if (methodDetails.getType().equals(METHOD_TYPE.MEMBER)) {
                    loggingExpr = Jimple.v().newStaticInvokeExpr(logMethodMap.get(Object.class.getName()).makeRef(), IntConstant.v(methodId), StringConstant.v(LOG_ITEM.CALL_THIS.toString()), body.getThisLocal());
                    loggingStmt = Jimple.v().newInvokeStmt(loggingExpr);

                    loggingStmt.addTag(NEWLY_ADDED_TAG);
                    units.insertBeforeNoRedirect(loggingStmt, stmt);
                }
                // log params if there is one
                if (sootMethod.getParameterCount() > 0) {
                    Local arrLocal = localGenerator.generateLocal(ArrayType.v(RefType.v(Object.class.getName()), sootMethod.getParameterCount()));
                    Stmt finalStmt1 = stmt;
                    createArrForParams(localGenerator, arrLocal, sootMethod, body.getParameterLocals()).forEach(u -> units.insertBeforeNoRedirect(u, finalStmt1));
                    loggingExpr = Jimple.v().newStaticInvokeExpr(logMethodMap.get(Object.class.getName()).makeRef(), IntConstant.v(methodId), StringConstant.v(LOG_ITEM.CALL_PARAM.toString()), arrLocal);
                    loggingStmt = Jimple.v().newInvokeStmt(loggingExpr);
                    loggingStmt.addTag(NEWLY_ADDED_TAG);
                    units.insertBeforeNoRedirect(loggingStmt, stmt);
                }
                // set paramLogged to prevent re-logging
                paramLogged = true;
            }
            if (stmt instanceof ThrowStmt) {
                Value thrownOp = ((ThrowStmt) stmt).getOp();
                loggingExpr = Jimple.v().newStaticInvokeExpr(logMethodMap.get(Object.class.getName()).makeRef(), IntConstant.v(methodId), StringConstant.v(LOG_ITEM.THREW_EXCEPTION.toString()), thrownOp);
                loggingStmt = Jimple.v().newInvokeStmt(loggingExpr);
                loggingStmt.addTag(NEWLY_ADDED_TAG);
                units.insertBefore(loggingStmt, stmt);
            }
            if (stmt instanceof ReturnStmt || stmt instanceof ReturnVoidStmt) {
                if (methodDetails.getType().equals(METHOD_TYPE.CONSTRUCTOR) || methodDetails.getType().equals(METHOD_TYPE.MEMBER)) {
                    loggingExpr = Jimple.v().newStaticInvokeExpr(logMethodMap.get(Object.class.getName()).makeRef(), IntConstant.v(methodId), StringConstant.v(LOG_ITEM.RETURN_THIS.toString()), body.getThisLocal());
                    loggingStmt = Jimple.v().newInvokeStmt(loggingExpr);
                    loggingStmt.addTag(NEWLY_ADDED_TAG);
                    units.insertBefore(loggingStmt, stmt);
                }

                if (stmt instanceof ReturnStmt) {
                    Value returnOp = ((ReturnStmt) stmt).getOp();
                    loggingExpr = Jimple.v().newStaticInvokeExpr(logMethodMap.getOrDefault(sootMethod.getReturnType().toString(), logMethodMap.get(Object.class.getName())).makeRef(), IntConstant.v(methodId), StringConstant.v(LOG_ITEM.RETURN_ITEM.toString()), returnOp);
                } else
                    loggingExpr = Jimple.v().newStaticInvokeExpr(logMethodMap.get(String.class.getName()).makeRef(), IntConstant.v(methodId), StringConstant.v(LOG_ITEM.RETURN_VOID.toString()), StringConstant.v("NULL"));
                loggingStmt = Jimple.v().newInvokeStmt(loggingExpr);
                loggingStmt.addTag(NEWLY_ADDED_TAG);
                units.insertBefore(loggingStmt, stmt);

            }
            if(stmt.containsInvokeExpr() && isLoggingInvokedLibMethod(stmt.getInvokeExpr(), sootMethod, stmt.getInvokeExpr().getMethod())) {
                InvokeExpr invokedExpr = stmt.getInvokeExpr();
                MethodDetails invokedMethodDetails = result.findExistingLibMethod(invokedExpr.getMethod().getSignature());
                if (invokedMethodDetails == null) {
                    invokedMethodDetails = new MethodDetails(invokedExpr.getMethod());
                    result.addLibMethod(invokedMethodDetails);
                }
                int invokedMethodID = invokedMethodDetails.getId();
                loggingExpr = Jimple.v().newStaticInvokeExpr(startLogMethod.makeRef(), IntConstant.v(invokedMethodID), StringConstant.v(LOG_ITEM.START_CALL.toString()));
                loggingStmt = Jimple.v().newInvokeStmt(loggingExpr);
                loggingStmt.addTag(NEWLY_ADDED_TAG);
                units.insertBefore(loggingStmt, stmt);
                if (invokedMethodDetails.getType().equals(METHOD_TYPE.MEMBER) && invokedExpr instanceof InstanceInvokeExpr) {
                    loggingExpr = Jimple.v().newStaticInvokeExpr(logMethodMap.get(Object.class.getName()).makeRef(), IntConstant.v(invokedMethodID), StringConstant.v(LOG_ITEM.CALL_THIS.toString()), ((InstanceInvokeExpr) invokedExpr).getBase());
                    loggingStmt = Jimple.v().newInvokeStmt(loggingExpr);
                    loggingStmt.addTag(NEWLY_ADDED_TAG);
                    units.insertBefore(loggingStmt, stmt);
                }
                if(invokedMethodDetails.getParameterCount() > 0 ) {
                    Local arrLocal = localGenerator.generateLocal(ArrayType.v(RefType.v(Object.class.getName()), invokedMethodDetails.getParameterCount()));
                    units.insertBefore(createArrForParams(localGenerator, arrLocal, invokedExpr.getMethod(), invokedExpr.getArgs()), stmt);
                    loggingExpr = Jimple.v().newStaticInvokeExpr(logMethodMap.get(Object.class.getName()).makeRef(), IntConstant.v(invokedMethodID), StringConstant.v(LOG_ITEM.CALL_PARAM.toString()), arrLocal);
                    loggingStmt = Jimple.v().newInvokeStmt(loggingExpr);
                    loggingStmt.addTag(NEWLY_ADDED_TAG);
                    units.insertBefore(loggingStmt, stmt);
                }

                if(invokedMethodDetails.getReturnType().equals("void")) {
                    loggingExpr = Jimple.v().newStaticInvokeExpr(logMethodMap.get(Object.class.getName()).makeRef(), IntConstant.v(invokedMethodID), StringConstant.v(LOG_ITEM.RETURN_VOID.toString()), StringConstant.v("NULL"));
                    loggingStmt = Jimple.v().newInvokeStmt(loggingExpr);
                    loggingStmt.addTag(NEWLY_ADDED_TAG);
                    units.insertAfter(loggingStmt, stmt);
                }
                else {

                    if(stmt instanceof AssignStmt)
                        loggingExpr = Jimple.v().newStaticInvokeExpr(logMethodMap.getOrDefault(invokedMethodDetails.getReturnType().toString(), logMethodMap.get(Object.class.getName())).makeRef(), IntConstant.v(invokedMethodID), StringConstant.v(LOG_ITEM.RETURN_ITEM.toString()), ((AssignStmt)stmt).getLeftOp());
                    else
                        loggingExpr = Jimple.v().newStaticInvokeExpr(logMethodMap.get(Object.class.getName()).makeRef(), IntConstant.v(invokedMethodID), StringConstant.v(LOG_ITEM.RETURN_ITEM.toString()),  NullConstant.v());
                    loggingStmt = Jimple.v().newInvokeStmt(loggingExpr);
                    loggingStmt.addTag(NEWLY_ADDED_TAG);
                    units.insertAfter(loggingStmt, stmt);
                }
                if ((invokedMethodDetails.getType().equals(METHOD_TYPE.CONSTRUCTOR) || invokedMethodDetails.getType().equals(METHOD_TYPE.MEMBER) ) && (invokedExpr instanceof InstanceInvokeExpr )) {
                    loggingExpr = Jimple.v().newStaticInvokeExpr(logMethodMap.get(Object.class.getName()).makeRef(), IntConstant.v(invokedMethodID), StringConstant.v(LOG_ITEM.RETURN_THIS.toString()), ((InstanceInvokeExpr) invokedExpr).getBase());
                    loggingStmt = Jimple.v().newInvokeStmt(loggingExpr);
                    loggingStmt.addTag(NEWLY_ADDED_TAG);
                    units.insertAfter(loggingStmt, stmt);
                }
            }
        }
        result.addMethod(methodDetails);
        int finalEndLineNo = endLineNo;
        if (Properties.getSingleton().getFaultyClassLineMap().entrySet().stream().anyMatch(e ->
                (e.getKey().equals(sootMethod.getDeclaringClass().getName()) || (sootMethod.getDeclaringClass().isInnerClass() && sootMethod.getDeclaringClass().getOuterClass().getName().equals(e.getKey()))) && e.getValue().stream().anyMatch(i -> i <= finalEndLineNo && i >= startLineNo))) {
            Properties.getSingleton().addFaultyFunc(sootMethod.getSignature());
            Properties.getSingleton().addFaultyFuncId(methodId);
        }

    }
    private boolean isLoggingInvokedLibMethod(InvokeExpr invokedExpr, SootMethod currentMethod, SootMethod invokedMethod) {
        if(!invokedMethod.isJavaLibraryMethod()) return false;
        if(invokedMethod.isConstructor() && invokedMethod.getDeclaringClass().isInterface()) return false;
        if(!invokedMethod.isPublic()) return false;
        if(currentMethod.isConstructor() && currentMethod.getDeclaringClass().getSuperclass().equals(invokedMethod.getDeclaringClass()) && invokedMethod.isConstructor()) return false;

        return (invokedMethod.isConstructor() || (invokedExpr instanceof InstanceInvokeExpr) || (invokedMethod.isStatic() && !(invokedMethod.getReturnType() instanceof PrimType)));
    }
}
