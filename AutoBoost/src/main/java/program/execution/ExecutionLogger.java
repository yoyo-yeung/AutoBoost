package program.execution;

import entity.LOG_ITEM;
import entity.METHOD_TYPE;
import helper.Properties;
import org.apache.commons.lang3.ClassUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import program.analysis.MethodDetails;
import program.instrumentation.InstrumentResult;

import java.lang.reflect.Array;
import java.util.Stack;
import java.util.stream.IntStream;

public class ExecutionLogger {
    private static final Logger logger = LogManager.getLogger(ExecutionLogger.class);
    private static final Stack<MethodExecution> executing = new Stack<>();
    private static final InstrumentResult instrumentResult = InstrumentResult.getSingleton();
    private static final ExecutionTrace executionTrace = ExecutionTrace.getSingleton();
    private static int sameMethodCount = 0; // this variable is used for keeping track of no. of methods, sharing same methodId with the top one in stack, not logged but processing
    private static boolean skipping = false;


    /**
     * Invoked when a method has started its execution. the method would be added to stack for further processing
     *
     * @param methodId ID of method currently being invoked
     * @param process  LOG_ITEM value representing status of method call
     */
    public static void start(int methodId, String process) throws ClassNotFoundException {
        if (!LOG_ITEM.START_CALL.equals(LOG_ITEM.valueOf(process)))
            throw new IllegalArgumentException("Unacceptable process for current operation ");
        // skip logging of method if it is a method call enclosed
        if (returnNow(methodId, process))
            return;
        MethodExecution newExecution = new MethodExecution(executionTrace.getNewExeID(), methodId);
        MethodDetails details = instrumentResult.getMethodDetailByID(methodId);
        if (details.getType().equals(METHOD_TYPE.STATIC_INITIALIZER) || (executing.size() > 0 && executing.peek().getTest() == null))
            newExecution.setTest(null);
        executing.add(newExecution);
        if((details.getType().equals(METHOD_TYPE.CONSTRUCTOR) || details.getType().equals(METHOD_TYPE.STATIC)) && details.getParameterCount() == 0 )
            setSkipping(newExecution);

    }

    /**
     * Method checking if the current operation and their corresponding values for the method should be stored.
     * Operation and values of sub-functions called under methods like "equals", "toString" and "hashCode" should not be processed and stored as these methods are also used during processing, hence processing of such methods would call itself and result in infinite loop.
     *
     * @param methodId ID of method invoked, corresponding MethodDetails can be found in InstrumentationResult
     * @param process  value of LOG_ITEM type, representing the status of method execution and the item being stored
     * @return true if the current operation and value should not be logged, false if they should
     */
    private static boolean returnNow(int methodId, String process) throws ClassNotFoundException {
        if (executing.size() == 0 ) {
            skipping = false;
            return false;
        }
        if(Properties.getSingleton().getFaultyFuncIds().contains(methodId)) return false;
        LOG_ITEM processItem = LOG_ITEM.valueOf(process);
        if (processItem.equals(LOG_ITEM.THREW_EXCEPTION)) return false;
        MethodExecution latestExecution = getLatestExecution();
        if (latestExecution == null) {
            skipping = false;
            return false;
        }
        int latestID = latestExecution.getMethodInvokedId();
        MethodDetails latestDetails = instrumentResult.getMethodDetailByID(latestID);
        if(!skipping && !latestDetails.getType().equals(METHOD_TYPE.CONSTRUCTOR))
            return skipping;
        if (skipping && !(processItem.equals(LOG_ITEM.START_CALL) && methodId == latestID) && !(processItem.equals(LOG_ITEM.RETURN_ITEM) && methodId == latestID) && !(processItem.equals(LOG_ITEM.RETURN_VOID) && methodId == latestID))
            return skipping;

        if(skipping) {
            switch (processItem) {
                // if the current method to be logged is same as the latest one, add to count to prevent inconsistent popping
                case START_CALL:
                    sameMethodCount++;
                    break;
                // if the current method trying to be logged is the one to be skipped + it is "return-ing", then remove it from stack and go back to logging as expected
                case RETURN_VOID:
                case RETURN_ITEM:
                    if (sameMethodCount == 0) {
                        skipping = false;
                        executing.pop();
                        if (executing.size() > 0)
                            ExecutionTrace.getSingleton().changeVertex(latestExecution.getID(), executing.peek().getID());
                        else ExecutionTrace.getSingleton().removeVertex(latestExecution.getID());
                        return true;
                    } else sameMethodCount--;
            }
            skipping = true;
            return true;
        }
        if (latestDetails.getType().equals(METHOD_TYPE.CONSTRUCTOR)) {
            MethodDetails current = instrumentResult.getMethodDetailByID(methodId);
            skipping = false;
            if (latestID != methodId) {
                if (current.getType().equals(METHOD_TYPE.CONSTRUCTOR) && current.getDeclaringClass() != latestDetails.getDeclaringClass() && !ClassUtils.getAllSuperclasses(Class.forName(latestDetails.getDeclaringClass().getName())).contains(Class.forName(current.getDeclaringClass().getName()))) {
                    return false;
                } else return current.getType().equals(METHOD_TYPE.CONSTRUCTOR);
            }
        }
        skipping = false;
        return false;
    }

