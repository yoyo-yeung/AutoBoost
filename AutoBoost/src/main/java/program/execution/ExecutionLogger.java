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
        if(!LOG_ITEM.START_CALL.equals(LOG_ITEM.valueOf(process)))
            throw new IllegalArgumentException("Unacceptable process for current operation ");
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
        if(obj == null || process.equals(LOG_ITEM.RETURN_VOID.toString())) {
            setVarIDforExecutions(methodId, process, -1);
            return;
        }
        if (obj.getClass().isArray()){
        }
        else if(ClassUtils.isPrimitiveWrapper(obj.getClass())){}
        else {
            int ID = trace.getObjVarDetailsID(obj);
            VarDetail varDetails;
            if(ID == -1) {
                ID = trace.getNewVarID();
                varDetails = new ObjVarDetails(ID, obj);
                trace.addNewVarDetail(varDetails, executing.peek().getID());
            }
            setVarIDforExecutions(methodId, process, ID);
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
        if(value == null || process.equals(LOG_ITEM.RETURN_VOID.toString())) {
            setVarIDforExecutions(methodId, process, -1);
            return;
        }
    }

    public static Stack<MethodExecution> getExecuting() {
        return executing;
    }

    /**
     * called when method has finished logging
     */
    private static void endLogMethod(int methodId) {
        if(methodId!=executing.peek().getMethodInvokedId())
            throw new RuntimeException("Method finishing logging does not match stored method");
        MethodExecution finishedMethod = executing.pop();
        ExecutionTrace executionTrace = ExecutionTrace.getSingleton();
        executionTrace.addMethodExecution(finishedMethod);
        // some method called the finishedMethod
        if(executing.size()!=0)
            executionTrace.addMethodRelationship(executing.peek().getID(), finishedMethod.getID());
    }

    /**
     * This method is used for updating values of the method under execution.
     * For use inside class only
     * @param methodId ID of method being processed
     * @param process LOG_ITEM type, enclose current status of method and the type of item to store
     * @param ID ID of the VarDetail object to store
     */
    private static void setVarIDforExecutions(int methodId, String process, int ID) {
        MethodExecution execution = executing.peek();
        if( methodId != execution.getMethodInvokedId() )
            throw new RuntimeException("Method processing does NOT match stored method ");
        switch (LOG_ITEM.valueOf(process)) {
            case CALL_THIS:
               execution.setCalleeId(ID);
                break;
            case CALL_PARAM:
               execution.addParam(ID);
                break;
            case RETURN_THIS:
               execution.setResultThisId(ID);
                break;
            case RETURN_VOID:
                ID = -1;
            case RETURN_ITEM:
               execution.setReturnValId(ID);
//               execution.relationshipCheck();
                endLogMethod(methodId);
                break;
            default:
                throw new RuntimeException("Invalid value provided for process " + process);
        }
    }
}
