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
    }
    public static void log(int methodId, String process, String name, Object obj) {
    }

    public static void log(int methodId, String process, String name, byte value) {
    }

    public static void log(int methodId, String process, String name, short value) {

    }
    public static void log(int methodId, String process, String name, int value) {
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
