package program.execution;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import entity.LOG_ITEM;
import org.apache.commons.lang3.ClassUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import program.analysis.MethodDetails;
import program.execution.variable.ObjVarDetails;
import program.execution.variable.PrimitiveVarDetails;
import program.execution.variable.StringVarDetails;
import program.execution.variable.VarDetail;
import program.instrumentation.InstrumentResult;

import java.util.Arrays;
import java.util.Stack;

public class ExecutionLogger {
    private static final Logger logger = LogManager.getLogger(ExecutionLogger.class);
    private static final Stack<MethodExecution> executing = new Stack<>();
    private static final String[] skipMethods = {"equals", "toString", "hashCode"};
    private static int sameMethodCount = 0; // this variable is used for keeping track of no. of methods, sharing same methodId with the top one in stack, not logged but processing
    private static final Gson gson = new GsonBuilder().addSerializationExclusionStrategy(new ExclusionStrategy() {
        @Override
        public boolean shouldSkipField(FieldAttributes fieldAttributes) {
            if(Arrays.stream(fieldAttributes.getDeclaringClass().getDeclaredFields()).filter(field -> fieldAttributes.getName().equals(field.getName())).count()>1)
                return true;
            return false;
        }
        @Override
        public boolean shouldSkipClass(Class<?> aClass) {
            return aClass.equals(java.text.DecimalFormat.class);
        }
    }).create();

    /**
     * Invoked when a method has started its execution. the method would be added to stack for further processing
     * @param methodId ID of method currently being invoked
     * @param process LOG_ITEM value representing status of method call
     */
    public static void start(int methodId, String process) {
        if(!LOG_ITEM.START_CALL.equals(LOG_ITEM.valueOf(process)))
            throw new IllegalArgumentException("Unacceptable process for current operation ");
        // skip logging of method if it is a method call enclosed
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
        InstrumentResult result = InstrumentResult.getSingleton();
        if(executing == null || executing.size() == 0)
            return false;
        MethodExecution latestExecution = getLatestExecution();
        if(latestExecution == null)
            return false;
        int latestID = latestExecution.getMethodInvokedId();
        MethodDetails latestDetails = result.getMethodDetailByID(latestID);
        // if the method logged most recently is to be skipped, OR it is a sub-method of a method stored before (prevent infinite loop)
        if(Arrays.stream(skipMethods).anyMatch(m -> m.equals(latestDetails.getName())) || executing.stream().filter(e -> e.sameCalleeParamNMethod(latestExecution)).count() > 1 ) {
            if (latestID != methodId)
                return true;
            switch (LOG_ITEM.valueOf(process)) {
                // if the current method to be logged is same as the latest one, add to count to prevent inconsistent popping
                case START_CALL:
                    sameMethodCount++;
                    break;
                // if the current method trying to be logged is the one to be skipped + it is "return-ing", then remove it from stack and go back to logging as expected
                case RETURN_VOID:
                case RETURN_ITEM:
                    if (sameMethodCount == 0) executing.pop();
                    else sameMethodCount--;
            }
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
            String objValue = gson.toJson(obj);
            int ID = trace.getVarDetailID(obj.getClass(), objValue);
            VarDetail varDetails;
            if(ID == -1) {
                ID = trace.getNewVarID();
                varDetails = new ObjVarDetails(ID, obj.getClass(), objValue);
                trace.addNewVarDetail(varDetails, executing.peek().getID());
            }
            else
                trace.addVarDetailUsage(ID, executing.peek().getID());
            setVarIDforExecutions(methodId, process, ID);
        }

    }

    private static void logPrimitive(int methodId, String process, Class<?> c, Object value) {
        if(returnNow(methodId, process))
            return;
        int varID = ExecutionTrace.getSingleton().getVarDetailID(c, value);
        if(varID == -1 ) {
            try {
                PrimitiveVarDetails varDetails = new PrimitiveVarDetails(ExecutionTrace.getSingleton().getNewVarID(), c, value);
                ExecutionTrace.getSingleton().addNewVarDetail(varDetails, getLatestExecution().getID());
                varID = varDetails.getID();
            } catch (NoSuchFieldException | IllegalAccessException e) {
                logger.error(e.getLocalizedMessage());
                throw new RuntimeException("Failed to create PrimitiveVarDetail with value provided");
            }
        } else {
            ExecutionTrace.getSingleton().addVarDetailUsage(varID, getLatestExecution().getID());
        }
        setVarIDforExecutions(methodId, process, varID);
    }
    public static void log(int methodId, String process, String name, byte value) {
        logPrimitive(methodId, process, byte.class, (Byte)value);
    }

    public static void log(int methodId, String process, String name, short value) {
        logPrimitive(methodId, process, short.class, (Short)value);
    }
    public static void log(int methodId, String process, String name, int value) {
        logPrimitive(methodId, process, int.class, (Integer)value);
    }
    public static void log(int methodId, String process, String name, long value) {
        logPrimitive(methodId, process, long.class, (Long)value);
    }
    public static void log(int methodId, String process, String name, float value) {
        logPrimitive(methodId, process, float.class, (Float)value);
    }
    public static void log(int methodId, String process, String name, double value) {
        logPrimitive(methodId, process, double.class, (Double)value);
    }
    public static void log(int methodId, String process, String name, boolean value) {
        logPrimitive(methodId, process, boolean.class, (Boolean)value);
    }
    public static void log(int methodId, String process, String name, char value) {
        logPrimitive(methodId, process, char.class, (Character)value);
    }
    public static void log(int methodId, String process, String name, String value) {
        MethodExecution latestExecution = getLatestExecution();
        if(returnNow(methodId, process))
            return;
        if(value == null || process.equals(LOG_ITEM.RETURN_VOID.toString())) {
            setVarIDforExecutions(methodId, process, -1);
            return;
        }
        int varID = ExecutionTrace.getSingleton().getVarDetailID(String.class, value);
        if (varID == -1) {
            StringVarDetails varDetails = new StringVarDetails(ExecutionTrace.getSingleton().getNewVarID(), value);
            ExecutionTrace.getSingleton().addNewVarDetail(varDetails, latestExecution.getID());
            varID = varDetails.getID();
        } else ExecutionTrace.getSingleton().addVarDetailUsage(varID, latestExecution.getID());
        setVarIDforExecutions(methodId, process, varID);

    }

    public static Stack<MethodExecution> getExecuting() {
        return executing;
    }

    public static MethodExecution getLatestExecution() {return executing.peek();}

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
        MethodExecution execution = getLatestExecution();
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
