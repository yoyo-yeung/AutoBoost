package program.execution;

import entity.LOG_ITEM;
import entity.METHOD_TYPE;
import entity.UnrecognizableException;
import helper.Properties;
import org.apache.commons.lang3.ClassUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import program.analysis.MethodDetails;
import program.execution.variable.VarDetail;
import program.instrumentation.InstrumentResult;
import soot.VoidType;

import java.lang.reflect.Array;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ExecutionLogger {
    private static final Logger logger = LogManager.getLogger(ExecutionLogger.class);
    private static final ConcurrentHashMap<Long, Stack<MethodExecution>> threadExecutingMap = new ConcurrentHashMap<Long, Stack<MethodExecution>>();
    private static final InstrumentResult instrumentResult = InstrumentResult.getSingleton();
    private static final ExecutionTrace executionTrace = ExecutionTrace.getSingleton();
    private static final HashMap<Long, Boolean> threadSkippingMap = new HashMap<Long, Boolean>();
    private static boolean logging = false;



    /**
     * Method checking if the current operation and their corresponding values for the method should be stored.
     * Operation and values of sub-functions called under methods like "equals", "toString" and "hashCode" should not be processed and stored as these methods are also used during processing, hence processing of such methods would call itself and result in infinite loop.
     *
     * @param methodId ID of method invoked, corresponding MethodDetails can be found in InstrumentationResult
     * @param process  value of LOG_ITEM type, representing the status of method execution and the item being stored
     * @param threadID
     * @return true if the current operation and value should not be logged, false if they should
     */
    private static boolean returnNow(int methodId, LOG_ITEM process, long threadID) throws ClassNotFoundException {
        if (getCurrentExecuting(threadID).size() == 0 ) {
            setThreadSkippingState(threadID, false);
            return false;
        }
        if(Properties.getSingleton().getFaultyFuncIds().contains(methodId)) return false;
        MethodExecution latestExecution = getLatestExecution(threadID);
        if (latestExecution == null) {
            setThreadSkippingState(threadID, false);
            return false;
        }
        MethodDetails latestDetails = latestExecution.getMethodInvoked();

        if(!getThreadSkippingState(threadID) && latestDetails.getType().equals(METHOD_TYPE.CONSTRUCTOR)) {
            MethodDetails current = instrumentResult.getMethodDetailByID(methodId);
            if (current.getType().equals(METHOD_TYPE.CONSTRUCTOR) && current.getDeclaringClass() != latestDetails.getDeclaringClass() && !ClassUtils.getAllSuperclasses(Class.forName(latestDetails.getDeclaringClass().getName())).contains(Class.forName(current.getDeclaringClass().getName()))) {
                return false;
            } else return current.getType().equals(METHOD_TYPE.CONSTRUCTOR);
        }
        else return getThreadSkippingState(threadID);
    }

    // both instance and returnVal must be Object or else they would not be logged in the first place
    public static void logFieldAccess(int methodId, Object instance, Object returnVal, long threadID) throws ClassNotFoundException {
        if(!isLogging() || returnNow(methodId, LOG_ITEM.START, threadID)) return;
        MethodDetails details = instrumentResult.getMethodDetailByID(methodId);
        MethodExecution newExecution = new MethodExecution(executionTrace.getNewExeID(), details);
        Stack<MethodExecution> currentlyExecuting = getCurrentExecuting(threadID);
        // set test as null if static initializer is running
        if(currentlyExecuting.size() > 0 && currentlyExecuting.peek().getTest() == null) newExecution.setTest(null);
        updateExecutionRelationships(threadID, newExecution);
        // not putting in thread stack as it is instant access
        setVarForExecution(newExecution, LOG_ITEM.CALL_THIS, executionTrace.getVarDetail(newExecution, instance == null? Object.class: instance.getClass(), instance, LOG_ITEM.CALL_THIS, false));
        if(details.getReturnSootType() instanceof soot.PrimType)
            setVarIDForExecution(newExecution, LOG_ITEM.RETURN_ITEM, executionTrace.getVarDetail(newExecution, ClassUtils.wrapperToPrimitive(returnVal.getClass()), returnVal, LOG_ITEM.RETURN_ITEM, false).getID());
        else {
            setVarIDForExecution(newExecution, LOG_ITEM.RETURN_ITEM, executionTrace.getVarDetail(newExecution, (returnVal == null ? Object.class : returnVal.getClass()), returnVal, LOG_ITEM.RETURN_ITEM, false).getID());
        }
        endLogMethod(threadID, newExecution);
    }

    public static int logStart(int methodId, Object callee, Object params, long threadID) throws ClassNotFoundException {
         if(!isLogging() || returnNow(methodId, LOG_ITEM.START, threadID)) return -1;
        MethodDetails details = instrumentResult.getMethodDetailByID(methodId);
        MethodExecution newExecution = new MethodExecution(executionTrace.getNewExeID(), details);
        Stack<MethodExecution> currentExecuting = getCurrentExecuting(threadID);
//        logger.debug(details.getSignature());
        if (details.getType().equals(METHOD_TYPE.STATIC_INITIALIZER) || (currentExecuting.size() > 0 && currentExecuting.peek().getTest() == null))
            newExecution.setTest(null);
        updateExecutionRelationships(threadID, newExecution);
        addExecutionToThreadStack(threadID, newExecution);
        if(details.getType().equals(METHOD_TYPE.MEMBER)) // i.e. have callee
            setVarForExecution(newExecution, LOG_ITEM.CALL_THIS, executionTrace.getVarDetail(newExecution, callee == null ? Object.class : callee.getClass(), callee, LOG_ITEM.CALL_THIS, false));
        if(details.getParameterCount() > 0 ) {
            if (Array.getLength(params) < details.getParameterCount())
                throw new RuntimeException("Illegal parameter provided for logging");
            IntStream.range(0, details.getParameterCount()).forEach(i -> {
                Object paramVal = Array.get(params, i);
                if (details.getParameterTypes().get(i) instanceof soot.PrimType)
                    setVarIDForExecution(newExecution, LOG_ITEM.CALL_PARAM, executionTrace.getVarDetail(newExecution, ClassUtils.wrapperToPrimitive(paramVal.getClass()), paramVal, LOG_ITEM.CALL_PARAM, false).getID());
                else
                    setVarIDForExecution(newExecution,  LOG_ITEM.CALL_PARAM, executionTrace.getVarDetail(newExecution, paramVal == null ? Object.class : paramVal.getClass(), paramVal, LOG_ITEM.CALL_PARAM, false).getID());
            });

        }
        updateSkipping(newExecution, threadID);
//        logger.debug("started " + newExecution.toDetailedString() + "\t" + threadID);
        return newExecution.getID();
    }


    public static void logEnd(int executionID, Object callee, Object returnVal, long threadID) throws ClassNotFoundException {
        if(!isLogging())
            return;
        if(executionID == -1) return;
        setThreadSkippingState(threadID, false);
        MethodExecution execution = getLatestExecution(threadID);
        if(execution == null) return;
        if (executionID != execution.getID()) {
            if (instrumentResult.isLibMethod(execution.getMethodInvoked().getId())) {
                while(instrumentResult.isLibMethod(execution.getMethodInvoked().getId()) && execution.getID() != executionID) {
                    getCurrentExecuting(threadID).pop();
                    executionTrace.changeVertex(execution.getID(), getLatestExecution(threadID).getID());
                    execution = getLatestExecution(threadID);
                }
            }
            else {
                Class<?> exceptionClass = execution.getExceptionClass() != null ? execution.getExceptionClass() : UnrecognizableException.class;
                while (execution.getID() != executionID) {
                    execution.setExceptionClass(exceptionClass);
                    endLogMethod(threadID, execution);
                    if(exceptionClass.equals(UnrecognizableException.class)) {
                        logger.error(execution.toDetailedString());
                        logger.error(getCurrentExecuting(threadID).stream().map(MethodExecution::toDetailedString).collect(Collectors.joining(",")));
                        logger.error(getCurrentExecuting(threadID).stream().filter(e -> e.getID()==executionID).findFirst().map(MethodExecution::toDetailedString).orElse("null"));
                    }
                    logger.debug("force end "+ execution.toDetailedString());
                    execution = getLatestExecution(threadID);
                }
            }
            logEnd(executionID, callee, returnVal, threadID);
        }
        else {
            MethodDetails details = execution.getMethodInvoked();
            if (!details.getReturnSootType().equals(VoidType.v()) && !(instrumentResult.isLibMethod(details.getId()) && returnVal == null)) {
                if(details.getReturnSootType() instanceof soot.PrimType)
                    setVarIDForExecution(execution, LOG_ITEM.RETURN_ITEM, executionTrace.getVarDetail(execution, ClassUtils.wrapperToPrimitive(returnVal.getClass()), returnVal, LOG_ITEM.RETURN_ITEM, false).getID());
                else {
                    setVarIDForExecution(execution, LOG_ITEM.RETURN_ITEM, executionTrace.getVarDetail(execution, (returnVal == null ? Object.class : returnVal.getClass()), returnVal, LOG_ITEM.RETURN_ITEM, false).getID());
                }
            }
            if(details.getType().equals(METHOD_TYPE.CONSTRUCTOR) || details.getType().equals(METHOD_TYPE.MEMBER))
                setVarIDForExecution(execution, LOG_ITEM.RETURN_THIS, executionTrace.getVarDetail(execution, callee ==null? Object.class : callee.getClass(), callee, LOG_ITEM.RETURN_THIS, false).getID());
            endLogMethod(threadID, execution);

        }

    }

    public static void logException(Object exception, long threadID) {
        if(!isLogging()) return;
        getLatestExecution(threadID).setExceptionClass(exception.getClass());
    }

    public static MethodExecution getLatestExecution(long threadID) {
        if(!threadExecutingMap.containsKey(threadID) || threadExecutingMap.get(threadID).empty()) throw new RuntimeException("Fail to get latest execution on thread");
        return threadExecutingMap.get(threadID).peek();
    }

    /**
     * called when method has finished logging
     */
    private static void endLogMethod(long threadID, MethodExecution execution) {
//        logger.debug("ended method " + execution.toDetailedString());
        Optional<MethodExecution> duplicate = executionTrace.getAllMethodExecs().values().stream().filter(execution::sameContent).findFirst();
        if(duplicate.isPresent()) {
//            logger.debug(execution.toDetailedString() +"\t" + duplicate.get().toDetailedString());
            executionTrace.replacePossibleDefExe(execution, duplicate.get());
            executionTrace.changeVertex(execution.getID(), duplicate.get().getID());
        }
        else {
            executionTrace.updateFinishedMethodExecution(execution);
        }
        removeExecutionFromStack(threadID, execution);
    }

    private static void setVarIDForExecution(MethodExecution execution, LOG_ITEM process, int ID) {
        switch (process) {
//            case CALL_THIS:
//                execution.setCalleeId(ID);
//                break;
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

    private static void setVarForExecution(MethodExecution execution, LOG_ITEM process, VarDetail varDetail) {
        switch (process) {
            case CALL_THIS:
                execution.setCallee(varDetail);
                break;
            default:
                throw new RuntimeException("Invalid value provided for process " + process);
        }
    }

    public static void clearExecutingStack() {
        threadExecutingMap.clear();
    }

    private static void updateSkipping(MethodExecution execution, long threadID) {
        if(getThreadSkippingState(threadID)) return;
        Stack<MethodExecution> executionStack = getCurrentExecuting(threadID);
        if(( executionStack.size() >  1 && executionStack.stream().limit(executionStack.size() - 1).filter(e -> e.getMethodInvoked().equals(execution.getMethodInvoked())).anyMatch(e -> e.getTest() != null && e.sameCalleeParamNMethod(execution)) )|| executionTrace.getAllMethodExecs().values().stream()
                .filter(e -> e.getMethodInvoked().equals(execution.getMethodInvoked()))
                .anyMatch(e -> e.getTest() != null && e.sameCalleeParamNMethod(execution))) {
            setThreadSkippingState(threadID, true);
//            logger.debug("setting skipping as true since " + execution.toDetailedString());
        }
    }

    private static void updateExecutionRelationships(long threadID, MethodExecution execution) {
        Stack<MethodExecution> executionStack = getCurrentExecuting(threadID);
        executionTrace.addMethodExecution(execution);
        if (executionStack.size() != 0)
            executionTrace.addMethodRelationship(executionStack.peek().getID(), execution.getID());
    }

    private static void addExecutionToThreadStack(long threadID, MethodExecution execution) {
        threadExecutingMap.putIfAbsent(threadID, new Stack<>());
        threadExecutingMap.get(threadID).push(execution);
    }

    private static void removeExecutionFromStack(long threadID, MethodExecution execution) {
        threadExecutingMap.get(threadID).removeIf(e -> e.getID()==execution.getID());
        if(threadExecutingMap.get(threadID).size() == 0 ) threadExecutingMap.remove(threadID);
    }

    private static Stack<MethodExecution> getCurrentExecuting(long threadID) {
        threadExecutingMap.putIfAbsent(threadID, new Stack<>());
        return threadExecutingMap.get(threadID);
    }

    private static boolean getThreadSkippingState(long threadID) {
        threadSkippingMap.putIfAbsent(threadID, false);
        return threadSkippingMap.get(threadID);
    }

    private static void setThreadSkippingState(long threadID, boolean val) {
        threadSkippingMap.put(threadID, val);
    }

    static List<MethodExecution> getAllExecuting() {
        return threadExecutingMap.values().stream().flatMap(v -> new ArrayList<>(v).stream()).collect(Collectors.toList());
    }

    public static boolean isLogging() {
        return logging;
    }

    public static void setLogging(boolean logging) {
        ExecutionLogger.logging = logging;
    }
}
