package program.generation.test;

import helper.Properties;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

import java.util.*;
import java.util.stream.Collectors;

public class TestSuite {
    private final List<TestClass> testClasses = new ArrayList<>();
    private final static int MAX_CASE_COUNT = 200;
    private final Set<Class<?>> imports = new HashSet<Class<?>>(){{add(RunWith.class); add(Suite.class);}};
    private final String testSuiteName = Properties.getSingleton().getTestSuitePrefix() +"Test";

    public TestSuite() {
    }

    public List<TestClass> getTestClasses() {
        return testClasses;
    }

    public void assignTestCase(TestCase testCase) {
        if(testClasses.size() == 0 )
            this.testClasses.add(new TestClass());
        if(testClasses.get(testClasses.size()-1).getEnclosedTestCases().size()>=MAX_CASE_COUNT)
            this.testClasses.add(new TestClass());
        this.testClasses.get(testClasses.size()-1).addEnclosedTestCases(testCase);


    }

    public String getTestSuiteName() {
        return testSuiteName;
    }

    public void assignTestCase(List<TestCase> testCases) {
        testCases.forEach(this::assignTestCase);
    }
    public String output() {
        StringBuilder result = new StringBuilder();
        result.append("package " + Properties.getSingleton().getGeneratedPackage()).append(";").append(Properties.getNewLine());
        this.imports.stream().map(i -> "import " +  i.getName() + ";" + Properties.getNewLine()).forEach(result::append);
        result.append("@RunWith(Suite.class)").append(Properties.getNewLine())
                .append("@Suite.SuiteClasses({");
        result.append(this.testClasses.stream().map(t -> t.getClassName() + ".class").collect(Collectors.joining(",")));
        result.append("})").append(Properties.getNewLine());
        result.append("public class ").append(this.testSuiteName).append(" {").append(Properties.getNewLine()).append("}");
        return result.toString();
    }



}
