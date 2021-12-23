package program.execution;

import org.apache.commons.lang3.ClassUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import program.execution.variable.ObjVarDetails;

import java.util.Stack;

public class ExecutionLogger {
    private static Logger logger = LogManager.getLogger(ExecutionLogger.class);
    private static Stack<MethodExecution> executing = new Stack<>();

    public static void start(int methodId) {
//        logger.debug(methodId);
//        logger.debug(InstrumentResult.getSingleton().getMethodDetailsMap());

    }
    public static void log(int methodId, String process, String name, Object obj) {
        ExecutionTrace trace = ExecutionTrace.getSingleton();
        if(returnNow(methodId, process))
            return;
        if (obj.getClass().isArray()){
//            logger.debug(obj);
//            logger.debug(obj.getClass());
//            logger.debug(obj.getClass().getSimpleName());
//            ArrayVarDetails var = new ArrayVarDetails(obj);
//            logger.debug(Array.get(obj,0));
//            logger.debug(var);
        }
        else if(ClassUtils.isPrimitiveWrapper(obj.getClass())){}
        else {
            int ID = trace.getObjVarDetailsID(obj);
            VarDetail varDetails;
            if(ID == -1) {
                ID = trace.getNewVarID();
                varDetails = new ObjVarDetails(ID, obj);
                trace.addVarDetail(varDetails);
            }else {
                logger.debug("find old matching");
                logger.debug(trace.getAllVars().get(ID).getType());
                logger.debug(obj.getClass());
                logger.debug(trace.getAllVars().get(ID).getValue().hashCode()+"\t"+trace.getAllVars().get(ID).getValue().toString());
                logger.debug(obj.hashCode()+"\t"+obj.toString());
            }
            if(LOG_ITEM.valueOf(process).equals(LOG_ITEM.CALL_THIS)) {
                logger.debug(InstrumentResult.getSingleton().getMethodDetailsMap().get(ID));
                executing.peek().setCalleeId(ID);
            }
        }

    }

    public static void log(int methodId, String process, String name, byte value) {
    }

    public static void log(int methodId, String process, String name, short value) {

    }
    public static void log(int methodId, String process, String name, int value) {
        try {
//            logger.debug(new PrimitiveVarDetails(0, int.class, (Integer) value));
        }
        catch (Exception e) {
            logger.error(e);
        }
    }
    public static void log(int methodId, String process, String name, long value) {
    }
    public static void log(int methodId, String process, String name, float value) {
    }
    public static void log(int methodId, String process, String name, double value) {
    }
    public static void log(int methodId, String process, String name, boolean value) {
    }
    public static void log(int methodId, String process, String name, char value) {
    }
    public static void log(int methodId, String process, String name, String value) {
    }

}
