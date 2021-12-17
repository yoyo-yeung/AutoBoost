package program.execution;

import entity.LOG_ITEM;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ExecutionTrace {
    private static Logger logger = LogManager.getLogger(ExecutionTrace.class);



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
    private static boolean isWrapperClass(Class c) {
        return c.equals(Byte.class) || c.equals(Short.class) || c.equals(Integer.class) || c.equals(Long.class) || c.equals(Float.class) || c.equals(Double.class) || c.equals(Boolean.class) || c.equals(Character.class);
    }

}
