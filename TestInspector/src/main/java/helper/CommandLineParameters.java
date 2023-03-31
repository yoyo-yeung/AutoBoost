package helper;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.MissingArgumentException;
import org.apache.commons.cli.Options;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ppg.cmds.Command;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;

public class CommandLineParameters {
    private static final Logger logger = LogManager.getLogger(CommandLineParameters.class);
    public static Options getCommandLineOptions(){
        Options options = new Options();
        options.addOption("instrumentedBinPath", true, "Path to store instrumented binary files. Existing binaries inside will be overwritten");
        options.addOption("CUTs", true, "Classes under test, will be instrumented" );
        options.addOption("testClasses", true, "Test classes to be executed");
        options.addOption("testCases", true, "Failing test case's name. It should be in the format of testClass" + Properties.getSingleton().getClassCaseSeparator() +"testCaseName");
        options.addOption("stmtCountFile", true, "File to store no. of statments ran for the test");
        options.addOption("projName", true, "Name of project, will be used as key in file");
        options.addOption("testInfoFolder", true, "Path of folder to store info of test executed");
        return options;
    }
    public static void processInstrumentedBinDir(CommandLine line) throws MissingArgumentException {
        if(!line.hasOption("instrumentedBinPath"))
            throw new MissingArgumentException("Missing argument instrumentedBinPath");
        File folder = new File(line.getOptionValue("instrumentedBinPath"));
        if(!folder.isDirectory())
            folder.mkdirs();
        Properties.getSingleton().setInstrumentedBinPath(folder.getAbsolutePath());
    }
    public static void processCUTs(CommandLine line) throws MissingArgumentException {
        if(!line.hasOption("CUTs"))
            throw new MissingArgumentException("Missing argument CUTs");
        Properties.getSingleton().setCUTs(line.getOptionValue("CUTs").split(","));
    }
    public static void processTestClasses(CommandLine line) throws MissingArgumentException {
        if(!line.hasOption("testClasses"))
            throw new MissingArgumentException("testClasses");
        Properties.getSingleton().setTestClasses(line.getOptionValue("testClasses").split(","));
    }
    public static void processTestCases(CommandLine line) throws MissingArgumentException {
        if(!line.hasOption("testCases"))
            throw new MissingArgumentException("Missing argument testCases");
        Properties properties = Properties.getSingleton();
        String[] cases = line.getOptionValue("testCases").split(",");
        if (Arrays.stream(cases).anyMatch(c -> !c.contains(properties.getClassCaseSeparator())))
            throw new IllegalArgumentException("Incorrect format for testCases");
        Properties.getSingleton().setTestCases(cases);
    }
    public static void processStmtCountFile(CommandLine line) throws IOException, MissingArgumentException {
        if(! line.hasOption("stmtCountFile"))
            throw new MissingArgumentException("Missing argument stmtCountFile");
        File file  = new File(line.getOptionValue("stmtCountFile"));
        file.getParentFile().mkdirs();
        if(! file.exists())
            file.createNewFile();
        Properties.getSingleton().setStmtCountFile(file.getAbsolutePath());
    }
    public static void processProjName(CommandLine line) throws MissingArgumentException {
        if(!line.hasOption("projName"))
            throw new MissingArgumentException("projName");
        Properties.getSingleton().setProjName(line.getOptionValue("projName"));
    }
    public static void processTestInfoFolder(CommandLine line) throws MissingArgumentException {
        if(!line.hasOption("testInfoFolder"))
            throw new MissingArgumentException("Missing argument testInfoFolder");
        File folder = new File(line.getOptionValue("testInfoFolder"));
        folder.mkdirs();
        Properties.getSingleton().setTestInfoFolder(line.getOptionValue("testInfoFolder"));
    }

}
