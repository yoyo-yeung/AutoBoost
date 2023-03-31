package helper;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.stream.Collectors;

public class Properties {
    private static Logger logger = LogManager.getLogger(Properties.class);
    private static final Properties singleton = new Properties();
    private String instrumentedBinPath = null;
    private String[] CUTs = null;
    private String[] testCases = null;
    private String[] testClasses = null;
    private String stmtCountFile = null;
    private String projName = null;
    private String testInfoFolder = null;
    private String stmtOutputFormat = "%s: \n%s";
    private String classCaseSeparator = "::";

    public Properties() {
    }

    public static Properties getSingleton() {
        return singleton;
    }

    public String getInstrumentedBinPath() {
        return instrumentedBinPath;
    }

    public void setInstrumentedBinPath(String instrumentedBinPath) {
        this.instrumentedBinPath = instrumentedBinPath;
        logger.info(getPropertyDisplay("instrumentedBinPath", this.instrumentedBinPath));
    }

    public String[] getCUTs() {
        return CUTs;
    }

    public void setCUTs(String[] CUTs) {
        this.CUTs = CUTs;
        logger.info(getPropertyDisplay("CUTs", Arrays.stream(this.CUTs).collect(Collectors.joining(","))));
    }

    public String[] getTestCases() {
        return testCases;
    }

    public void setTestCases(String[] testCases) {
        this.testCases = testCases;
        logger.info(getPropertyDisplay("TestCases", Arrays.stream(testCases).collect(Collectors.joining(","))));
    }

    public String[] getTestClasses() {
        return testClasses;
    }

    public void setTestClasses(String[] testClasses) {
        this.testClasses = testClasses;
        logger.info(getPropertyDisplay("TestClasses", Arrays.stream(testClasses).collect(Collectors.joining(","))));
    }

    public String getStmtCountFile() {
        return stmtCountFile;
    }

    public void setStmtCountFile(String stmtCountFile) {

        logger.info(getPropertyDisplay("stmtCountFile", stmtCountFile));
        this.stmtCountFile = stmtCountFile;
    }

    public String getProjName() {
        return projName;
    }

    public void setProjName(String projName) {
        logger.info(getPropertyDisplay("projName", projName));
        this.projName = projName;
    }

    public String getTestInfoFolder() {
        return testInfoFolder;
    }

    public void setTestInfoFolder(String testInfoFolder) {
        logger.info(getPropertyDisplay("testInfoFolder", testInfoFolder));
        this.testInfoFolder = testInfoFolder;
    }

    public String getStmtOutputFormat() {
        return stmtOutputFormat;
    }

    public String getClassCaseSeparator() {
        return classCaseSeparator;
    }

    private String getPropertyDisplay(String key, String ... values) {
        return String.format("%s : \t\t%s", key, String.join(",", values));
    }
}
