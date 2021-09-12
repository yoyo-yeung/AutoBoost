package instrumentation;
import helper.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.*;
import soot.jimple.IntConstant;
import soot.jimple.InvokeExpr;
import soot.jimple.Jimple;
import soot.jimple.Stmt;
import soot.util.Chain;

import java.util.Iterator;
import java.util.Map;

public class Instrumenter extends BodyTransformer{
    static Logger logger = LoggerFactory.getLogger(Instrumenter.class);
    static SootClass counterClass;
    static SootMethod addCounterMethod;
    static {
        counterClass = Scene.v().loadClassAndSupport("instrumentation.Counter");
        addCounterMethod = counterClass.getMethod("void addOccurance(int,int)");
    }

    @Override
    protected void internalTransform(Body body, String phaseName, Map<String, String> options) {
        SootMethod method = body.getMethod();
        String belongedClass = method.getDeclaringClass().getName();

        Chain<Unit> units = body.getUnits();
        Iterator<?> stmtIt = units.snapshotIterator();
        Stmt stmt;
        String key;
        int index;
        InvokeExpr incExpr;
        Stmt incStmt;
        while(stmtIt.hasNext()) {
            stmt = (Stmt) stmtIt.next();
            if(stmt instanceof  soot.jimple.internal.JIdentityStmt)
                continue;
            key = belongedClass + "::" + method.getSubSignature()+":::" + stmt.toString();
            switch (Properties.getInstance().getIndexingMode()) {
                case USE:
                    index = Index.getInstance().getStatementIndex(key);
                    if(index==-1)
                        continue;
                    break;
                case CREATE:
                default:
                    index = Index.getInstance().getStatementIndex(key);
                    if(index == -1) {
                        index = Counter.getNewIndex();
                        Index.getInstance().setStatementIndex(key, index);
                    }
                    break;
            }
            incExpr = Jimple.v().newStaticInvokeExpr(addCounterMethod.makeRef(), IntConstant.v(index), IntConstant.v(1));
            incStmt = Jimple.v().newInvokeStmt(incExpr);
            units.insertBefore(incStmt, stmt);
        }

    }
}
