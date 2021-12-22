package program.instrumentation;

import entity.ACCESS;
import entity.LOG_ITEM;
import entity.METHOD_TYPE;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import program.analysis.ClassAnalysis;
import program.analysis.MethodDetails;
import soot.*;
import soot.jimple.*;
import soot.util.Chain;

import java.util.*;
import java.util.stream.Collectors;

public class Instrumenter extends BodyTransformer {
    private static Logger logger = LogManager.getLogger(Instrumenter.class);
    private static SootClass loggerClass;
    private static Map<String, SootMethod> logMethodMap = new HashMap<>();
    private static String[] supportedType = {Object.class.getName(), byte.class.getName(), short.class.getName(), int.class.getName(), long.class.getName(), float.class.getName(), double.class.getName(), boolean.class.getName(), char.class.getName(), String.class.getName() };
    static {
        loggerClass = Scene.v().loadClassAndSupport("program.execution.ExecutionLogger");
        logMethodMap =  Arrays.stream(supportedType).collect(Collectors.toMap(t -> t, t -> loggerClass.getMethod("void log(int,java.lang.String,java.lang.String,"+t+")")));
    }

    @Override
    protected void internalTransform(Body body, String phaseName, Map<String, String> options) {
        InstrumentResult result = InstrumentResult.getSingleton();
        ClassAnalysis analysis = result.getClassAnalysis();
        Stmt stmt;
        Chain<Unit> units = body.getUnits();
        Iterator<?> stmtIt = units.snapshotIterator();

        MethodDetails methodDetails = new MethodDetails(body.getMethod());
        int methodId = methodDetails.getId();
        SootClass declaringClass = body.getMethod().getDeclaringClass();
        InvokeExpr invExpr;
        Stmt invStmt;
        if(!analysis.isVisited(declaringClass.getName())){
            analysis.addClass(declaringClass);
            if(declaringClass.hasSuperclass() && !declaringClass.getSuperclass().isLibraryClass() && !declaringClass.getSuperclass().isJavaLibraryClass()){
                analysis.addClass(declaringClass.getSuperclass());
                analysis.addRelation(declaringClass.getSuperclass().getName(), declaringClass.getName());
            }
            declaringClass.getInterfaces().forEach(i -> {
                analysis.addClass(i);
                analysis.addRelation(i.getName(), declaringClass.getName());
            });
            analysis.addVisitedClass(declaringClass.getName());
        }
        boolean directAssgn = false, paramLogged = false;
        while(stmtIt.hasNext()){
            stmt = (Stmt) stmtIt.next();

            if(stmt instanceof soot.jimple.internal.JIdentityStmt) {
                continue;
            }
            if(!directAssgn && stmt instanceof AssignStmt && ((AssignStmt) stmt).getLeftOp().toString().indexOf("this")==0)
                directAssgn = true;
                if(methodDetails.getType().equals(METHOD_TYPE.MEMBER)){
                    invExpr = Jimple.v().newStaticInvokeExpr(logMethodMap.get(Object.class.getName()).makeRef(), IntConstant.v(methodId), StringConstant.v(LOG_ITEM.CALL_THIS.toString()), StringConstant.v(body.getThisLocal().getName()), body.getThisLocal());
                    invStmt = Jimple.v().newInvokeStmt(invExpr);
                    units.insertBefore(invStmt, stmt);
                }
                if(body.getMethod().getParameterCount()>0)
                    for(Local l : body.getParameterLocals()){
                        invExpr = Jimple.v().newStaticInvokeExpr(logMethodMap.getOrDefault(l.getType().toString(), logMethodMap.get(Object.class.getName())).makeRef(), IntConstant.v(methodId), StringConstant.v(LOG_ITEM.CALL_PARAM.toString()), StringConstant.v(l.getName()), l);
                        invStmt = Jimple.v().newInvokeStmt(invExpr);
                        units.insertBefore(invStmt, stmt);
                    }
                paramLogged = true;
            }
            if(stmt instanceof  ReturnStmt || stmt instanceof ReturnVoidStmt) {
                if(!methodDetails.getReturnType().getClass().equals(VoidType.class)){
//                logger.debug(((ReturnStmt)stmt).getOp());
                }
                if (methodDetails.getType().equals(METHOD_TYPE.CONSTRUCTOR)) {
                    invExpr = Jimple.v().newStaticInvokeExpr(logMethodMap.get(Object.class.getName()).makeRef(), IntConstant.v(methodId), StringConstant.v(LOG_ITEM.RETURN_THIS.toString()), StringConstant.v(body.getThisLocal().getName()), body.getThisLocal());
                    invStmt = Jimple.v().newInvokeStmt(invExpr);
                    units.insertBefore(invStmt, stmt);
                }
            }
        }
        methodDetails.setDirectAssignment(directAssgn);
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
