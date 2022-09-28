package helper;

import org.apache.commons.lang3.ClassUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import soot.Modifier;
import soot.RefType;
import soot.Type;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Helper {
    private static Logger logger = LogManager.getLogger(Helper.class);
    /**
     *
     * @param c class to find accessing package
     * @return null if cannot be accessed at all, "" if all packages are fine and package name if requirement exist
     */
    public static String getRequiredPackage(Class<?>c) {
        if(accessibilityCheck(c, "")) return ""; // if any package is fine
        if(accessibilityCheck(c, c.getPackage().getName())) return c.getPackage().getName(); // if has to be in declaring package
        return null; // if cannot be accessed anyways
    }

    /**
     * @param c Class to be accessed
     * @param packageName package of classes accessing c
     * @return if accessing is allowed
     */
    public static boolean accessibilityCheck(Class<?> c, String packageName) {
        int modifier = c.getModifiers();
        if(c.getName().contains("$"))
            try {
                Integer.parseInt(c.getName().substring(c.getName().indexOf("$") + 1)); // dont know why isAnonymousClass return false for these
                return false;
            }
            catch (NumberFormatException ignored){

            }
        return (Modifier.isPublic(modifier) || (!Modifier.isPrivate(modifier) && (packageName.isEmpty() || c.getPackage().getName().equals(packageName)))) // access check
                && (!ClassUtils.isInnerClass(c) || (Modifier.isStatic(modifier) && Modifier.isPublic(modifier) && accessibilityCheck(c.getEnclosingClass(), packageName))) // if is inner class, is static inner class and its enclosing class CAN be accessed
                && !c.isAnonymousClass() // is NOT anonymous class
                && !c.getSimpleName().startsWith("$");
    }

    public static Class<?> getAccessibleSuperType(Class<?> c, String packageName) {
        if(accessibilityCheck(c, packageName)) return c;
        List<Class<?>> classList = Stream.concat(Stream.of(c), ClassUtils.getAllSuperclasses(c).stream()).collect(Collectors.toList());
        classList.remove(Object.class);
        List<Class<?>> interfaceList = classList.stream().flatMap(s -> Arrays.stream(s.getInterfaces())).filter(c1-> accessibilityCheck(c1, packageName)).collect(Collectors.toList());
        classList = classList.stream().filter(c1-> accessibilityCheck(c1, packageName)).collect(Collectors.toList());
        return classList.stream().findFirst().orElse(interfaceList.stream().findFirst().orElse(Object.class));

    }

    public static Class<?> sootTypeToClass(Type sootType) {
        try {
            return ClassUtils.getClass(sootType.toQuotedString().replace("'",""));
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    public static boolean isCannotMockType(Type type) {
        return isCannotMockType(type.toQuotedString()) && Arrays.stream(new Class[]{Boolean.class, Byte.class, Character.class, Class.class, Double.class, Enum.class, Float.class, Integer.class, Long.class, Short.class, Void.class, String.class, StringBuilder.class, StringBuffer.class}).map(Class::getName).map(RefType::v).noneMatch(type::equals);
    }

    public static boolean isCannotMockType(Class<?> type) {
        return isCannotMockType(type.getName()) && !ClassUtils.isPrimitiveOrWrapper(type) && !type.equals(String.class) && !type.equals(StringBuilder.class) && !type.equals(StringBuffer.class) && !type.equals(Enum.class) ;
    }

    private static boolean isCannotMockType(String typeName) {
//        logger.debug(typeName);
        return typeName.replace("'", "").startsWith("java.lang.reflect.Method") || typeName.equals("java.io.StringReader");
    }
}
