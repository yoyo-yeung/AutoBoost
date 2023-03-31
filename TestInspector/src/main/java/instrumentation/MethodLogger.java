package instrumentation;

import com.google.common.primitives.Primitives;
import fj.P;
import helper.Properties;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class MethodLogger {
    private static final Logger logger = LogManager.getLogger(MethodLogger.class);
    private static HashMap<String, List<List<Map.Entry<String, String>>>> methodParamValueMap = new HashMap<>();
    private static List<Map.Entry<String, String>> pending = new ArrayList<>();
    private static List<String> methodCallOrder = new ArrayList<>();

    public static void logMethod(String method) {
        methodCallOrder.add(method);
    }

    public static void logParam(String method, String paramName, String paramValue) {
        if (!methodParamValueMap.containsKey(method))
            methodParamValueMap.put(method, new ArrayList<>());
        List<List<Map.Entry<String, String>>> methodParamList = methodParamValueMap.get(method);

//        paramValue = paramValue.replaceAll("\\@\\w+\\[", "[");
        if(methodParamList.size()==0 || methodParamList.get(methodParamList.size()-1).stream().anyMatch(v -> v.getKey().equals(paramName))) {
            methodParamList.add(new ArrayList<>());
        }
        methodParamList.get(methodParamList.size()-1).add(new AbstractMap.SimpleEntry<String, String>(paramName, paramValue));
        methodParamValueMap.put(method, methodParamList);
//        logger.debug(methodParamValueMap);


    }

    public static void logParam(String method, String paramName, byte paramValue) {
        logParam(method, paramName, String.valueOf(paramValue));
    }

    public static void logParam(String method, String paramName, short paramValue) {
        logParam(method, paramName, String.valueOf(paramValue));
    }

    public static void logParam(String method, String paramName, boolean paramValue) {
        logParam(method, paramName, String.valueOf(paramValue));
    }

    public static void logParam(String method, String paramName, long paramValue) {
        logParam(method, paramName, String.valueOf(paramValue));
    }

    public static void logParam(String method, String paramName, int paramValue) {
        logParam(method, paramName, String.valueOf(paramValue));
    }

    public static void logParam(String method, String paramName, double paramValue) {
        logParam(method, paramName, String.valueOf(paramValue));
    }

    public static void logParam(String method, String paramName, float paramValue) {
        logParam(method, paramName, String.valueOf(paramValue));
    }

    public static void logParam(String method, String paramName, Object paramValue) {
//        logger.debug(paramValue.toString());
        if (paramValue == null)
            logParam(method, paramName, "null");
        else {
            logParam(method, paramName, ToStringBuilder.reflectionToString(paramValue));
//            Arrays.stream(paramValue.getClass().getDeclaredFields()).filter(f-> Arrays.stream(Properties.getSingleton().getCUTs()).anyMatch(n -> n.equals(f.getType().getName()))
//                    ).forEach(f-> {
//                try {
//                    f.setAccessible(true);
//                    logParam(paramName, f.getName(), f.get(paramValue)==null?"null":ToStringBuilder.reflectionToString(f.get(paramValue)));
//                } catch (IllegalAccessException e) {
//                    e.printStackTrace();
//                }
//            });
        }

//            Arrays.stream(paramValue.getClass().getDeclaredFields()).filter(f -> checkClassAsObject(f.getType())&& Modifier.toString(f.getModifiers()).contains("public")).forEach(f -> {
//                try {
//
//                    logParam(paramName, f.getName(), f.getType().isEnum()? f.get(paramValue).toString() : ToStringBuilder.reflectionToString(f.get(paramValue)));
//                } catch (IllegalAccessException e) {
//                    e.printStackTrace();
//                }
//
//            });



    }

    public static void logParam(String method, String paramName, char paramValue) {
        logParam(method, paramName, String.valueOf(paramValue));
    }

    public static void logParam(String method, String paramName, int[] paramValue) {
        if (paramValue == null)
            logParam(method, paramName, "null");
        else
            IntStream.range(0, paramValue.length).forEach(i -> logParam(method, paramName + "[" + i + "]", paramValue[i]));
    }

    public static void logParam(String method, String paramName, double[] paramValue) {
        if (paramValue == null)
            logParam(method, paramName, "null");
        else
            IntStream.range(0, paramValue.length).forEach(i -> logParam(method, paramName + "[" + i + "]", paramValue[i]));
    }

    public static void logParam(String method, String paramName, char[] paramValue) {
        if (paramValue == null)
            logParam(method, paramName, "null");
        else logParam(method, paramName, paramValue.toString());
    }

    public static void logParam(String method, String paramName, boolean[] paramValue) {
        if (paramValue == null)
            logParam(method, paramName, "null");
        else
            IntStream.range(0, paramValue.length).forEach(i -> logParam(method, paramName + "[" + i + "]", paramValue[i]));
    }

    public static void logParam(String method, String paramName, float[] paramValue) {
        if (paramValue == null)
            logParam(method, paramName, "null");
        else
            IntStream.range(0, paramValue.length).forEach(i -> logParam(method, paramName + "[" + i + "]", paramValue[i]));
    }

    public static void logParam(String method, String paramName, byte[] paramValue) {
        if (paramValue == null)
            logParam(method, paramName, "null");
        else
            IntStream.range(0, paramValue.length).forEach(i -> logParam(method, paramName + "[" + i + "]", paramValue[i]));
    }

    public static void logParam(String method, String paramName, long[] paramValue) {
        if (paramValue == null)
            logParam(method, paramName, "null");
        else
            IntStream.range(0, paramValue.length).forEach(i -> logParam(method, paramName + "[" + i + "]", paramValue[i]));
    }

    public static void logParam(String method, String paramName, short[] paramValue) {
        if (paramValue == null)
            logParam(method, paramName, "null");
        else
            IntStream.range(0, paramValue.length).forEach(i -> logParam(method, paramName + "[" + i + "]", paramValue[i]));
    }

    public static void logParam(String method, String paramName, int[][] paramValue) {
        if (paramValue == null)
            logParam(method, paramName, "null");
        else
            IntStream.range(0, paramValue.length).filter(i -> paramValue[i] != null).forEach(i -> IntStream.range(0, paramValue[i].length).forEach(x -> logParam(method, paramName + "[" + i + "][" + x + "]", paramValue[i][x])));
    }

    public static void logParam(String method, String paramName, float[][] paramValue) {
        if (paramValue == null)
            logParam(method, paramName, "null");
        else
            IntStream.range(0, paramValue.length).filter(i -> paramValue[i] != null).forEach(i -> IntStream.range(0, paramValue[i].length).forEach(x -> logParam(method, paramName + "[" + i + "][" + x + "]", paramValue[i][x])));
    }


    public static void logParam(String method, String paramName, double[][] paramValue) {
        if (paramValue == null)
            logParam(method, paramName, "null");
        else
            IntStream.range(0, paramValue.length).filter(i -> paramValue[i] != null).forEach(i -> IntStream.range(0, paramValue[i].length).forEach(x -> logParam(method, paramName + "[" + i + "][" + x + "]", paramValue[i][x])));
    }


    public static void logParam(String method, String paramName, short[][] paramValue) {
        if (paramValue == null)
            logParam(method, paramName, "null");
        else
            IntStream.range(0, paramValue.length).filter(i -> paramValue[i] != null).forEach(i -> IntStream.range(0, paramValue[i].length).forEach(x -> logParam(method, paramName + "[" + i + "][" + x + "]", paramValue[i][x])));
    }


    public static void logParam(String method, String paramName, long[][] paramValue) {
        if (paramValue == null)
            logParam(method, paramName, "null");
        else IntStream.range(0, paramValue.length).filter(i -> paramValue[i] != null)
                .forEach(i -> IntStream.range(0, paramValue[i].length)
                        .forEach(x -> logParam(method, paramName + "[" + i + "][" + x + "]", paramValue[i][x])));
    }

    public static void logParam(String method, String paramName, char[][] paramValue) {
        if (paramValue == null)
            logParam(method, paramName, "null");
        else
            IntStream.range(0, paramValue.length).filter(i -> paramValue[i] != null).forEach(i -> IntStream.range(0, paramValue[i].length).forEach(x -> logParam(method, paramName + "[" + i + "][" + x + "]", paramValue[i][x])));
    }

    public static void logParam(String method, String paramName, byte[][] paramValue) {
        if (paramValue == null)
            logParam(method, paramName, "null");
        else
            IntStream.range(0, paramValue.length).filter(i -> paramValue[i] != null).forEach(i -> IntStream.range(0, paramValue[i].length).forEach(x -> logParam(method, paramName + "[" + i + "][" + x + "]", paramValue[i][x])));
    }

    public static void logParam(String method, String paramName, boolean[][] paramValue) {
        if (paramValue == null)
            logParam(method, paramName, "null");
        else
            IntStream.range(0, paramValue.length).filter(i -> paramValue[i] != null).forEach(i -> IntStream.range(0, paramValue[i].length).forEach(x -> logParam(method, paramName + "[" + i + "][" + x + "]", paramValue[i][x])));
    }

    public static HashMap<String, List<List<Map.Entry<String, String>>>> getMethodParamValueMap() {
        return methodParamValueMap;
    }

    public static void setMethodParamValueMap(HashMap<String, List<List<Map.Entry<String, String>>>> methodParamValueMap) {
        MethodLogger.methodParamValueMap = methodParamValueMap;
    }

    public static void reset() {
        methodParamValueMap = new HashMap<>();
        methodCallOrder = new ArrayList<>();
    }

    public static List<String> getMethodCallOrder() {
        return methodCallOrder;
    }

    public static void setMethodCallOrder(List<String> methodCallOrder) {
        MethodLogger.methodCallOrder = methodCallOrder;
    }
//    public static <T> void logParam(String method, String paramName, Class<T> [][] paramValue) {
//        if (paramValue == null)
//            logParam(method, paramName, "null");
//        else
//            IntStream.range(0, paramValue.length).filter(i -> paramValue[i] != null).forEach(i -> IntStream.range(0, paramValue[i].length).forEach(x -> logParam(method, paramName + "[" + i + "][" + x + "]", paramValue[i][x])));
//    }
    private static boolean checkClassAsObject(Class c) {
        return !c.isPrimitive() && !c.equals(String.class) && !c.isInterface() && ! c.isArray() && !Primitives.isWrapperType(c);
    }
}