    public static void log(int methodId, String process, Object obj) throws ClassNotFoundException {
        LOG_ITEM processItem = LOG_ITEM.valueOf(process);
        if (returnNow(methodId, process)) {
            return;
        }
        switch (processItem) {
            case THREW_EXCEPTION:
                getLatestExecution().setExceptionClass(obj.getClass());
                break;
            case RETURN_VOID:
                setVarIDforExecutions(methodId, process, -1);
                break;
            case CALL_PARAM:
                MethodDetails details = InstrumentResult.getSingleton().getMethodDetailByID(getLatestExecution().getMethodInvokedId());
                if (Array.getLength(obj) < details.getParameterCount())
                    throw new RuntimeException("Illegal parameter provided for logging");
                IntStream.range(0, details.getParameterCount()).forEach(i -> {
                    if (details.getParameterTypes().get(i) instanceof soot.PrimType)
                        setVarIDforExecutions(methodId, process, executionTrace.getVarDetailID(ClassUtils.wrapperToPrimitive(Array.get(obj, i).getClass()), Array.get(obj, i), processItem));
                    else
                        setVarIDforExecutions(methodId, process, executionTrace.getVarDetailID(Array.get(obj, i) == null ? Object.class : Array.get(obj, i).getClass(), Array.get(obj, i), processItem));
                });
                break;
            default:
                setVarIDforExecutions(methodId, process, executionTrace.getVarDetailID(obj == null ? Object.class : obj.getClass(), obj, processItem));
        }

    }

    private static void logPrimitive(int methodId, String process, Class<?> c, Object value) throws ClassNotFoundException {
        if (returnNow(methodId, process))
            return;
        setVarIDforExecutions(methodId, process, executionTrace.getVarDetailID(c, value, LOG_ITEM.valueOf(process)));
    }

    public static void log(int methodId, String process, byte value) throws ClassNotFoundException {
        logPrimitive(methodId, process, byte.class, value);
    }

    public static void log(int methodId, String process, short value) throws ClassNotFoundException {
        logPrimitive(methodId, process, short.class, value);
    }

    public static void log(int methodId, String process, int value) throws ClassNotFoundException {
        logPrimitive(methodId, process, int.class, value);
    }

    public static void log(int methodId, String process, long value) throws ClassNotFoundException {
        logPrimitive(methodId, process, long.class, value);
    }

    public static void log(int methodId, String process, float value) throws ClassNotFoundException {
        logPrimitive(methodId, process, float.class, value);
    }

    public static void log(int methodId, String process, double value) throws ClassNotFoundException {
        logPrimitive(methodId, process, double.class, value);
    }

    public static void log(int methodId, String process, boolean value) throws ClassNotFoundException {
        logPrimitive(methodId, process, boolean.class, value);
    }

