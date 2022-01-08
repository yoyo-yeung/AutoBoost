package helper;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Properties {
    private static Logger logger = LogManager.getLogger(Properties.class);
    private static final Properties singleton = new Properties();
    private String insBinPath =  null;
    private String[] CUTs = null;
    private String[] testCases = null;
    private String[] faultyFunc = null;
    private static final String classMethSep = "::";
    private static final String classSep = ",";
    private static final String NEW_LINE = "\n";
    private static final String DELIMITER = ",";
    private String generatedPackage = null;

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
        logProperty("insBinPath", this.insBinPath);
    }

    public String[] getCUTs() {
        return CUTs;
    }

    public void setCUTs(String[] CUTs) {
        this.CUTs = CUTs;
        logProperty("CUTs", String.join(",", this.CUTs));
    }

    public static String getClassMethSep() {
        return classMethSep;
    }

    public static String getClassSep() {
        return classSep;
    }

    private void logProperty(String name, String value){
        logger.debug(String.format("Argument set for %-15s:", name) + String.format(" %s", value));
    }

    public String[] getTestCases() {
        return testCases;
    }

    public void setTestCases(String[] testCases) {
        this.testCases = testCases;
        logProperty("testCases", String.join(",", this.testCases));

    }

    public String getGeneratedPackage() {
        return generatedPackage;
    }

    public void setGeneratedPackage(String generatedPackage) {
        this.generatedPackage = generatedPackage;
        logProperty("generatedPackage", this.generatedPackage);
    }

    public static String getDELIMITER() {
        return DELIMITER;
    }

    public static String getNewLine() {
        return NEW_LINE;
    }

    public String[] getFaultyFunc() {
        return faultyFunc;
    }

    public void setFaultyFunc(String[] faultyFunc) {
        this.faultyFunc = faultyFunc;
        logProperty("faultyFunc", String.join(",", this.faultyFunc));
    }
}
