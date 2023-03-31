package instrumentation;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import soot.*;
import soot.jimple.*;
import soot.jimple.internal.JIdentityStmt;
import soot.util.Chain;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class Instrumenter extends BodyTransformer {
    private static Logger logger = LogManager.getLogger(Instrumenter.class);
    static SootClass counterClass;
    static SootClass logClass;
    static SootMethod addCounterMethod;
    static HashMap<String, SootMethod> logMethods = new HashMap<>();
    static SootMethod addMethodOrderMethod;
    private String classMethodSeparator = ":::";
    static {
        counterClass = Scene.v().loadClassAndSupport("instrumentation.Counter");
        addCounterMethod = counterClass.getMethod("void addOccurance(int)");
        logClass = Scene.v().loadClassAndSupport("instrumentation.MethodLogger");
        logMethods.put("int", logClass.getMethod("void logParam(java.lang.String,java.lang.String,int)"));
        logMethods.put("double", logClass.getMethod("void logParam(java.lang.String,java.lang.String,double)"));
        logMethods.put("float", logClass.getMethod("void logParam(java.lang.String,java.lang.String,float)"));
        logMethods.put("char", logClass.getMethod("void logParam(java.lang.String,java.lang.String,char)"));
        logMethods.put("byte", logClass.getMethod("void logParam(java.lang.String,java.lang.String,byte)"));
        logMethods.put("short", logClass.getMethod("void logParam(java.lang.String,java.lang.String,short)"));
        logMethods.put("boolean", logClass.getMethod("void logParam(java.lang.String,java.lang.String,boolean)"));
        logMethods.put("long", logClass.getMethod("void logParam(java.lang.String,java.lang.String,long)"));
        logMethods.put("int[]", logClass.getMethod("void logParam(java.lang.String,java.lang.String,int[])"));
        logMethods.put("double[]", logClass.getMethod("void logParam(java.lang.String,java.lang.String,double[])"));
        logMethods.put("float[]", logClass.getMethod("void logParam(java.lang.String,java.lang.String,float[])"));
        logMethods.put("char[]", logClass.getMethod("void logParam(java.lang.String,java.lang.String,char[])"));
        logMethods.put("byte[]", logClass.getMethod("void logParam(java.lang.String,java.lang.String,byte[])"));
        logMethods.put("short[]", logClass.getMethod("void logParam(java.lang.String,java.lang.String,short[])"));
        logMethods.put("boolean[]", logClass.getMethod("void logParam(java.lang.String,java.lang.String,boolean[])"));
        logMethods.put("long[]", logClass.getMethod("void logParam(java.lang.String,java.lang.String,long[])"));

        logMethods.put("int[][]", logClass.getMethod("void logParam(java.lang.String,java.lang.String,int[][])"));
        logMethods.put("double[][]", logClass.getMethod("void logParam(java.lang.String,java.lang.String,double[][])"));
        logMethods.put("float[][]", logClass.getMethod("void logParam(java.lang.String,java.lang.String,float[][])"));
        logMethods.put("char[][]", logClass.getMethod("void logParam(java.lang.String,java.lang.String,char[][])"));
        logMethods.put("byte[][]", logClass.getMethod("void logParam(java.lang.String,java.lang.String,byte[][])"));
        logMethods.put("short[][]", logClass.getMethod("void logParam(java.lang.String,java.lang.String,short[][])"));
        logMethods.put("boolean[][]", logClass.getMethod("void logParam(java.lang.String,java.lang.String,boolean[][])"));
        logMethods.put("long[][]", logClass.getMethod("void logParam(java.lang.String,java.lang.String,long[][])"));
        logMethods.put("Object", logClass.getMethod("void logParam(java.lang.String,java.lang.String,java.lang.Object)"));
        logMethods.put("java.lang.String", logClass.getMethod("void logParam(java.lang.String,java.lang.String,java.lang.String)"));
        addMethodOrderMethod = logClass.getMethod("void logMethod(java.lang.String)");

    }
    @Override
    protected void internalTransform(Body body, String phaseName, Map<String, String> options) {
//        logger.debug(body.getDefBoxes().get(0).getClass().getDeclaredField().setAccessible(true););
        SootMethod method = body.getMethod();
        String belongedClass = method.getDeclaringClass().getName();
        Chain<Unit> units = body.getUnits();
        Iterator<?> stmtIt = units.snapshotIterator();
        Stmt stmt;
        InvokeExpr incExpr;
        Stmt incStmt;
        InvokeExpr logExpr;
        Stmt logStmt;
        int stmtId;

        boolean logged = false;
        String logKey = belongedClass + classMethodSeparator + method.getSubSignature();
        while(stmtIt.hasNext()) {
            stmt = (Stmt) stmtIt.next();
            if(stmt instanceof soot.jimple.internal.JIdentityStmt) {
                continue;
            }
            if(!logged &&!method.isPrivate()){
                if(!method.isStatic()&& !method.getName().contains("<init>")){
                    Local tl = body.getThisLocal();
                    logExpr = Jimple.v().newStaticInvokeExpr(logMethods.getOrDefault(tl.getType().toString(), logMethods.get("Object")).makeRef(), StringConstant.v(logKey), StringConstant.v(tl.getName()), tl);
                    logStmt = Jimple.v().newInvokeStmt(logExpr);
                    units.insertBefore(logStmt, stmt);
                }
                logExpr = Jimple.v().newStaticInvokeExpr(addMethodOrderMethod.makeRef(), StringConstant.v(logKey));
                logStmt = Jimple.v().newInvokeStmt(logExpr);
                units.insertBefore(logStmt, stmt);
                for(Local l : body.getParameterLocals()) {
                    logExpr = Jimple.v().newStaticInvokeExpr(logMethods.getOrDefault(l.getType().toString(), logMethods.get("Object")).makeRef(), StringConstant.v(logKey), StringConstant.v(l.getName()), l);
                    logStmt = Jimple.v().newInvokeStmt(logExpr);
                    units.insertBefore(logStmt, stmt);
                }
//                if(method.getParameterCount()==0){
//                    logExpr = Jimple.v().newStaticInvokeExpr(logMethods.get("java.lang.String").makeRef(), StringConstant.v(logKey), StringConstant.v("No parameter"),  StringConstant.v(""));
//                    logStmt = Jimple.v().newInvokeStmt(logExpr);
//                    units.insertBefore(logStmt, stmt);
//                }
            }
            logged =true;
            stmtId = Counter.getStmtId(belongedClass + this.classMethodSeparator + logKey + this.classMethodSeparator + stmt.toString());
            incExpr = Jimple.v().newStaticInvokeExpr(addCounterMethod.makeRef(), IntConstant.v(stmtId));
            incStmt = Jimple.v().newInvokeStmt(incExpr);
            if(stmt instanceof ReturnStmt && !method.isPrivate()){
//                if(method.getName().contains("<init>"))
//                logger.debug(stmt);
                logExpr = getReturnInvokeExpr(logKey, (ReturnStmt) stmt);
//                if(logExpr==null)
//                    return;
                logStmt = Jimple.v().newInvokeStmt(logExpr);
                units.insertBefore(logStmt, stmt);
//                if(!method.isStatic() && method.getName().contains("<init>")) {
//                Local tl = body.getThisLocal();
//                logExpr = Jimple.v().newStaticInvokeExpr(logMethods.getOrDefault(tl.getType().toString(), logMethods.get("Object")).makeRef(), StringConstant.v(logKey), StringConstant.v(tl.getName()), tl);
//                logStmt = Jimple.v().newInvokeStmt(logExpr);
//                units.insertBefore(logStmt, stmt);
//            }
            }

            units.insertBefore(incStmt, stmt);
        }
//        stmtIt = units.snapshotIterator();
//        if(method.getSubSignature().contains("<init>"))
//        while(stmtIt.hasNext()) {
//            stmt = (Stmt) stmtIt.next();
////            if(stmt.toString().contains("round("))
//            logger.debug(stmt);
//        }
    }
    private InvokeExpr getReturnInvokeExpr(String logKey, ReturnStmt stmt) {
        Value op = stmt.getOp();
        if(op instanceof Local)
            return Jimple.v().newStaticInvokeExpr(logMethods.getOrDefault(op.getType().toString(), logMethods.get("Object")).makeRef(), StringConstant.v(logKey), StringConstant.v("RETURN"), (Local)op);
        else
//            return null;
            return Jimple.v().newStaticInvokeExpr(logMethods.get("java.lang.String").makeRef(),  StringConstant.v(logKey), StringConstant.v("RETURN"), StringConstant.v(op.toString()));
    }

}
