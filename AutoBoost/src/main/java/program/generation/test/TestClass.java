package program.generation.test;

import helper.Properties;
import org.apache.commons.lang3.ClassUtils;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class TestClass {
    private static AtomicInteger classIDGenerator = new AtomicInteger(0);
    private int ID;
    private ArrayList<TestCase> enclosedTestCases = new ArrayList<>();
    private Set<Class<?>> imports = new HashSet<>();
    private String className = null;
    private String packageName = null;
    private Set<Class<?>> mockedTypes = new HashSet<>();

    public TestClass(String packageName) {
        this.packageName = packageName;
        this.ID = classIDGenerator.incrementAndGet();
        this.className = Properties.getSingleton().getTestSuitePrefix() + "_"+ this.ID + "_Test";
        if(Properties.getSingleton().getJunitVer() == 3)
            this.addImports(junit.framework.TestCase.class);

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
        if(this.enclosedTestCases.stream().anyMatch(ex -> ex.equals(testCase))) return;
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
        if(this.mockedTypes.size() > 0 ){
            this.addImports(PowerMockRunner.class);
            this.addImports(PrepareForTest.class);
            this.addImports(RunWith.class);
        }
        Set<Class<?>> fullCNameNeeded= this.imports.stream().collect(Collectors.groupingBy(Class::getSimpleName, Collectors.toSet())).entrySet().stream().filter(e -> e.getValue().size() > 1 )
                .flatMap(e -> e.getValue().stream()).collect(Collectors.toSet());
        fullCNameNeeded.addAll(this.imports.stream().filter(ClassUtils::isInnerClass).collect(Collectors.toSet()));
        StringBuilder result = new StringBuilder();
        result.append("package " + packageName).append(";").append(Properties.getNewLine());
        this.imports.stream().filter(i -> !fullCNameNeeded.contains(i)).filter(i -> !i.getPackage().getName().equals(packageName)).map(i -> {
            return "import " + i.getName().replace("$", ".") + ";" + Properties.getNewLine();
        }).forEach(result::append);
        mockAnnotationsSetUp(fullCNameNeeded).forEach(result::append);
        result.append("public class ").append(this.className).append(Properties.getSingleton().getJunitVer()==3? " extends TestCase" : "").append("{").append(Properties.getNewLine());
        this.getEnclosedTestCases().stream().map(t-> t.output(fullCNameNeeded)+Properties.getNewLine()).forEach(result::append);
        result.append("}").append(Properties.getNewLine());
        return result.toString();
    }

    public List<String> mockAnnotationsSetUp(Set<Class<?>> fullCNameNeeded){
        List<String> res = new ArrayList<>();
        if(this.mockedTypes.size() == 0) return res;
        res.add("@RunWith(PowerMockRunner.class)\n");
        res.add("@PrepareForTest({" + this.mockedTypes.stream().map(t -> (fullCNameNeeded.contains(t)? t.getName().replace("$", ".") : t.getSimpleName()) + ".class").collect(Collectors.joining(",") )+ "})\n");
        return res;
    }
}
