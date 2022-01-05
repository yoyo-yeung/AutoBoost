package helper;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.MissingArgumentException;
import org.apache.commons.cli.Options;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.net.URI;
import java.nio.file.Paths;

public class CommandLineParameter {
    private static final Logger logger = LogManager.getLogger(CommandLineParameter.class);
    public static Options getCommandLineOptions() {
        Options options = new Options();
        options.addOption("instrumentedBinPath", true, "Path to store instrumented binary files. Existing binaries inside will be overwritten");
        options.addOption("CUTs", true, "Classes under test, will be instrumented. Separated by " + Properties.getClassSep());
        options.addOption(Help.getOption());
        options.addOption("testCases", true, "Failing test case's name. It should be in the format of testClass" + Properties.getClassMethSep() +"testCaseName. Separated by " + Properties.getClassSep());
        options.addOption("generatedPackage", true, "Package of the generated test cases");
        return options;
    }
    public static void processCommand(CommandLine line) throws MissingArgumentException {
        processInstrumentationCommand(line);
        processTestingCommand(line);
    }
    private static void processInstrumentationCommand(CommandLine line) throws MissingArgumentException {
        Properties properties = Properties.getSingleton();
        // process instrumentedBinPath option
        if(!line.hasOption("instrumentedBinPath"))
            throw new MissingArgumentException("Missing argument for instrumentedBinPath");
        File dir = new File(line.getOptionValue("instrumentedBinPath"));
        if(dir.exists() && dir.isDirectory() && dir.canRead())
            properties.setInsBinPath(dir.getAbsolutePath());
        else throw new IllegalArgumentException("Illegal argument for instrumentedBinPath");

        // process CUTs option
        if(!line.hasOption("CUTs"))
            throw new MissingArgumentException("Missing argument for CUTs");
        properties.setCUTs(line.getOptionValue("CUTs").split(Properties.getClassSep()));
    }
    private static void processTestingCommand(CommandLine line) throws MissingArgumentException {
        Properties properties = Properties.getSingleton();
        if(!line.hasOption("testCases"))
            throw new MissingArgumentException("Missing argument for testCases");
        properties.setTestCases(line.getOptionValue("testCases").split(Properties.getClassSep()));
        if(!line.hasOption("generatedPackage"))
            throw new MissingArgumentException("Missing argument for generatedPackage");
        properties.setGeneratedPackage(line.getOptionValue("generatedPackage"));
    }
}