    public static void log(int methodId, String process, char value) throws ClassNotFoundException {
        logPrimitive(methodId, process, char.class, value);
    }

    public static void log(int methodId, String process, String value) throws ClassNotFoundException {
        if (returnNow(methodId, process))
            return;
        if (LOG_ITEM.valueOf(process).equals(LOG_ITEM.RETURN_VOID)) {
            setVarIDforExecutions(methodId, process, -1);
            return;
        }
        setVarIDforExecutions(methodId, process, executionTrace.getVarDetailID(String.class, value, LOG_ITEM.valueOf(process)));

    }

    public static Stack<MethodExecution> getExecuting() {
        return executing;
    }

    public static MethodExecution getLatestExecution() {
        return executing.peek();
    }

    /**
     * called when method has finished logging
     */
    private static void endLogMethod(int methodId) {
        if (methodId != executing.peek().getMethodInvokedId())
            throw new RuntimeException("Method finishing logging does not match stored method");
        MethodExecution finishedMethod = executing.pop();
//        if(!finishedMethod.relationshipCheck())
//            throw new RuntimeException("Method finished incorrect. ");
        executionTrace.addMethodExecution(finishedMethod, methodId);
        // some method called the finishedMethod
        if (executing.size() != 0)
            executionTrace.addMethodRelationship(executing.peek().getID(), finishedMethod.getID());
    }

    /**
     * This method is used for updating values of the method under execution.
     * For use inside class only
     *
     * @param methodId ID of method being processed
     * @param process  LOG_ITEM type, enclose current status of method and the type of item to store
     * @param ID       ID of the VarDetail object to store
     */
    private static void setVarIDforExecutions(int methodId, String process, int ID) {
        MethodExecution execution = getLatestExecution();
        if (methodId != execution.getMethodInvokedId()) {
            if (execution.getExceptionClass() != null) {
                Class<?> exceptionClass = execution.getExceptionClass();
                while (execution.getMethodInvokedId() != methodId) {
                    execution.setExceptionClass(exceptionClass);
                    endLogMethod(execution.getMethodInvokedId());
                    execution = getLatestExecution();
                }
            }
            else if (instrumentResult.isLibMethod(execution.getMethodInvokedId())) {
                while(instrumentResult.isLibMethod(execution.getMethodInvokedId())) {
                    executing.pop();
                    execution = getLatestExecution();
                }
            }
            else {
                throw new RuntimeException("Inconsistent method invoke");
            }
        }
        MethodDetails details = instrumentResult.getMethodDetailByID(methodId);
        switch (LOG_ITEM.valueOf(process)) {
            case CALL_THIS:
                execution.setCalleeId(ID);
                if(details.getParameterCount() == 0)
                    setSkipping(execution);
                break;
            case CALL_PARAM:
                execution.addParam(ID);
                setSkipping(execution);
                break;
            case RETURN_THIS:
                execution.setResultThisId(ID);
                break;
            case RETURN_VOID:
                ID = -1;
            case RETURN_ITEM:
                execution.setReturnValId(ID);
                endLogMethod(methodId);
                break;
            default:
                throw new RuntimeException("Invalid value provided for process " + process);
        }
//        logger.debug("set " + execution.toDetailedString());
    }


    public static void clearExecutingStack() {
        executing.clear();
    }

    private static void setSkipping(MethodExecution execution) {
        if(skipping) return;
        if(( executing.size() >  1 && executing.stream().limit(executing.size() - 1).filter(e -> e.getMethodInvokedId() == execution.getMethodInvokedId()).anyMatch(e -> e.getTest() != null && e.sameCalleeParamNMethod(execution)) )||  executionTrace.
                getAllMethodExecs().values().stream()
                .filter(e -> e.getMethodInvokedId() == execution.getMethodInvokedId())
                .anyMatch(e -> e.getTest() != null && e.sameCalleeParamNMethod(execution)))
            skipping = true;
    }
}
