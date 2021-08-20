package helper;


import entities.IndexingMode;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.MissingArgumentException;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.util.Arrays;

public class CommandLineParameters {
    private static final Logger logger = LoggerFactory.getLogger(CommandLineParameters.class);
    public static Options getCommandLineOptions(){
        Options options = new Options();
        Option help = Help.getOption();
        // file name of generated test results
        Option testResultPrefix = new Option("testResultPrefix", true, "prefix for generated json for test results and coverage details, default: " + Properties.DEFAULT_TEST_RESULT_PREFIX);
        Option testClassNames =  new Option("testClasses", true, "Required. List of test classes, separated by ','");
        Option resultDir = new Option("resultDir", true, "Path to store generated test result json, default:" + Properties.DEFAULT_RESULT_PATH);
        Option instrumentedBinDir = new Option("instrumentedBinDir", true, "Path to store the instrumented binaries, default: "+Properties.DEFAULT_INSTR_BIN);
        Option indexingMode = new Option("indexingMode", true, "Indexing mode. Options: CREATE, USE. Default: CREATE");
        Option indexFile = new Option("indexFile", true, "Path for generate/use index file during instrumentation, default: " + Properties.DEFAULT_INDEX_FILE);
        Option instrumentClasses = new Option("instrumentClasses", true, "Required. Names of classes to be instrumented. Separated by ','");
//        Option cpPath = new Option("cpPath", true, "Required. Path storing binaries of classes under test to be instrumented, separated by File.PathSeparator");
        options.addOption(help);
        options.addOption(testResultPrefix);
        options.addOption(testClassNames);
        options.addOption(resultDir);

        options.addOption(instrumentedBinDir);
        options.addOption(indexingMode);
//        options.addOption(createIndex);
//        options.addOption(useIndex);
        options.addOption(indexFile);
        options.addOption(instrumentClasses);
//        options.addOption(cpPath);
        return options;
    }
    public static void retrieveTestClassNames(CommandLine line) throws Exception {
        if(!line.hasOption("testClasses"))
            throw new MissingArgumentException("Missing testClasses argument, required for test case execution");
        Properties.getInstance().setTestClassNames(line.getOptionValue("testClasses").split(","));

        logger.debug("Test Classes: \t\t" + Arrays.toString(Properties.getInstance().getTestClassNames()));
    }
    public static void retrieveTestResultPrefix(CommandLine line){
        String testResultPrefix;
        if(line.hasOption("testResultPrefix"))
            testResultPrefix = line.getOptionValue("testResultPrefix");
        else
            testResultPrefix = Properties.DEFAULT_TEST_RESULT_PREFIX;
        Properties.getInstance().setTestResultPrefix(testResultPrefix);
        logger.debug("Prefix for Files storing test results: \t\t" + Properties.getInstance().getTestResultPrefix());
    }
    public static void retrieveFilePath(CommandLine line){
        File dir;
        if(line.hasOption("resultDir"))
            dir = new File(line.getOptionValue("resultDir"));
        else {
            dir = new File(new File("").getAbsolutePath(), Properties.DEFAULT_RESULT_PATH);
        }
        if(!dir.exists())
            dir.mkdirs();
        Properties.getInstance().setTestResultFilePath(dir.getAbsolutePath());
        logger.debug("Path storing test results: \t\t"+Properties.getInstance().getTestResultFilePath());
    }
    public static void retrieveInstrumentedBinDir(CommandLine line) {
        File dir;
        if(line.hasOption("instrumentedBinDir"))
            dir = new File(line.getOptionValue("instrumentedBinDir"));
        else
            dir = new File(new File("").getAbsolutePath(), Properties.DEFAULT_INSTR_BIN);
        if(!dir.exists())
            dir.mkdirs();
        Properties.getInstance().setInstrumentedBinDir(dir.getAbsolutePath());
        logger.debug("Directory storing instrumented binaries: " + Properties.getInstance().getInstrumentedBinDir());
    }
    public static void retrieveIndexingMode(CommandLine line) {
        if(line.hasOption("indexingMode"))
            Properties.getInstance().setIndexingMode(IndexingMode.valueOf(line.getOptionValue("indexingMode").toUpperCase())); // illegal argument if its not allowed
        else
            Properties.getInstance().setIndexingMode(IndexingMode.CREATE);
        logger.debug("Indexing mode: \t\t "+ Properties.getInstance().getIndexingMode());
    }

    public static void retrieveIndexFile(CommandLine line) {
        File indexFile;
        if(line.hasOption("indexFile")) {
            indexFile = new File(line.getOptionValue("indexFile"));
            if(!indexFile.getParentFile().exists())
                indexFile.getParentFile().mkdirs();
        }
        else {
            indexFile = new File(Properties.DEFAULT_INDEX_FILE);
        }
        if((Properties.getInstance().getIndexingMode()==IndexingMode.USE) && !indexFile.exists())
            throw new IllegalArgumentException("Index file to USE for instrumentation does not exist");
        Properties.getInstance().setIndexFile(indexFile.getAbsolutePath());
        logger.debug("Index file to " + (Properties.getInstance().getIndexingMode()==IndexingMode.CREATE?"write":"read")+": \t\t" + Properties.getInstance().getIndexFile());
    }
    // need to ensure createAndUseIndex extracted BEFORE file as checking of file existance is only needed if it is used
    public static void retrieveIndexInfo(CommandLine line) {
        retrieveIndexingMode(line);
        retrieveIndexFile(line);
    }

//    public static void retrieveCpPath(CommandLine line) throws MissingArgumentException {
//        if(!line.hasOption("cpPath"))
//            throw new MissingArgumentException("Missing cpPath argument");
//        else
//            Properties.getInstance().setCpPath(line.getOptionValue("cpPath"));
//        logger.debug("Path for binaries to instrument: \t\t "+ Properties.getInstance().getCpPath());
//    }

    public static void retrieveInstrumentClasses(CommandLine line) throws MissingArgumentException {
        if(! line.hasOption("instrumentClasses"))
            throw new MissingArgumentException("Please provide the list of classes to instrument");
        Properties.getInstance().setInstrumentClasses(line.getOptionValue("instrumentClasses").split(","));
        logger.debug("Classes to instrument: \t\t" + String.join(",", Properties.getInstance().getInstrumentClasses()));
    }

}
