package kwyyeung.autoboost.program.generation.test;

import kwyyeung.autoboost.helper.Properties;
import org.apache.commons.lang3.ClassUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class TestClass {
    private static final Logger logger = LogManager.getLogger(TestClass.class);
    private static AtomicInteger classIDGenerator = new AtomicInteger(0);
    private int ID;
    private final ArrayList<TestCase> enclosedTestCases = new ArrayList<>();
    private Set<Class<?>> imports = new HashSet<>();
    private String className = null;
    private String packageName = null;
    private final Set<Class<?>> mockedTypes = new HashSet<>();

    public TestClass(String packageName) {
        this.packageName = packageName;
        this.ID = classIDGenerator.incrementAndGet();
        this.className = Properties.getSingleton().getTestSuitePrefix() + "_" + this.ID + "_Test";
        this.addImports(Field.class);

    }

    public static AtomicInteger getClassIDGenerator() {
        return classIDGenerator;
    }

    public static void setClassIDGenerator(AtomicInteger classIDGenerator) {
        TestClass.classIDGenerator = classIDGenerator;
    }

    public int getID() {
        return ID;
    }

    public void setID(int ID) {
        this.ID = ID;
    }

    public List<TestCase> getEnclosedTestCases() {
        return enclosedTestCases.stream().distinct().collect(Collectors.toList());
    }


    public void addEnclosedTestCases(TestCase testCase) {
        this.enclosedTestCases.add(testCase);
        this.imports.addAll(testCase.getAllImports());
        this.mockedTypes.addAll(testCase.getMockedTypes());
    }

    public void addEnclosedTestCases(ArrayList<TestCase> testCases) {
        this.enclosedTestCases.addAll(testCases);
        testCases.forEach(testCase -> this.imports.addAll(testCase.getAllImports()));
    }

    public Set<Class<?>> getImports() {
        return imports;
    }

    public void setImports(Set<Class<?>> imports) {
        this.imports = imports;
    }

    public void addImports(Class<?> imports) {
        this.imports.add(imports);
    }

    public void addImports(Set<Class<?>> imports) {
        this.imports.addAll(imports);
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public String output() {
        Set<Class<?>> fullCNameNeeded = this.imports.stream().collect(Collectors.groupingBy(Class::getSimpleName, Collectors.toSet())).entrySet().stream().filter(e -> e.getValue().size() > 1)
                .flatMap(e -> e.getValue().stream()).collect(Collectors.toSet());
        fullCNameNeeded.addAll(this.imports.stream().filter(c -> ClassUtils.isInnerClass(c) || c.getPackage() == null).collect(Collectors.toSet()));
        StringBuilder result = new StringBuilder();
        result.append("package " + packageName).append(";").append(Properties.getNewLine());
        this.imports.stream().filter(i -> !fullCNameNeeded.contains(i)).filter(i -> i.getPackage() != null && !i.getPackage().getName().equals(packageName)).map(i -> {
            return "import " + i.getName().replace("$", ".").replace("kwyyeung.autoboost.internal.", "") + ";" + Properties.getNewLine();
        }).forEach(result::append);
        result.append("public class ").append(this.className).append("{").append(Properties.getNewLine());
        result.append(getFieldSetUpMethod());
        this.getEnclosedTestCases().stream().map(t -> t.output(fullCNameNeeded) + Properties.getNewLine()).forEach(result::append);
        result.append("}").append(Properties.getNewLine());
        return result.toString();
    }


    private String getFieldSetUpMethod() {


        return "    private void setField(Object obj, String fieldClass, String fieldName, Object fieldValue) {\n" +
                "        try {\n" +
                "            Field field = Class.forName(fieldClass).getDeclaredField(fieldName);\n" +
                "            field.setAccessible(true);\n" +
                "            field.set(obj, fieldValue);\n" +
                "        } catch (Exception ignored) {}\n    }\n";
    }

}
