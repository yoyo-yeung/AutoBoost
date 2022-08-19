package helper;

import org.apache.commons.lang3.ClassUtils;
import soot.Modifier;

public class Helper {
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
                && (!ClassUtils.isInnerClass(c) || (Modifier.isStatic(modifier) && accessibilityCheck(c.getEnclosingClass(), packageName))) // if is inner class, is static inner class and its enclosing class CAN be accessed
                && !c.isAnonymousClass(); // is NOT anonymous class
    }

    public static Class<?> getAccessibleSuperType(Class<?> c, String packageName) {
        return ClassUtils.getAllSuperclasses(c).stream().filter(c1 -> accessibilityCheck(c1, packageName)).findFirst().orElse(c);
    }

}