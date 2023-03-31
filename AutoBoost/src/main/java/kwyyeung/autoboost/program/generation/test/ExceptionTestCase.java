package kwyyeung.autoboost.program.generation.test;

import kwyyeung.autoboost.helper.Properties;
import kwyyeung.autoboost.program.execution.MethodExecution;

import java.util.HashSet;
import java.util.Set;

public class ExceptionTestCase extends TestCase {
    private Class<?> exceptionClass;

    public ExceptionTestCase(MethodExecution target) {
        super(target);
        this.addImports(exceptionClass);
    }

    public void setExceptionClass(Class<?> exceptionClass) {
        this.exceptionClass = exceptionClass;
    }

    public String output(Set<Class<?>> fullCNameNeeded) {
        String indentation = "\t";
        StringBuilder result = new StringBuilder();
        String contentIndentation = Properties.getSingleton().getJunitVer() == 3 ? indentation + indentation + indentation : indentation + indentation;
        if (Properties.getSingleton().getJunitVer() != 3)
            result.append(indentation).append("@Test (expected = ").append(exceptionClass.getName()).append(".class").append(")\n");
        result.append(indentation).append("public void test").append(this.getID()).append("() throws Exception {\n");
        if (Properties.getSingleton().getJunitVer() == 3)
            result.append(indentation).append(indentation).append("try{").append("\n");
        result.append(outputStmts(contentIndentation, fullCNameNeeded));
        if (Properties.getSingleton().getJunitVer() == 3)
            result.append(indentation).append(indentation).append("fail(\"Expected exception\");\n").append(indentation).append("}catch(").append(exceptionClass.getName()).append(" ignored){\n").append(indentation).append(indentation).append("}\n");
        result.append(indentation).append("}\n");
        return result.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ExceptionTestCase that = (ExceptionTestCase) o;
        return this.outputStmts("", new HashSet<>()).equals(that.outputStmts("", new HashSet<>()));
    }

}
