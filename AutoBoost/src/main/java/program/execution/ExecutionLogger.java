package program.execution;

import entity.LOG_ITEM;
import entity.METHOD_TYPE;
import helper.Properties;
import org.apache.commons.lang3.ClassUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import program.analysis.MethodDetails;
import program.instrumentation.InstrumentResult;
import soot.VoidType;

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
     * Method checking if the current operation and their corresponding values for the method should be stored.
     * Operation and values of sub-functions called under methods like "equals", "toString" and "hashCode" should not be processed and stored as these methods are also used during processing, hence processing of such methods would call itself and result in infinite loop.
     *
     * @param methodId ID of method invoked, corresponding MethodDetails can be found in InstrumentationResult
     * @param process  value of LOG_ITEM type, representing the status of method execution and the item being stored
     * @return true if the current operation and value should not be logged, false if they should
     */
    private static boolean returnNow(int methodId, LOG_ITEM process) throws ClassNotFoundException {
        if (executing.size() == 0 ) {
            skipping = false;
            return false;
        }
        if(Properties.getSingleton().getFaultyFuncIds().contains(methodId)) return false;
        MethodExecution latestExecution = getLatestExecution();
        if (latestExecution == null) {
            skipping = false;
            return false;
        }
        int latestID = latestExecution.getMethodInvokedId();
        MethodDetails latestDetails = instrumentResult.getMethodDetailByID(latestID);
        if(!skipping && !latestDetails.getType().equals(METHOD_TYPE.CONSTRUCTOR))
            return skipping;
        if (skipping && !((process.equals(LOG_ITEM.START)) && methodId == latestID) && !(process.equals(LOG_ITEM.RETURN) && methodId == latestID))
            return true;

        if(skipping) {
            switch (process) {
                // if the current method to be logged is same as the latest one, add to count to prevent inconsistent popping
                case START:
                    sameMethodCount++;
                    break;
                // if the current method trying to be logged is the one to be skipped + it is "return-ing", then remove it from stack and go back to logging as expected
                case RETURN:
                    if (sameMethodCount == 0) {
                        skipping = false;
                        executing.pop();
                        if (executing.size() > 0)
                            ExecutionTrace.getSingleton().changeVertex(latestExecution.getID(), executing.peek().getID());
                        else ExecutionTrace.getSingleton().removeVertex(latestExecution.getID());
                        return true;
                    } else sameMethodCount--;
            }
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


    public static void logStart(int methodId, Object callee, Object params) throws ClassNotFoundException {
         if(returnNow(methodId, LOG_ITEM.START)) return;
        MethodExecution newExecution = new MethodExecution(executionTrace.getNewExeID(), methodId);
        MethodDetails details = instrumentResult.getMethodDetailByID(methodId);
        if (details.getType().equals(METHOD_TYPE.STATIC_INITIALIZER) || (executing.size() > 0 && executing.peek().getTest() == null))
            newExecution.setTest(null);
        executing.add(newExecution);
        if(details.getType().equals(METHOD_TYPE.MEMBER)) // i.e. have callee
            setVarIDForExecution(methodId, newExecution, LOG_ITEM.CALL_THIS, executionTrace.getVarDetailID(callee == null ? Object.class : callee.getClass(), callee, LOG_ITEM.CALL_THIS));
        if(details.getParameterCount() > 0 ) {
            if (Array.getLength(params) < details.getParameterCount())
                throw new RuntimeException("Illegal parameter provided for logging");
            IntStream.range(0, details.getParameterCount()).forEach(i -> {
                Object paramVal = Array.get(params, i);
                if (details.getParameterTypes().get(i) instanceof soot.PrimType)
                    setVarIDForExecution(methodId, newExecution, LOG_ITEM.CALL_PARAM, executionTrace.getVarDetailID(ClassUtils.wrapperToPrimitive(paramVal.getClass()), paramVal, LOG_ITEM.CALL_PARAM));
                else
                    setVarIDForExecution(methodId, newExecution,  LOG_ITEM.CALL_PARAM, executionTrace.getVarDetailID(paramVal == null ? Object.class : paramVal.getClass(), paramVal, LOG_ITEM.CALL_PARAM));
            });

        }
        updateSkipping(newExecution);
    }


    public static void logEnd(int methodId, Object callee, Object returnVal) throws ClassNotFoundException {
        if(returnNow(methodId, LOG_ITEM.RETURN))
            return;
        MethodExecution execution = getLatestExecution();
        if (methodId != execution.getMethodInvokedId()) {
            logger.debug(execution.toDetailedString());
            logger.debug(instrumentResult.getMethodDetailByID(methodId).toString());
            if (execution.getExceptionClass() != null) {
                Class<?> exceptionClass = execution.getExceptionClass();
                while (execution.getMethodInvokedId() != methodId) {
                    execution.setExceptionClass(exceptionClass);
                    endLogMethod(execution);
                    execution = getLatestExecution();
                }
            }
            else if (instrumentResult.isLibMethod(execution.getMethodInvokedId())) {
                while(instrumentResult.isLibMethod(execution.getMethodInvokedId()) && execution.getMethodInvokedId() != methodId) {
                    executing.pop();
                    execution = getLatestExecution();
                }
            }
            else {
                throw new RuntimeException("Inconsistent method invoke");
            }
            logEnd(methodId, callee, returnVal);
        }
        else {
            MethodDetails details = instrumentResult.getMethodDetailByID(methodId);
            if (!details.getReturnSootType().equals(VoidType.v()) && !(instrumentResult.isLibMethod(methodId) && returnVal == null)) {
                if(details.getReturnSootType() instanceof soot.PrimType)
                    setVarIDForExecution(methodId, execution, LOG_ITEM.RETURN_ITEM, executionTrace.getVarDetailID(ClassUtils.wrapperToPrimitive(returnVal.getClass()), returnVal, LOG_ITEM.RETURN_ITEM));
                else
                    setVarIDForExecution(methodId, execution, LOG_ITEM.RETURN_ITEM, executionTrace.getVarDetailID(returnVal == null ? Object.class: returnVal.getClass(), returnVal, LOG_ITEM.RETURN_ITEM));
            }
            if(details.getType().equals(METHOD_TYPE.CONSTRUCTOR) || details.getType().equals(METHOD_TYPE.MEMBER))
                setVarIDForExecution(methodId, execution, LOG_ITEM.RETURN_THIS, executionTrace.getVarDetailID(callee ==null? Object.class : callee.getClass(), callee, LOG_ITEM.RETURN_THIS));
            endLogMethod(execution);

        }

    }

    public static void logException(Object exception) {
        getLatestExecution().setExceptionClass(exception.getClass());
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
    private static void endLogMethod(MethodExecution execution) {
        logger.debug("ended method " + execution.toSimpleString());
//        if(!finishedMethod.relationshipCheck())
//            throw new RuntimeException("Method finished incorrect. ");
        executionTrace.addMethodExecution(execution, execution.getMethodInvokedId());
        executing.remove(execution);
        if (executing.size() != 0)
            executionTrace.addMethodRelationship(executing.peek().getID(), execution.getID());
    }

    private static void setVarIDForExecution(int methodID, MethodExecution execution, LOG_ITEM process, int ID) {
        switch (process) {
            case CALL_THIS:
                execution.setCalleeId(ID);
                break;
            case CALL_PARAM:
                execution.addParam(ID);
                break;
            case RETURN_THIS:
                execution.setResultThisId(ID);
                break;
            case RETURN_ITEM:
                execution.setReturnValId(ID);
                break;
            default:
                throw new RuntimeException("Invalid value provided for process " + process);
        }
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
            logger.debug(execution.toDetailedString());
            logger.debug(instrumentResult.getMethodDetailByID(methodId).toString());
            if (execution.getExceptionClass() != null) {
                Class<?> exceptionClass = execution.getExceptionClass();
                while (execution.getMethodInvokedId() != methodId) {
                    execution.setExceptionClass(exceptionClass);
                    endLogMethod(execution);
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
        setVarIDForExecution(methodId, execution, LOG_ITEM.valueOf(process), ID);
//        logger.debug("set " + execution.toDetailedString());
    }


    public static void clearExecutingStack() {
        executing.clear();
    }

    private static void updateSkipping(MethodExecution execution) {
        if(skipping) return;
        if(( executing.size() >  1 && executing.stream().limit(executing.size() - 1).filter(e -> e.getMethodInvokedId() == execution.getMethodInvokedId()).anyMatch(e -> e.getTest() != null && e.sameCalleeParamNMethod(execution)) )||  executionTrace.
                getAllMethodExecs().values().stream()
                .filter(e -> e.getMethodInvokedId() == execution.getMethodInvokedId())
                .anyMatch(e -> e.getTest() != null && e.sameCalleeParamNMethod(execution))) {
            skipping = true;
            logger.debug("setting skipping as true since " + execution.toDetailedString());
        }
    }
}
