package program.generation;

import entity.METHOD_TYPE;
import helper.Helper;
import org.apache.commons.lang3.ClassUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import program.analysis.MethodDetails;
import program.execution.ExecutionTrace;
import program.execution.MethodExecution;
import program.execution.variable.*;
import program.generation.test.TestCase;

import java.lang.reflect.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

class ExecutionChecker {
    private static final Logger logger = LogManager.getLogger(ExecutionChecker.class);
    private static final ExecutionTrace executionTrace = ExecutionTrace.getSingleton();


    protected static Object constructDefaultInstance(Class<?> type) {
        try {
            return type.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            logger.error(e.getMessage());
            return null;
        }
    }

    protected static void constructPrimWrapOrString(VarDetail p, TestCase testCase) {
        if (testCase.createdObjForVar(p.getID())) return;
        testCase.addObjForVar(p.getID(), p.getValue());
        if(testCase.getObjForVar(p.getID()) == null || !testCase.getObjForVar(p.getID()).equals(p.getValue())) logger.error("null val " + p);

    }

    protected static void constructStringB(StringBVarDetails p, TestCase testCase) {
        if (testCase.createdObjForVar(p.getID())) return;
        testCase.addObjForVar(p.getID(), p.getType().equals(StringBuffer.class) ? new StringBuffer((String) p.getValue()) : new StringBuilder((String) p.getValue()));
        if(testCase.getObjForVar(p.getID()) == null) logger.error("null val " + p);
        if(!testCase.getObjForVar(p.getID()).toString().equals(p.getValue())) logger.error("wrong val");
    }

    protected static void constructEnum(EnumVarDetails p, TestCase testCase) {
        if (testCase.createdObjForVar(p.getID())) return;
        if (p.getType().equals(Class.class)) {
            try {
                testCase.addObjForVar(p.getID(), ClassUtils.getClass(p.getValue()));
            } catch (ClassNotFoundException e) {
                logger.error(e.getMessage());
                e.printStackTrace();
            }
        } else if (p.getType().isEnum())
            testCase.addObjForVar(p.getID(), Enum.valueOf((Class) p.getType(), p.getValue()));
        else {
            try {
                testCase.addObjForVar(p.getID(), p.getType().getField(p.getValue()).get(null));
            } catch (NoSuchFieldException | IllegalAccessException e) {
                logger.error(e.getMessage());
                e.printStackTrace();
            }
        }
        if(testCase.getObjForVar(p.getID()) == null) logger.error("null val " + p);
    }

    protected static void constructMap(MapVarDetails p, TestCase testCase) {
        if (testCase.createdObjForVar(p.getID())) return;
        Map res = (Map) constructDefaultInstance(p.getType());
        p.getKeyValuePairs().stream().forEach(e -> {
            res.put(testCase.getObjForVar(e.getKey()), testCase.getObjForVar(e.getValue()));
        });
        testCase.addObjForVar(p.getID(), res);
        if(testCase.getObjForVar(p.getID()) == null) logger.error("null val " + p);
        if(!testCase.getObjForVar(p.getID()).equals(res)) logger.error("not same ");
        if(res.keySet().stream().filter(Objects::isNull).count()!=p.getKeyValuePairs().stream().map(e -> e.getKey()).filter(k -> k==executionTrace.getNullVar().getID()).count())
            logger.error("more null than expected");
        if(res.values().stream().filter(Objects::isNull).count()!=p.getKeyValuePairs().stream().map(e -> e.getValue()).filter(k -> k==executionTrace.getNullVar().getID()).count())
            logger.error("more null than expected");
    }

    protected static void constructArr(ArrVarDetails p, TestCase testCase) {
        if (testCase.createdObjForVar(p.getID())) return;
        Stream components = p.getComponents().stream().map(testCase::getObjForVar);
        if (p.getType().isArray())
            testCase.addObjForVar(p.getID(), components.toArray(s -> Array.newInstance(p.getComponentType(), s)));
        else {
            Collection res = (Collection) constructDefaultInstance(p.getType());
            components.forEach(res::add);
            testCase.addObjForVar(p.getID(), res);
        }
        if(testCase.getObjForVar(p.getID()) == null) logger.error("null val " + p);
        if(IntStream.range(0, p.getComponents().size()).anyMatch(id -> p.getComponents().get(id)!=executionTrace.getNullVar().getID() && ((p.getType().isArray() && Helper.getArrayElement(testCase.getObjForVar(p.getID()), id)==null) || (!p.getType().isArray() && ((Collection)testCase.getObjForVar(p.getID())== null)))))
            logger.error("issues");

    }

    protected static void constructMock(VarDetail p, TestCase testCase) {
        if (testCase.createdObjForVar(p.getID())) return;
        Class<?> valType = Helper.getAccessibleMockableSuperType(p.getType(), testCase.getPackageName());
        testCase.addObjForVar(p.getID(), Mockito.mock(valType));
        if(testCase.getObjForVar(p.getID()) == null) logger.error("null val " + p);
    }

