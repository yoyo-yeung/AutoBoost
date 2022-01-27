package program.generation.test;

import helper.Properties;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class TestClass {
    private static AtomicInteger classIDGenerator = new AtomicInteger(0);
    private int ID;
    private ArrayList<TestCase> enclosedTestCases = new ArrayList<>();
    private Set<Class<?>> imports = new HashSet<>();
    private String className = null;

    public TestClass() {
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

    public ArrayList<TestCase> getEnclosedTestCases() {
        return enclosedTestCases;
    }

    public void setEnclosedTestCases(ArrayList<TestCase> enclosedTestCases) {
        this.enclosedTestCases = enclosedTestCases;
    }

    public void addEnclosedTestCases(TestCase testCase) {
        this.enclosedTestCases.add(testCase);
        this.imports.addAll(testCase.getAllImports());
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

    public String output() {
        StringBuilder result = new StringBuilder();
        result.append("package " + Properties.getSingleton().getGeneratedPackage()).append(";").append(Properties.getNewLine());
        this.imports.stream().map(i -> {
            return "import " + i.getName().replaceAll("\\$", ".") + ";" + Properties.getNewLine();
        }).forEach(result::append);
        result.append("public class ").append(this.className).append(Properties.getSingleton().getJunitVer()==3? " extends TestCase" : "").append("{").append(Properties.getNewLine());
        this.getEnclosedTestCases().stream().map(t-> t.output()+Properties.getNewLine()).forEach(result::append);
        result.append("}").append(Properties.getNewLine());
        return result.toString();
    }
}
