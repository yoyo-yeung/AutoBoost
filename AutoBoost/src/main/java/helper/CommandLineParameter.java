package helper;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.MissingArgumentException;
import org.apache.commons.cli.Options;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.Arrays;
import java.util.regex.Pattern;

public class CommandLineParameter {
    private static final Logger logger = LogManager.getLogger(CommandLineParameter.class);
    public static Options getCommandLineOptions() {
        Options options = new Options();
        options.addOption("instrumentedBinPath", true, "Required. Path to store instrumented binary files. Existing binaries inside will be overwritten");
//        options.addOption("CUTs", true, "Required. Classes under test, will be instrumented. Separated by " + Properties.getClassSep());
        options.addOption(Help.getOption());
        options.addOption("testCases", true, "Required. Failing test case's name. It should be in the format of testClass" + Properties.getClassMethSep() +"testCaseName. Separated by " + Properties.getClassSep());
        options.addOption("generatedPackage", true, "Required. Package of the generated test cases");
        options.addOption("faultyFunc", true, "Required. Faulty function's subsignatures, each enclosed by <>. Separated by "+ Properties.getClassMethSep() + "e.g. <org.apache.commons.math.analysis.integration.SimpsonIntegrator: double integrate(double,double)>" + Properties.getClassMethSep()+"<org.apache.commons.math.optimization.linear.SimplexTableau: org.apache.commons.math.optimization.RealPointValuePair getSolution()>");
        options.addOption("testSourceDir", true, "Required. Directory storing generated test .java");
        options.addOption("testSuitePrefix", true, "Optional. Prefix of generated test classes. Default: AB");
        options.addOption("faultyClassLine", true, "Required. Faulty class's namd and line number separated by ':', each fault separated by ','");
        options.addOption("junitVer" , true, "Optional. Expected JUnit Version: 3/4. Default: 4");
        options.addOption(Help.getOption());
        return options;
    }
    public static void processCommand(CommandLine line) throws MissingArgumentException {
        processInstrumentationCommand(line);
        processTestingCommand(line);
        processGenerationCommand(line);
        Properties.getSingleton().logProperties();
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
//        if(!line.hasOption("CUTs"))
//            throw new MissingArgumentException("Missing argument for CUTs");
//
//        properties.setCUTs(line.getOptionValue("CUTs").split(Properties.getClassSep()));

    }
    private static void processTestingCommand(CommandLine line) throws MissingArgumentException {
        Properties properties = Properties.getSingleton();
        if(!line.hasOption("testCases"))
            throw new MissingArgumentException("Missing argument for testCases");
        properties.setTestCases(line.getOptionValue("testCases").split(Properties.getClassSep()));
        if(!line.hasOption("faultyFunc") && !line.hasOption("faultyClassLine"))
            throw new MissingArgumentException("Missing argument for faultyFunc");
        if(line.hasOption("faultyFunc"))
            properties.setFaultyFunc(Arrays.asList(line.getOptionValue("faultyFunc").split(Properties.getClassMethSep())));
        if(line.hasOption("faultyClassLine")) {
            Arrays.stream(line.getOptionValue("faultyClassLine").split(",")).forEach(i -> {
                logger.debug(i);
                String[] details = i.split(":");
                if(details.length!=2)
                    throw new IllegalArgumentException("Incorrect argument faultyClassLine");
                properties.addFaultyClassLineMap(details[0], Integer.valueOf(details[1]));
            });
        }
    }

    private static void processGenerationCommand(CommandLine line) throws MissingArgumentException {
        Properties properties = Properties.getSingleton();
        if(!line.hasOption("generatedPackage"))
            throw new MissingArgumentException("Missing argument for generatedPackage");
        properties.setGeneratedPackage(line.getOptionValue("generatedPackage"));
        if(!line.hasOption("testSourceDir"))
            throw new MissingArgumentException("Missing argument for testSourceDir");
        properties.setTestSourceDir(line.getOptionValue("testSourceDir"));
        if(line.hasOption("testSuitePrefix")){
            if(Pattern.compile("[^a-zA-Z0-9]").matcher(line.getOptionValue("testSuitePrefix")).find())
                throw new IllegalArgumentException("Illegal testSuitePrefix, contain special character. ");
            properties.setTestSuitePrefix(line.getOptionValue("testSuitePrefix"));
        }
        if(line.hasOption("junitVer"))
            properties.setJunitVer(Integer.parseInt(line.getOptionValue("junitVer")));

    }
}
