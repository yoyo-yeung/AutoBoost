package program.generation.test;

import helper.Properties;

import java.util.Set;

public class ExceptionTestCase extends TestCase{
    private Class<?> exceptionClass;
    public ExceptionTestCase(String packageName) {
        super(packageName);
        this.addImports(exceptionClass);
    }

    public void setExceptionClass(Class<?> exceptionClass) {
        this.exceptionClass = exceptionClass;
    }

    public String output(Set<Class<?>> fullCNameNeeded) {
        String indentation = "\t";
        StringBuilder result = new StringBuilder();
        if(Properties.getSingleton().getJunitVer()!=3)
            result.append(indentation).append("@Test (expected = ").append(exceptionClass.getName()).append(".class").append(")\n");
        result.append(indentation).append("public void test").append(this.getID()).append("() throws Exception {\n");
        result.append(outputStmts(indentation+indentation, fullCNameNeeded));
        result.append(indentation).append("}\n");
        return result.toString();
    }

}
