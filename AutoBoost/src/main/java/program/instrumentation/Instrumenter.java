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
import soot.tagkit.AttributeValueException;
import soot.tagkit.LineNumberTag;
import soot.tagkit.Tag;

import java.util.*;
import java.util.stream.IntStream;

public class Instrumenter extends BodyTransformer {
    private static final Logger logger = LogManager.getLogger(Instrumenter.class);
    private static final SootClass loggerClass;
    private static final SootMethod logStartMethod;
    private static final SootMethod logEndMethod;
    private static final SootMethod logExceptionMethod;

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
    static {
        loggerClass = Scene.v().loadClassAndSupport("program.execution.ExecutionLogger");
        logStartMethod = loggerClass.getMethod("void logStart(int,java.lang.Object,java.lang.Object)");
        logEndMethod = loggerClass.getMethod("void logEnd(int,java.lang.Object,java.lang.Object)");
        logExceptionMethod = loggerClass.getMethod("void logException(java.lang.Object)");
    }
    private List<Unit> createArrForParams( LocalGenerator localGenerator, Local paramArrLocal,  List<Type> paramTypes, List<?> params) {
        if(params.size() <= 0 ) return new ArrayList<>();
        List<Unit> toInsert = new ArrayList<>();
//        Local paramArrLocal = localGenerator.generateLocal(ArrayType.v(RefType.v(Object.class.getName()), params.size()));
        Unit defArrUnit = Jimple.v().newAssignStmt(paramArrLocal, Jimple.v().newNewArrayExpr(RefType.v(Object.class.getName()), IntConstant.v(params.size())));
        defArrUnit.addTag(NEWLY_ADDED_TAG);
        toInsert.add(defArrUnit);
        IntStream.range(0, params.size()).forEach(i -> {
            Value paramLocal = (Value) params.get(i);
            if(paramTypes.get(i) instanceof PrimType) {
                Local castLocal = localGenerator.generateLocal(((PrimType)paramTypes.get(i)).boxedType());
                SootMethodRef ref = Scene.v().makeMethodRef(((PrimType) paramTypes.get(i)).boxedType().getSootClass(), "valueOf",
                        Collections.singletonList(paramTypes.get(i)), castLocal.getType(), true);
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
            if (stmt.hasTag("LineNumberTag") && endLineNo < ((LineNumberTag) stmt.getTag("LineNumberTag")).getLineNumber())
                endLineNo = ((LineNumberTag) stmt.getTag("LineNumberTag")).getLineNumber();
            if (stmt instanceof soot.jimple.internal.JIdentityStmt) {
                continue;
            }

            // log if the method is NOT static initializer and NOT enum class
            if (declaringClass.isEnum())
                continue;
            if (!paramLogged) {
                // log callee and params
                Value paramLocal = NullConstant.v();
                if (sootMethod.getParameterCount() > 0) {
                    paramLocal = localGenerator.generateLocal(ArrayType.v(RefType.v(Object.class.getName()), sootMethod.getParameterCount()));
                    Stmt finalStmt1 = stmt;
                    createArrForParams(localGenerator, (Local) paramLocal, body.getMethod().getParameterTypes(), body.getParameterLocals())
                            .forEach(u -> units.insertBeforeNoRedirect(u, finalStmt1));
                }
                loggingExpr = Jimple.v().newStaticInvokeExpr(logStartMethod.makeRef(), IntConstant.v(methodId),  methodDetails.getType().equals(METHOD_TYPE.MEMBER) ? body.getThisLocal() : NullConstant.v(), paramLocal);
                loggingStmt = Jimple.v().newInvokeStmt(loggingExpr);
                loggingStmt.addTag(NEWLY_ADDED_TAG);
                units.insertBeforeNoRedirect(loggingStmt, stmt);
                // set paramLogged to prevent re-logging
                paramLogged = true;
            }
            if (stmt instanceof ThrowStmt) {
                Value thrownOp = ((ThrowStmt) stmt).getOp();
                loggingExpr = Jimple.v().newStaticInvokeExpr(logExceptionMethod.makeRef(), thrownOp);
                loggingStmt = Jimple.v().newInvokeStmt(loggingExpr);
                loggingStmt.addTag(NEWLY_ADDED_TAG);
                units.insertBefore(loggingStmt, stmt);
            }
            if (stmt instanceof ReturnStmt || stmt instanceof ReturnVoidStmt) {
                Value returnVal = NullConstant.v();

                if (stmt instanceof ReturnStmt) {
                    if(sootMethod.getReturnType() instanceof PrimType) {
                        returnVal = localGenerator.generateLocal(((PrimType)sootMethod.getReturnType()).boxedType());
                        SootMethodRef ref = Scene.v().makeMethodRef(((PrimType) sootMethod.getReturnType()).boxedType().getSootClass(), "valueOf",
                                Collections.singletonList(sootMethod.getReturnType()), returnVal.getType(), true);
                        Unit castUnit = Jimple.v().newAssignStmt(returnVal, Jimple.v().newStaticInvokeExpr(ref, ((ReturnStmt) stmt).getOp()));
                        castUnit.addTag(NEWLY_ADDED_TAG);
                        units.insertBefore(castUnit, stmt);
                    }
                    else
                        returnVal = ((ReturnStmt) stmt).getOp();
                }
                loggingExpr = Jimple.v().newStaticInvokeExpr(logEndMethod.makeRef(), IntConstant.v(methodId), methodDetails.getType().equals(METHOD_TYPE.CONSTRUCTOR) || methodDetails.getType().equals(METHOD_TYPE.MEMBER) ? body.getThisLocal() : NullConstant.v(), returnVal);
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
                Value paramLocal = NullConstant.v();
                if(invokedMethodDetails.getParameterCount() > 0 ) {
                    paramLocal = localGenerator.generateLocal(ArrayType.v(RefType.v(Object.class.getName()), invokedMethodDetails.getParameterCount()));
                    units.insertBefore(createArrForParams(localGenerator, (Local) paramLocal, invokedExpr.getMethod().getParameterTypes(), invokedExpr.getArgs()), stmt);
                }
                loggingExpr = Jimple.v().newStaticInvokeExpr(logStartMethod.makeRef(), IntConstant.v(invokedMethodID), invokedMethodDetails.getType().equals(METHOD_TYPE.MEMBER) && invokedExpr instanceof InstanceInvokeExpr ?((InstanceInvokeExpr) invokedExpr).getBase() : NullConstant.v(), paramLocal);
                loggingStmt = Jimple.v().newInvokeStmt(loggingExpr);
                loggingStmt.addTag(NEWLY_ADDED_TAG);
                units.insertBefore(loggingStmt, stmt);

                Value returnVal = NullConstant.v();
                Unit afterStmt = stmt;
                if (!invokedMethodDetails.getReturnSootType().equals(VoidType.v()) && stmt instanceof AssignStmt) {
                    if (invokedExpr.getMethod().getReturnType() instanceof PrimType) {
                        returnVal = localGenerator.generateLocal(((PrimType) invokedExpr.getMethod().getReturnType()).boxedType());
                        SootMethodRef ref = Scene.v().makeMethodRef(((PrimType) invokedExpr.getMethod().getReturnType()).boxedType().getSootClass(), "valueOf",
                                Collections.singletonList(invokedExpr.getMethod().getReturnType()), returnVal.getType(), true);
                        Unit castUnit = Jimple.v().newAssignStmt(returnVal, Jimple.v().newStaticInvokeExpr(ref, ((AssignStmt) stmt).getLeftOp()));
                        castUnit.addTag(NEWLY_ADDED_TAG);
                        units.insertAfter(castUnit, stmt);
                        afterStmt = castUnit;
                    } else returnVal = ((AssignStmt) stmt).getLeftOp();

                }
                loggingExpr = Jimple.v().newStaticInvokeExpr(logEndMethod.makeRef(), IntConstant.v(invokedMethodID), (invokedMethodDetails.getType().equals(METHOD_TYPE.CONSTRUCTOR) || invokedMethodDetails.getType().equals(METHOD_TYPE.MEMBER) ) && (invokedExpr instanceof InstanceInvokeExpr ) ? ((InstanceInvokeExpr) invokedExpr).getBase() : NullConstant.v(), returnVal);
                loggingStmt = Jimple.v().newInvokeStmt(loggingExpr);
                loggingStmt.addTag(NEWLY_ADDED_TAG);
                units.insertAfter(loggingStmt, afterStmt);
            }
        }
        result.addMethod(methodDetails);
        int finalEndLineNo = endLineNo;
        Properties properties = Properties.getSingleton();
        if (properties.getFaultyClassLineMap().entrySet().stream().anyMatch(e ->
                (e.getKey().equals(sootMethod.getDeclaringClass().getName()) || (sootMethod.getDeclaringClass().isInnerClass() && sootMethod.getDeclaringClass().getOuterClass().getName().equals(e.getKey()))) && e.getValue().stream().anyMatch(i -> i <= finalEndLineNo && i >= startLineNo))) {
            properties.addFaultyFunc(sootMethod.getSignature());
            properties.addFaultyFuncId(methodId);
        }

    }
    private boolean isLoggingInvokedLibMethod(InvokeExpr invokedExpr, SootMethod currentMethod, SootMethod invokedMethod) {
        if(!invokedMethod.isJavaLibraryMethod()) return false;
        if((invokedMethod.getName().equals("equals") ||  invokedMethod.getName().equals("hashCode") || invokedMethod.getName().equals("toString")) && !invokedMethod.isStatic()) return false;
        if(invokedMethod.isConstructor() && invokedMethod.getDeclaringClass().isInterface()) return false;
        if(!invokedMethod.isPublic()) return false;
        if(currentMethod.isConstructor() && currentMethod.getDeclaringClass().getSuperclass().equals(invokedMethod.getDeclaringClass()) && invokedMethod.isConstructor()) return false;
        try {
            if(invokedMethod.isStatic() && (invokedMethod.getReturnType() instanceof  PrimType || invokedMethod.getReturnType().equals(RefType.v("java.lang.String")) ||  ClassUtils.isPrimitiveOrWrapper(ClassUtils.getClass(invokedMethod.getReturnType().toString()))))
                return false;
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        return (invokedMethod.isConstructor() || (invokedExpr instanceof InstanceInvokeExpr) || invokedMethod.isStatic() );
    }
}
