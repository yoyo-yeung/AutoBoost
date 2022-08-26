package helper;

import org.apache.commons.lang3.ClassUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import soot.Modifier;

import java.util.Arrays;
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
        return (Modifier.isPublic(modifier) || (!Modifier.isPrivate(modifier) && c.getPackage().getName().equals(packageName))) // access check
                && (!ClassUtils.isInnerClass(c) || (Modifier.isStatic(modifier) && Modifier.isPublic(modifier) && accessibilityCheck(c.getEnclosingClass(), packageName))) // if is inner class, is static inner class and its enclosing class CAN be accessed
                && !c.isAnonymousClass(); // is NOT anonymous class
    }

    public static Class<?> getAccessibleSuperType(Class<?> c, String packageName) {
        if(accessibilityCheck(c, packageName)) return c;
        return Stream.concat(Stream.of(c), ClassUtils.getAllSuperclasses(c).stream())
                .flatMap(s -> Stream.concat(Stream.of(s), Arrays.stream(s.getInterfaces())))
                .filter(c1-> accessibilityCheck(c1, packageName))
                .findFirst().orElse(c);
    }

}
