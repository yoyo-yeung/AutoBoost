package program.instrumentation;

import entity.LOG_ITEM;
import entity.METHOD_TYPE;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import program.analysis.MethodDetails;
import soot.*;
import soot.jimple.*;
import soot.util.Chain;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.stream.Collectors;

public class Instrumenter extends BodyTransformer {
    private static final Logger logger = LogManager.getLogger(Instrumenter.class);
    private static final SootClass loggerClass;
    private static Map<String, SootMethod> logMethodMap = new HashMap<>();
    private static final SootMethod startLogMethod;
    private static final String[] supportedType = {Object.class.getName(), byte.class.getName(), short.class.getName(), int.class.getName(), long.class.getName(), float.class.getName(), double.class.getName(), boolean.class.getName(), char.class.getName(), String.class.getName() };
    static {
        loggerClass = Scene.v().loadClassAndSupport("program.execution.ExecutionLogger");
        startLogMethod = loggerClass.getMethod("void start(int,java.lang.String)");
        logMethodMap =  Arrays.stream(supportedType).collect(Collectors.toMap(t -> t, t -> loggerClass.getMethod("void log(int,java.lang.String,java.lang.String,"+t+")")));
    }

    // future task : add casting of primitive value to wrapper type to for code reuse
    @Override
    protected void internalTransform(Body body, String phaseName, Map<String, String> options) {
        InstrumentResult result = InstrumentResult.getSingleton();
        Stmt stmt;
        Chain<Unit> units = body.getUnits();
        Iterator<?> stmtIt = units.snapshotIterator();
        MethodDetails methodDetails = new MethodDetails(body.getMethod());
        int methodId = methodDetails.getId();
        SootClass declaringClass = body.getMethod().getDeclaringClass();
        declaringClass.getFields().forEach(f -> f.setModifiers(f.getModifiers()&~Modifier.TRANSIENT&~Modifier.PRIVATE &~Modifier.PROTECTED | Modifier.PUBLIC ));
        InvokeExpr invExpr;
        Stmt invStmt;
        boolean directAssgn = false, paramLogged = false;
        while(stmtIt.hasNext()){
            stmt = (Stmt) stmtIt.next();

            if(stmt instanceof soot.jimple.internal.JIdentityStmt) {
                continue;
            }
            if(!directAssgn && stmt instanceof AssignStmt && ((AssignStmt) stmt).getLeftOp().toString().indexOf("this")==0)
                directAssgn = true;

            // log if the method is NOT static initializer and NOT enum class
            if(methodDetails.getType().equals(METHOD_TYPE.STATIC_INITIALIZER) || declaringClass.isEnum())
                continue;
            if (!paramLogged) {
                // log start of method
                invExpr = Jimple.v().newStaticInvokeExpr(startLogMethod.makeRef(), IntConstant.v(methodId), StringConstant.v(LOG_ITEM.START_CALL.toString()));
                invStmt = Jimple.v().newInvokeStmt(invExpr);
                units.insertBefore(invStmt, stmt);
                // log callee details if it is a member function
                if (methodDetails.getType().equals(METHOD_TYPE.MEMBER)) {
                    invExpr = Jimple.v().newStaticInvokeExpr(logMethodMap.get(Object.class.getName()).makeRef(), IntConstant.v(methodId), StringConstant.v(LOG_ITEM.CALL_THIS.toString()), StringConstant.v(body.getThisLocal().getName()), body.getThisLocal());
                    invStmt = Jimple.v().newInvokeStmt(invExpr);
                    units.insertBefore(invStmt, stmt);
                }
                // log params if there is one
                if (body.getMethod().getParameterCount() > 0)
                    for (Local l : body.getParameterLocals()) {
                        invExpr = Jimple.v().newStaticInvokeExpr(logMethodMap.getOrDefault(l.getType().toString(), logMethodMap.get(Object.class.getName())).makeRef(), IntConstant.v(methodId), StringConstant.v(LOG_ITEM.CALL_PARAM.toString()), StringConstant.v(l.getName()), l);
                        invStmt = Jimple.v().newInvokeStmt(invExpr);
                        units.insertBefore(invStmt, stmt);
                    }
                // set paramLogged to prevent re-logging
                paramLogged = true;
            }
            if (stmt instanceof ThrowStmt) {
                Value thrownOp = ((ThrowStmt) stmt).getOp();
                invExpr = Jimple.v().newStaticInvokeExpr(logMethodMap.get(Object.class.getName()).makeRef(), IntConstant.v(methodId), StringConstant.v(LOG_ITEM.THREW_EXCEPTION.toString()), StringConstant.v(thrownOp.getType().toString()), thrownOp instanceof Local ? (Local) thrownOp : thrownOp);
                invStmt = Jimple.v().newInvokeStmt(invExpr);
                units.insertBefore(invStmt, stmt);
            }
            if (stmt instanceof ReturnStmt || stmt instanceof ReturnVoidStmt) {
                if (methodDetails.getType().equals(METHOD_TYPE.CONSTRUCTOR) || methodDetails.getType().equals(METHOD_TYPE.MEMBER)) {
                    invExpr = Jimple.v().newStaticInvokeExpr(logMethodMap.get(Object.class.getName()).makeRef(), IntConstant.v(methodId), StringConstant.v(LOG_ITEM.RETURN_THIS.toString()), StringConstant.v(body.getThisLocal().getName()), body.getThisLocal());
                    invStmt = Jimple.v().newInvokeStmt(invExpr);
                    units.insertBefore(invStmt, stmt);
                }

                if (stmt instanceof ReturnStmt) {
                    Value returnOp = ((ReturnStmt) stmt).getOp();
                    invExpr = Jimple.v().newStaticInvokeExpr(logMethodMap.getOrDefault(body.getMethod().getReturnType().toString(), logMethodMap.get(Object.class.getName())).makeRef(), IntConstant.v(methodId), StringConstant.v(LOG_ITEM.RETURN_ITEM.toString()), StringConstant.v("RETURN"), returnOp instanceof Local ? (Local) returnOp : returnOp);
                } else
                    invExpr = Jimple.v().newStaticInvokeExpr(logMethodMap.get(String.class.getName()).makeRef(), IntConstant.v(methodId), StringConstant.v(LOG_ITEM.RETURN_VOID.toString()), StringConstant.v("RETURN"), StringConstant.v("NULL"));
                invStmt = Jimple.v().newInvokeStmt(invExpr);
                units.insertBefore(invStmt, stmt);

            }


        }
        result.addMethod(methodDetails);
//        if(methodDetails.getType().equals(METHOD_TYPE.CONSTRUCTOR)){
//            stmtIt = units.snapshotIterator();
//            logger.info(methodDetails.getSubsignature());
//            while(stmtIt.hasNext()){
//                stmt  = (Stmt) stmtIt.next();
//                logger.debug(stmt);
//            }
//        }
//        logger.debug(methodDetails);
    }
}
