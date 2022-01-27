package application;

import helper.CommandLineParameter;
import helper.Help;
import helper.Properties;
import org.junit.runner.Description;
import org.junit.runner.JUnitCore;
import org.junit.runner.Request;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;
import program.generation.TestGenerator;
import program.instrumentation.Instrumenter;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.ParseException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import soot.*;
import soot.options.Options;

import java.io.File;
import java.util.*;

public class AutoBoost {
    private static final Logger logger = LogManager.getLogger(AutoBoost.class);
    private final Properties properties = Properties.getSingleton();
    public static void main(String... args) throws ParseException {
        AutoBoost autoBoost = new AutoBoost();
        autoBoost.processCommand(args);
        autoBoost.setUpSoot();
        autoBoost.executeTests();
        autoBoost.generateTestCases();
    }
    public void processCommand(String... args) throws ParseException {
        CommandLineParser parser = new DefaultParser();
        try {
            CommandLine line = parser.parse(CommandLineParameter.getCommandLineOptions(), args);
            if(line.hasOption(Help.getOption()))
                Help.execute(CommandLineParameter.getCommandLineOptions());
            CommandLineParameter.processCommand(line);
        }
         catch (ParseException e) {
            logger.error(Arrays.toString(e.getStackTrace()));
            throw e;
        }
    }
    public void setUpSoot(){
        Options.v().set_soot_classpath(Scene.v().defaultClassPath() + File.pathSeparator + properties.getInsBinPath() + File.pathSeparator + System.getProperty("java.class.path"));
        Options.v().set_process_dir(Arrays.asList(properties.getInsBinPath()));
        Options.v().set_output_dir(properties.getInsBinPath());
        Options.v().set_keep_line_number(true);
        Options.v().setPhaseOption("jb", "use-original-names:true");
        Pack jtp = PackManager.v().getPack("jtp");
        Instrumenter instrumenter = new Instrumenter();
        jtp.add(new Transform("jtp.instrumenter", instrumenter));
        logger.info("Instrumentation begins");
        soot.Main.main(Scene.v().getBasicClasses().toArray(new String[0]));
//        logger.debug(InstrumentResult.getSingleton().getClassAnalysis().toString());
    }
    public void executeTests() {
        logger.info("Execute tests");
        JUnitCore junit = new JUnitCore();
        junit.addListener(new RunListener() {
            public void testRunStarted(Description description) {
                logger.debug("Test execution started");
            }
            public void testRunFinished(Result result) {
                logger.debug(result.getRunCount() + " tests executed");
                logger.debug(result.getFailureCount() + " tests failed");
            }
            public void testStarted(Description description) {
                logger.debug(description.getMethodName());
            }

            @Override
            public void testFinished(Description description) throws Exception {

            }

            @Override
            public void testFailure(Failure failure) throws Exception {
                logger.error(failure.getDescription().getMethodName()+" failed\n" + failure.getTrace());
            }
        });
        Arrays.stream(properties.getTestCases()).map(t -> {
            String[] test = t.split(Properties.getClassMethSep());
            try {
                if(test.length!=2)
                    return null;
                return Request.method(Class.forName(test[0]), test[1]);
            } catch (ClassNotFoundException e) {
                logger.error("Cannot find test case " + t + " for execution.");
                return null;
            }
        }).filter(Objects::nonNull).forEach(junit::run);
    }

    public void generateTestCases() {
        TestGenerator testGenerator = TestGenerator.getSingleton();
        testGenerator.generateResultCheckingTests();
        testGenerator.generateSameThisCheckingTests();
        logger.debug(testGenerator.getTestCases().size());
    }
}
