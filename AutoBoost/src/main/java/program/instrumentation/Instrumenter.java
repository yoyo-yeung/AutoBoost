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
        logMethodMap = Arrays.stream(supportedType).collect(Collectors.toMap(t -> t, t -> loggerClass.getMethod("void log(int,java.lang.String,java.lang.String," + t + ")")));
    }


    // future task : add casting of primitive value to wrapper type to for code reuse
    @Override
    protected void internalTransform(Body body, String phaseName, Map<String, String> options) {
        InstrumentResult result = InstrumentResult.getSingleton();
        Stmt stmt;
        PatchingChain<Unit> units = body.getUnits();
        Iterator<?> stmtIt = units.snapshotIterator();

        SootClass declaringClass = body.getMethod().getDeclaringClass();
        declaringClass.getFields().forEach(f -> f.setModifiers(f.getModifiers() & ~Modifier.TRANSIENT & ~Modifier.PRIVATE & ~Modifier.PROTECTED | Modifier.PUBLIC));

        MethodDetails methodDetails = new MethodDetails(body.getMethod());
        int methodId = methodDetails.getId();
        InvokeExpr invExpr;
        Stmt invStmt;
        boolean paramLogged = false;
        int startLineNo = body.getMethod().getJavaSourceStartLineNumber();
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
                invExpr = Jimple.v().newStaticInvokeExpr(startLogMethod.makeRef(), IntConstant.v(methodId), StringConstant.v(LOG_ITEM.START_CALL.toString()));
                invStmt = Jimple.v().newInvokeStmt(invExpr);
                invStmt.addTag(NEWLY_ADDED_TAG);
                units.insertBeforeNoRedirect(invStmt, stmt);
                // log callee details if it is a member function
                if (methodDetails.getType().equals(METHOD_TYPE.MEMBER)) {
                    invExpr = Jimple.v().newStaticInvokeExpr(logMethodMap.get(Object.class.getName()).makeRef(), IntConstant.v(methodId), StringConstant.v(LOG_ITEM.CALL_THIS.toString()), StringConstant.v(body.getThisLocal().getName()), body.getThisLocal());
                    invStmt = Jimple.v().newInvokeStmt(invExpr);

                    invStmt.addTag(NEWLY_ADDED_TAG);
                    units.insertBeforeNoRedirect(invStmt, stmt);
                }
                // log params if there is one
                if (body.getMethod().getParameterCount() > 0) {
                    LocalGenerator localGenerator = new LocalGenerator(body.getMethod().getActiveBody());
                    Local arrLocal = localGenerator.generateLocal(ArrayType.v(RefType.v(Object.class.getName()), body.getMethod().getParameterCount()));
                    Unit genArrUnit = Jimple.v().newAssignStmt(arrLocal, Jimple.v().newNewArrayExpr(RefType.v(Object.class.getName()), IntConstant.v(body.getMethod().getParameterCount())));
                    genArrUnit.addTag(NEWLY_ADDED_TAG);
                    units.insertBeforeNoRedirect(genArrUnit, stmt);
                    Stmt finalStmt1 = stmt;
                    IntStream.range(0, body.getMethod().getParameterCount()).forEach(i -> {
                        Local paramLocal = body.getParameterLocal(i);
                        Stmt arrAssignStmt;
                        if (paramLocal.getType() instanceof PrimType) {
                            Local castLocal = localGenerator.generateLocal(((PrimType) paramLocal.getType()).boxedType());
                            SootMethodRef ref = Scene.v().makeMethodRef(((PrimType) paramLocal.getType()).boxedType().getSootClass(), "valueOf",
                                    Collections.singletonList(paramLocal.getType()), castLocal.getType(), true);

                            Unit castUnit = Jimple.v().newAssignStmt(castLocal, Jimple.v().newStaticInvokeExpr(ref, paramLocal));
                            castUnit.addTag(NEWLY_ADDED_TAG);
                            units.insertBeforeNoRedirect(castUnit, finalStmt1);
                            arrAssignStmt = Jimple.v().newAssignStmt(Jimple.v().newArrayRef(arrLocal, IntConstant.v(i)), castLocal);
                        } else
                            arrAssignStmt = Jimple.v().newAssignStmt(Jimple.v().newArrayRef(arrLocal, IntConstant.v(i)), (paramLocal.getType() instanceof PrimType ? Jimple.v().newCastExpr(paramLocal, ((PrimType) paramLocal.getType()).boxedType()) : paramLocal));

                        arrAssignStmt.addTag(NEWLY_ADDED_TAG);
                        units.insertBeforeNoRedirect(arrAssignStmt, finalStmt1);
                    });
                    invExpr = Jimple.v().newStaticInvokeExpr(logMethodMap.get(Object.class.getName()).makeRef(), IntConstant.v(methodId), StringConstant.v(LOG_ITEM.CALL_PARAM.toString()), StringConstant.v("param_arr"), arrLocal);
                    invStmt = Jimple.v().newInvokeStmt(invExpr);
                    invStmt.addTag(NEWLY_ADDED_TAG);
                    units.insertBeforeNoRedirect(invStmt, stmt);
                }
                // set paramLogged to prevent re-logging
                paramLogged = true;
            }
            if (stmt instanceof ThrowStmt) {
                Value thrownOp = ((ThrowStmt) stmt).getOp();
                invExpr = Jimple.v().newStaticInvokeExpr(logMethodMap.get(Object.class.getName()).makeRef(), IntConstant.v(methodId), StringConstant.v(LOG_ITEM.THREW_EXCEPTION.toString()), StringConstant.v(thrownOp.getType().toString()), thrownOp instanceof Local ? thrownOp : thrownOp);
                invStmt = Jimple.v().newInvokeStmt(invExpr);
                invStmt.addTag(NEWLY_ADDED_TAG);
                units.insertBefore(invStmt, stmt);
            }
            if (stmt instanceof ReturnStmt || stmt instanceof ReturnVoidStmt) {
                if (methodDetails.getType().equals(METHOD_TYPE.CONSTRUCTOR) || methodDetails.getType().equals(METHOD_TYPE.MEMBER)) {
                    invExpr = Jimple.v().newStaticInvokeExpr(logMethodMap.get(Object.class.getName()).makeRef(), IntConstant.v(methodId), StringConstant.v(LOG_ITEM.RETURN_THIS.toString()), StringConstant.v(body.getThisLocal().getName()), body.getThisLocal());
                    invStmt = Jimple.v().newInvokeStmt(invExpr);
                    invStmt.addTag(NEWLY_ADDED_TAG);
                    units.insertBefore(invStmt, stmt);
                }

                if (stmt instanceof ReturnStmt) {
                    Value returnOp = ((ReturnStmt) stmt).getOp();
                    invExpr = Jimple.v().newStaticInvokeExpr(logMethodMap.getOrDefault(body.getMethod().getReturnType().toString(), logMethodMap.get(Object.class.getName())).makeRef(), IntConstant.v(methodId), StringConstant.v(LOG_ITEM.RETURN_ITEM.toString()), StringConstant.v("RETURN"), returnOp instanceof Local ? returnOp : returnOp);
                } else
                    invExpr = Jimple.v().newStaticInvokeExpr(logMethodMap.get(String.class.getName()).makeRef(), IntConstant.v(methodId), StringConstant.v(LOG_ITEM.RETURN_VOID.toString()), StringConstant.v("RETURN"), StringConstant.v("NULL"));
                invStmt = Jimple.v().newInvokeStmt(invExpr);
                invStmt.addTag(NEWLY_ADDED_TAG);
                units.insertBefore(invStmt, stmt);

            }

        }
        result.addMethod(methodDetails);
        int finalEndLineNo = endLineNo;
        if (Properties.getSingleton().getFaultyClassLineMap().entrySet().stream().anyMatch(e ->
                (e.getKey().equals(body.getMethod().getDeclaringClass().getName()) || (body.getMethod().getDeclaringClass().isInnerClass() && body.getMethod().getDeclaringClass().getOuterClass().getName().equals(e.getKey()))) && e.getValue().stream().anyMatch(i -> i <= finalEndLineNo && i >= startLineNo))) {
            Properties.getSingleton().addFaultyFunc(body.getMethod().getSignature());
            Properties.getSingleton().addFaultyFuncId(methodId);
        }

    }
}
