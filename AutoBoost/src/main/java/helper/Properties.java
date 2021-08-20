package helper;
//
//import com.sun.org.slf4j.internal.Logger;
//import com.sun.org.slf4j.internal.LoggerFactory;

import java.util.Arrays;
import java.util.stream.Collectors;

public class Properties {

//    private static final Logger logger = LoggerFactory.getLogger(Properties.class);

    private static final Properties singleton = new Properties();

    private String fixedClassPath;
    private String[] unacceptedClassPaths;
    private String[] testClassPaths;
    private String testClassNames;
    private String testRunnerJarPath;
    private String testRunnerClass;
    private String[] dependencyPaths;
    private String resultDir;

    private Properties(){

    }
    public static Properties getInstance(){
        return singleton;
    }

    public static void resetSingleton(){
        getInstance().fixedClassPath = null;
        getInstance().unacceptedClassPaths = null;
        getInstance().testClassPaths = null;
        getInstance().testClassNames = null;
        getInstance().testRunnerJarPath = null;
        getInstance().testRunnerClass = null;
        getInstance().dependencyPaths = null;
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
}
