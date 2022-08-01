package helper;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;

public class Properties {
    private static final Logger logger = LogManager.getLogger(Properties.class);
    private static final Properties singleton = new Properties();
    private String insBinPath =  null;
    private String[] testCases = null;
    private List<String> faultyFunc = new ArrayList<>();
    private List<Integer> faultyFuncIds = new ArrayList<>();
    private String testSourceDir = null;
    private String testSuitePrefix = "AB";
    private int casePerClass =200;
    private int junitVer = 4;
    private static final String classMethSep = "::";
    private static final String classSep = ",";
    private static final String NEW_LINE = "\n";
    private static final String DELIMITER = ",";

    public Properties() {
    }

    public static Properties getSingleton() {
        return singleton;
    }

    public String getInsBinPath() {
        return insBinPath;
    }

    public void setInsBinPath(String insBinPath) {
        this.insBinPath = insBinPath;
    }

    public static String getClassMethSep() {
        return classMethSep;
    }

    public static String getClassSep() {
        return classSep;
    }

    private void logProperty(String name, Object value){
        logger.info(String.format("Argument set for %-15s:", name) + (value == null ? "null" : String.format(" %s", value)));
    }

    public String[] getTestCases() {
        return testCases;
    }

    public void setTestCases(String[] testCases) {
        this.testCases = testCases;

    }


    public static String getDELIMITER() {
        return DELIMITER;
    }

    public static String getNewLine() {
        return NEW_LINE;
    }

    public List<String> getFaultyFunc() {
        return faultyFunc;
    }

    public void setFaultyFunc(List<String> faultyFunc) {
        this.faultyFunc = faultyFunc;
    }
    public void addFaultyFunc(String faultyFunc) {
        this.faultyFunc.add(faultyFunc);
    }
    public String getTestSourceDir() {
        return testSourceDir;
    }

    public void setTestSourceDir(String testSourceDir) {
        this.testSourceDir = testSourceDir;
    }

    public String getTestSuitePrefix() {
        return testSuitePrefix;
    }

    public void setTestSuitePrefix(String testSuitePrefix) {
        this.testSuitePrefix = testSuitePrefix;
    }

    public int getJunitVer() {
        return junitVer;
    }

    public void setJunitVer(int junitVer) {
        this.junitVer = junitVer;
    }

    public void logProperties() {
        logProperty("insBinPath", this.insBinPath);
        logProperty("testCases", String.join(",", this.testCases));
        logProperty("faultyFunc", String.join(",", this.faultyFunc));
        logProperty("testSourceDir", this.testSourceDir);
        logProperty("testClassPrefix", this.testSuitePrefix);
    }
    public void logFaultyFunc() {
        logProperty("faultyFunc", String.join(",", this.faultyFunc));
    }

    public List<Integer> getFaultyFuncIds() {
        return faultyFuncIds;
    }

    public void setFaultyFuncIds(List<Integer> faultyFuncIds) {
        this.faultyFuncIds = faultyFuncIds;
    }

    public void addFaultyFuncId(Integer faultyFuncId) {
        this.faultyFuncIds.add(faultyFuncId);
    }

    public int getCasePerClass() {
        return casePerClass;
    }

    public void setCasePerClass(int casePerClass) {
        this.casePerClass = casePerClass;
    }
}
