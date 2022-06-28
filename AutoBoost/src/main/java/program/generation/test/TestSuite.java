package program.generation.test;

import helper.Properties;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class TestSuite {
//    private final List<TestClass> testClasses = new ArrayList<>();
    private final Set<Class<?>> imports = new HashSet<Class<?>>(){{add(RunWith.class); add(Suite.class);}};
    private final String testSuiteName = Properties.getSingleton().getTestSuitePrefix() +"Test";
    private final Map<String, Stack<TestClass>> testClassMap = new ConcurrentHashMap<>();

    public TestSuite() {
    }

    public Set<TestClass> getTestClasses() {
        return this.testClassMap.values().stream().flatMap(Collection::stream).collect(Collectors.toSet());
    }

    public void assignTestCase(TestCase testCase) {
        String packageName = testCase.getPackageName();
        if(!this.testClassMap.containsKey(packageName))
            this.testClassMap.put(packageName, new Stack<TestClass>() {{push(new TestClass(packageName));}});
        if(this.testClassMap.get(packageName).peek().getEnclosedTestCases().size() >= Properties.getSingleton().getCasePerClass())
            this.testClassMap.get(packageName).push(new TestClass(packageName));
        this.testClassMap.get(packageName).peek().addEnclosedTestCases(testCase);

    }

    public String getTestSuiteName() {
        return testSuiteName;
    }


//    public String output() {
//        StringBuilder result = new StringBuilder();
//        result.append("package " + Properties.getSingleton().getGeneratedPackage()).append(";").append(Properties.getNewLine());
//        this.imports.stream().map(i -> "import " +  i.getName() + ";" + Properties.getNewLine()).forEach(result::append);
//        result.append("@RunWith(Suite.class)").append(Properties.getNewLine())
//                .append("@Suite.SuiteClasses({");
//        result.append(this.testClasses.stream().map(t -> t.getClassName() + ".class").collect(Collectors.joining(",")));
//        result.append("})").append(Properties.getNewLine());
//        result.append("public class ").append(this.testSuiteName).append(" {").append(Properties.getNewLine()).append("}");
//        return result.toString();
//    }



}
