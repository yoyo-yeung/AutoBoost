package program.execution;

import entity.LOG_ITEM;
import org.apache.commons.lang3.ClassUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import program.analysis.MethodDetails;
import program.execution.variable.ObjVarDetails;
import program.execution.variable.VarDetail;
import program.instrumentation.InstrumentResult;

import java.util.Arrays;
import java.util.Stack;

public class ExecutionLogger {
    private static Logger logger = LogManager.getLogger(ExecutionLogger.class);
    private static Stack<MethodExecution> executing = new Stack<>();
    private static final String[] skipMethods = {"equals", "toString", "hashCode"};

    /**
     * Invoked when a method has started its execution. the method would be added to stack for further processing
     * @param methodId ID of method currently being invoked
     * @param process LOG_ITEM value representing status of method call
     */
    public static void start(int methodId, String process) {
        // skip logging of method IF it is a method call enclosed
        if(returnNow(methodId, process))
            return;
        executing.add(new MethodExecution(ExecutionTrace.getSingleton().getNewExeID(), methodId));
    }

    /**
     * Method checking if the current operation and their corresponding values for the method should be stored.
     * Operation and values of sub-functions called under methods like "equals", "toString" and "hashCode" should not be processed and stored as these methods are also used during processing, hence processing of such methods would call itself and result in infinite loop.
     *
     * @param methodId ID of method invoked, corresponding MethodDetails can be found in InstrumentationResult
     * @param process value of LOG_ITEM type, representing the status of method execution and the item being stored
     * @return true if the current operation and value should not be logged, false if they should
     */
    private static boolean returnNow(int methodId, String process) {

        if(executing.size() == 0 || executing.peek() == null)
            return false;
        int latestID = executing.peek().getMethodInvokedId();
        MethodDetails details = InstrumentResult.getSingleton().getMethodDetailsMap().get(latestID);
        // if the method logged most recently is to be skipped
        if(Arrays.stream(skipMethods).anyMatch(m -> m.equals(details.getName()))) {
            // if the current method to be logged is same as the latest one, it must be stored to prevent inconsistent popping
            if(latestID == methodId && LOG_ITEM.valueOf(process).equals(LOG_ITEM.START_CALL))
                return false;
            // if the current method trying to be logged is the one to be skipped + it is "return-ing", then remove it from stack and go back to logging as expected
            if(latestID == methodId && (LOG_ITEM.valueOf(process).equals(LOG_ITEM.RETURN_ITEM) || LOG_ITEM.valueOf(process).equals(LOG_ITEM.RETURN_VOID)))
                executing.pop();
            return true;
        }
        return false;
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
        if(returnNow(methodId, process))
        return;
    }

    public static void log(int methodId, String process, String name, short value) {
        if(returnNow(methodId, process))
        return;

    }
    public static void log(int methodId, String process, String name, int value) {
        if(returnNow(methodId, process))
        return;
        try {
//            logger.debug(new PrimitiveVarDetails(0, int.class, (Integer) value));
        }
        catch (Exception e) {
            logger.error(e);
        }
    }
    public static void log(int methodId, String process, String name, long value) {
        if(returnNow(methodId, process))
        return;
    }
    public static void log(int methodId, String process, String name, float value) {
        if(returnNow(methodId, process))
        return;
    }
    public static void log(int methodId, String process, String name, double value) {
        if(returnNow(methodId, process))
        return;
    }
    public static void log(int methodId, String process, String name, boolean value) {
        if(returnNow(methodId, process))
        return;
    }
    public static void log(int methodId, String process, String name, char value) {
        if(returnNow(methodId, process))
        return;
    }
    public static void log(int methodId, String process, String name, String value) {
        if(returnNow(methodId, process))
        return;
    }

    public static Stack<MethodExecution> getExecuting() {
        return executing;
    }
}
