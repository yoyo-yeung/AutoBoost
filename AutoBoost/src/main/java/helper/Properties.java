package helper;
//
//import com.sun.org.slf4j.internal.Logger;
//import com.sun.org.slf4j.internal.LoggerFactory;

public class Properties {

//    private static final Logger logger = LoggerFactory.getLogger(Properties.class);

    private static final Properties singleton = new Properties();
    public static final String TEMP_BIN = ".tmpbin";
    public static String DEFAULT_TEST_RUNNER_CLASS = "application.JunitRunner";
    public static String DEFAULT_RESULT_DIR = ".testResult";
    public static final String FIXED_FILE_PREFIX = "fixed";
    public static final String PATCH_FILE_PREFIX = "patch-";
    // the below 3 String are temp., used for exploring ranking strat.
    public static final String RESULT_FILE_SUFFIX = "_results.json";
    public static final String PATH_COV_FILE_SUFFIX = "_path_coverage.json";
    public static final String STMT_SET_COV_FILE_SUFFIX = "_set_coverage.json";
    private String fixedClassPath;
    private String[] unacceptedClassPaths;
    private String[] testClassPaths;
    private String testClassNames;
    private String testRunnerJarPath;
    private String testRunnerClass;
    private String[] dependencyPaths;
    private String resultDir;
    private String instrumentClasses;
    private final String indexFile = "index.json";

    private Properties() {

    }

    public static Properties getInstance() {
        return singleton;
    }

    public static void resetSingleton() {
        getInstance().fixedClassPath = null;
        getInstance().unacceptedClassPaths = null;
        getInstance().testClassPaths = null;
        getInstance().testClassNames = null;
        getInstance().testRunnerJarPath = null;
        getInstance().testRunnerClass = null;
        getInstance().dependencyPaths = null;
        getInstance().instrumentClasses = null;
    }

    public void setFixedClassPath(String fixedClassPath) {
        this.fixedClassPath = fixedClassPath;
    }

    public void setUnacceptedClassPaths(String[] unacceptedClassPaths) {
        this.unacceptedClassPaths = unacceptedClassPaths;
    }

    public void setTestClassPaths(String[] testClassPaths) {
        this.testClassPaths = testClassPaths;
    }


    public void setTestClassNames(String testClassNames) {
        this.testClassNames = testClassNames;
    }

    public void setTestRunnerJarPath(String testRunnerJarPath) {
        this.testRunnerJarPath = testRunnerJarPath;
    }

    public String getFixedClassPath() {
        return fixedClassPath;
    }

    public String[] getUnacceptedClassPaths() {
        return unacceptedClassPaths;
    }

    public String[] getTestClassPaths() {
        return testClassPaths;
    }

    public String getTestClassNames() {
        return testClassNames;
    }

    public String getTestRunnerJarPath() {
        return testRunnerJarPath;
    }


    public String getTestRunnerClass() {
        return testRunnerClass;
    }

    public void setTestRunnerClass(String testRunnerClass) {
        this.testRunnerClass = testRunnerClass;
    }

    public String[] getDependencyPaths() {
        return dependencyPaths;
    }

    public void setDependencyPaths(String[] dependencyPaths) {
        this.dependencyPaths = dependencyPaths;
    }

    public String getLinkedTestClassPaths(String joint) {
        return String.join(joint, testClassPaths);
    }

    public String getLinkedDependencyPaths(String joint) {
        return String.join(joint, dependencyPaths);
    }

    public String getResultDir() {
        return resultDir;
    }

    public void setResultDir(String resultDir) {
        this.resultDir = resultDir;
    }

    public String getInstrumentClasses() {
        return instrumentClasses;
    }

    public void setInstrumentClasses(String instrumentClasses) {
        this.instrumentClasses = instrumentClasses;
    }

    public String getIndexFile() {
        return indexFile;
    }


}
