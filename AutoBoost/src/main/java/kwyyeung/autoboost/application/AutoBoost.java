package kwyyeung.autoboost.application;

import kwyyeung.autoboost.helper.CommandLineParameter;
import kwyyeung.autoboost.helper.Help;
import kwyyeung.autoboost.helper.Properties;
import kwyyeung.autoboost.program.execution.ExecutionLogger;
import kwyyeung.autoboost.program.execution.ExecutionTrace;
import kwyyeung.autoboost.program.execution.MethodExecution;
import kwyyeung.autoboost.program.generation.TestGenerator;
import kwyyeung.autoboost.program.instrumentation.InstrumentResult;
import kwyyeung.autoboost.program.instrumentation.Instrumenter;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.ParseException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.runner.Description;
import org.junit.runner.JUnitCore;
import org.junit.runner.Request;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;
import soot.Pack;
import soot.PackManager;
import soot.Scene;
import soot.Transform;
import soot.options.Options;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class AutoBoost {
    private static final Logger logger = LogManager.getLogger(AutoBoost.class);
    private static final kwyyeung.autoboost.helper.Properties properties = kwyyeung.autoboost.helper.Properties.getSingleton();
    private static String executingTest = null;
    private static PROGRAM_STATE currentProgramState = PROGRAM_STATE.PROCESSING;

    public static void main(String... args) throws ParseException, IOException {
        AutoBoost autoBoost = new AutoBoost();
        autoBoost.processCommand(args);
        autoBoost.setUpSoot();
        logger.info("Faulty methods: " + properties.getFaultyFuncIds().stream().map(id -> InstrumentResult.getSingleton().getMethodDetailByID(id).toString()).collect(Collectors.joining(",")));
        autoBoost.executeTests();
//        autoBoost.clearRuntimeOnlyInfo();

//        ExecutionTrace.getSingleton().checkTestabilityOfExecutions();
        currentProgramState = PROGRAM_STATE.TEST_GENERATION;
        autoBoost.generateTestCases();

    }

    public void processCommand(String... args) throws ParseException {
        CommandLineParser parser = new DefaultParser();
        try {
            CommandLine line = parser.parse(CommandLineParameter.getCommandLineOptions(), args);
            if (line.hasOption(Help.getOption()))
                Help.execute(CommandLineParameter.getCommandLineOptions());
            CommandLineParameter.processCommand(line);
        } catch (ParseException e) {
            logger.error(Arrays.toString(e.getStackTrace()));
            throw e;
        }
    }

    public void setUpSoot() {
        Options.v().set_soot_classpath(Scene.v().defaultClassPath() + File.pathSeparator + properties.getInsBinPath() + File.pathSeparator + System.getProperty("java.class.path"));
        Options.v().set_process_dir(Collections.singletonList(properties.getInsBinPath()));
        Options.v().set_output_dir(properties.getInsBinPath());
        Options.v().set_keep_line_number(true);
        Options.v().setPhaseOption("jb", "use-original-names:true");
        Options.v().set_allow_phantom_refs(true);
//        Options.v().set_include_all(true);
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
            @Override
            public void testRunStarted(Description description) {
//                logger.info("Test execution started");
            }

            @Override
            public void testRunFinished(Result result) {
            }

            @Override
            public void testStarted(Description description) {
                executingTest = description.getDisplayName();
                logger.info("Test " + description.getDisplayName() + " started ");
                ExecutionLogger.clearExecutingStack();
                currentProgramState = PROGRAM_STATE.TEST_EXECUTION;
            }

            @Override
            public void testFailure(Failure failure) throws Exception {
                logger.error(failure.getDescription().getDisplayName() + " failed\n" + failure.getTrace());
            }

            @Override
            public void testFinished(Description description) throws Exception {
                executingTest = null;
                ExecutionLogger.clearExecutingStack();
                currentProgramState = PROGRAM_STATE.TEST_LOG;

            }
        });
        Arrays.stream(properties.getTestCases()).map(t -> {
            String[] test = t.split(Properties.getClassMethSep());
            try {
                if (test.length != 2)
                    return null;
                return Request.method(Class.forName(test[0]), test[1]);
            } catch (ClassNotFoundException e) {
                logger.error("Cannot find test case " + t + " for execution.");
                return null;
            }
        }).filter(Objects::nonNull).forEach(junit::run);
    }

    public void generateTestCases() throws IOException {
        TestGenerator testGenerator = TestGenerator.getSingleton();
        logger.info("Test generation starting");
        List<MethodExecution> snapshot = ExecutionTrace.getSingleton().getAllMethodExecs().values().stream().filter(e -> e.getTest()!=null).collect(Collectors.toList());
        try {
            testGenerator.generateTestCases(snapshot);
//            testGenerator.generateResultCheckingTests(snapshot);
//            testGenerator.generateExceptionTests(snapshot);
        } catch (Exception | Error e) {
            logger.error(e.getClass()  + "\t" + e.getMessage());
            logger.error(Arrays.stream(e.getStackTrace()).map(StackTraceElement::toString).collect(Collectors.joining("\n")));
        } finally {
            ExecutionTrace.getSingleton().clear();
            testGenerator.output();
        }

    }

    public static String getExecutingTest() {
        return executingTest;
    }

    public void clearRuntimeOnlyInfo() {
        InstrumentResult.getSingleton().clearClassDetails();
        ExecutionLogger.clearExecutingStack();
    }

    public static PROGRAM_STATE getCurrentProgramState() {
        return currentProgramState;
    }

    public static void setCurrentProgramState(PROGRAM_STATE currentProgramState) {
        AutoBoost.currentProgramState = currentProgramState;
    }
}
