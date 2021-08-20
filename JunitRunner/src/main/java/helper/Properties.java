package helper;

import entities.IndexingMode;

public class Properties {
    private static final Properties singleton = new Properties();
    public static final String DEFAULT_INDEX_FILE = "./indexFile.json";
    public static final String DEFAULT_RESULT_PATH = ".testResult";
    public static final String DEFAULT_INSTR_BIN = ".instrumented";
    public static final String DEFAULT_TEST_RESULT_PREFIX = "default";

    private String[] testClassNames;
//    private String cpPath;
    private String testResultPrefix;
    private String testResultFilePath;
    private String instrumentedBinDir;
    private IndexingMode indexingMode;
    private String indexFile;
    private String[] instrumentClasses;
    public static Properties getInstance() {
        return singleton;
    }

    public static void resetSingleton() {
        getInstance().setIndexFile(DEFAULT_INDEX_FILE);
        getInstance().setInstrumentedBinDir(DEFAULT_INSTR_BIN);
        getInstance().setTestClassNames(null);
        getInstance().setTestResultPrefix(DEFAULT_TEST_RESULT_PREFIX);
        getInstance().setTestResultFilePath(DEFAULT_RESULT_PATH);
        getInstance().setIndexingMode(IndexingMode.CREATE);
        getInstance().setInstrumentClasses(null);
//        getInstance().setCpPath(System.getProperty( "java.class.path" ));
    }
    public String[] getTestClassNames() {
        return testClassNames;
    }

    public void setTestClassNames(String[] testClassNames) {
        this.testClassNames = testClassNames;
    }

    public String getTestResultPrefix() {
        return testResultPrefix;
    }

    public void setTestResultPrefix(String testResultPrefix) {
        this.testResultPrefix = testResultPrefix;
    }

    public String getTestResultFilePath() {
        return testResultFilePath;
    }

    public void setTestResultFilePath(String testResultFilePath) {
        this.testResultFilePath = testResultFilePath;
    }

    public String getInstrumentedBinDir() {
        return instrumentedBinDir;
    }

    public void setInstrumentedBinDir(String instrumentedBinDir) {
        this.instrumentedBinDir = instrumentedBinDir;
    }

    public String getIndexFile() {
        return indexFile;
    }

    public void setIndexFile(String indexFile) {
        this.indexFile = indexFile;
    }

    public IndexingMode getIndexingMode() {
        return indexingMode;
    }

    public void setIndexingMode(IndexingMode indexingMode) {
        this.indexingMode = indexingMode;
    }

//    public String getCpPath() {
//        return cpPath;
//    }
//
//    public void setCpPath(String cpPath) {
//        this.cpPath = cpPath;
//    }

    public String[] getInstrumentClasses() {
        return instrumentClasses;
    }

    public void setInstrumentClasses(String[] instrumentClasses) {
        this.instrumentClasses = instrumentClasses;
    }
}