    protected static void constructObj(TestCase testCase, MethodExecution execution, VarDetail resultID, Object[] params) {
        Object callee = execution.getCalleeId() == -1 ? null : testCase.getObjForVar(execution.getCalleeId());
        if(execution.getCalleeId()!=-1 && testCase.getObjForVar(execution.getCalleeId()) == null)
            logger.error("consturct obj ");
        if(IntStream.range(0, params.length).anyMatch(i -> execution.getParams().get(i)!=executionTrace.getNullVar().getID() && params[i] == null)) {
            logger.error("consturct params ");
            logger.error(execution.toSimpleString());
        }
        MethodDetails toInvoke = execution.getMethodInvoked();
        if (toInvoke.getType() == METHOD_TYPE.CONSTRUCTOR) {
            try {
                Constructor constructor = toInvoke.getdClass().getDeclaredConstructor(toInvoke.getParameterTypes().stream().map(Helper::sootTypeToClass).toArray(Class[]::new));
                constructor.setAccessible(true);
                testCase.addObjForVar(resultID.getID(), constructor.newInstance(params));
            } catch (NoSuchMethodException | InstantiationException | IllegalAccessException |
                     InvocationTargetException e) {
                testCase.setRecreated(false);
            }
        } else {
            Object returnVal = null;
            try {
                Method method = toInvoke.getdClass().getDeclaredMethod(toInvoke.getName(), toInvoke.getParameterTypes().stream().map(Helper::sootTypeToClass).toArray(Class[]::new));
                method.setAccessible(true);
                returnVal = method.invoke(callee, params);
                if(callee != null)
                    testCase.addObjForVar(execution.getResultThisId(), callee);
            } catch (IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
                testCase.setRecreated(false);
            }
            if (execution.getReturnValId() != -1)
                testCase.addObjForVar(execution.getReturnValId(), returnVal);
        }
    }

    protected static void setField(Object toSet, String className, String fieldName, Object fieldVal) {
        try {
            Field field = Class.forName(className).getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(toSet, fieldVal);
        } catch (NoSuchFieldException | ClassNotFoundException | IllegalAccessException e) {
            logger.error(e.getMessage());
            e.printStackTrace();
        }
    }

    protected static void setMock(Object obj, MethodExecution execution, Object[] params, Object returnVal, TestCase testCase) {
        if (obj == null || !Mockito.mockingDetails(obj).isMock()) {
            logger.error(obj == null ? "null " : obj.getClass() + "\t" + testCase.getID());
            throw new RuntimeException();
//            return;
        }
        MethodDetails methodDetails = execution.getMethodInvoked();
        try {
            Method toMock = methodDetails.getdClass().getMethod(methodDetails.getName(), methodDetails.getParameterTypes().stream().map(Helper::sootTypeToClass).toArray(Class[]::new));
            toMock.setAccessible(true);
            Mockito.when(toMock.invoke(obj, params)).thenReturn(returnVal);
            if(obj != null)
                testCase.addObjForVar(execution.getResultThisId(), obj);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            logger.error(e.getMessage());
            e.printStackTrace();
        }

    }

//    protected static void setStaticMock(MethodExecution execution, Object[] params, Object returnVal) {
//        MethodDetails methodDetails = execution.getMethodInvoked();
//        try {
//            Method toMock = methodDetails.getdClass().getMethod(methodDetails.getName(), methodDetails.getParameterTypes().stream().map(Helper::sootTypeToClass).toArray(Class[]::new));
//            toMock.setAccessible(true);
//            try(MockedStatic utility = Mockito.mockStatic(toMock.getDeclaringClass())){
//                utility.when(() -> toMock.invoke(null, params)).thenReturn(returnVal);
//            }
//
//        } catch (Exception e) {
//            logger.error(e.getClass() +"\t"+ e.getMessage());
//            e.printStackTrace();
//        }
//
//    }

    protected static Object getStandaloneObj(VarDetail p) {
        if(p.equals(executionTrace.getNullVar())) return null;
        if(p instanceof PrimitiveVarDetails || p instanceof WrapperVarDetails || p instanceof StringVarDetails || p instanceof StringBVarDetails)
            return p.getValue();
        if(p instanceof EnumVarDetails) {
            if(p.getType().equals(Class.class)) {
                try {
                    return ClassUtils.getClass(((EnumVarDetails) p).getValue());
                } catch (ClassNotFoundException e) {
                    throw new RuntimeException(e);
                }
            }
            if(p.getType().isEnum())
                return Enum.valueOf((Class)p.getType(), ((EnumVarDetails) p).getValue());
            else {
                try {
                    return p.getType().getDeclaredField(((EnumVarDetails) p).getValue()).get(null);
                } catch (NoSuchFieldException | IllegalAccessException e) {
                    return null;
                }
            }
        }
        if(p instanceof ArrVarDetails) {
            Stream<Object> components = ((ArrVarDetails) p).getComponents().stream().map(executionTrace::getVarDetailByID).map(ExecutionChecker::getStandaloneObj);
            if(p.getType().isArray())
                return components.toArray();
            Collection res = (Collection)constructDefaultInstance(p.getType());
            components.forEach(res::add);
            return res;
        }
        if(p instanceof MapVarDetails) {
            Map res = (Map) constructDefaultInstance(p.getType());
            ((MapVarDetails) p).getKeyValuePairs().stream().forEach(e -> res.put(getStandaloneObj(executionTrace.getVarDetailByID(e.getKey())), getStandaloneObj(executionTrace.getVarDetailByID(e.getValue()))));
            return res;
        }
        logger.error("cannot gen");
        return null;
    }
}
