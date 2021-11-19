package application;

import helper.CommandLineParameters;
import helper.Help;
import helper.Properties;
import instrumentation.Counter;
import instrumentation.Instrumenter;
import instrumentation.MethodLogger;
import instrumentation.TestInfo;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
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
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class TestInspector {
    private static final Logger logger = LogManager.getLogger(TestInspector.class);
    private HashMap<String, TestInfo> testInfoHashMap = new HashMap<>();

    public static void main(String... args) throws IOException {
        TestInspector inspector = new TestInspector();
        inspector.processCommand(args);
//        inspector.setUpProgram();
        inspector.setUpSoot();
        inspector.executeTests();
        inspector.output();
//        logger.debug(Counter.getStmtIdMap().values().stream().sorted().collect(Collectors.toList()).toString());
    }
    public void processCommand(String... args) {
        CommandLineParser parser = new GnuParser();
        try {
            CommandLine line = parser.parse(CommandLineParameters.getCommandLineOptions(), args);
            if(line.hasOption(Help.NAME))
                Help.execute(CommandLineParameters.getCommandLineOptions());
            CommandLineParameters.processInstrumentedBinDir(line);
            CommandLineParameters.processCUTs(line);
            CommandLineParameters.processTestClasses(line);
            CommandLineParameters.processTestCases(line);
            CommandLineParameters.processStmtCountFile(line);
            CommandLineParameters.processProjName(line);
            CommandLineParameters.processTestInfoFolder(line);

        } catch (Exception e) {
            logger.error(e.getMessage());
        }
    }
    public void setUpProgram() {
        Arrays.stream(Properties.getSingleton().getCUTs()).forEach(c -> {
            try {
                Arrays.stream(Class.forName(c).getDeclaredFields()).forEach(f -> {
                    logger.debug(Modifier.toString(f.getModifiers()));
                    try{f.setAccessible(true);
//                    Field modifiersField = Field.class.getDeclaredField("modifiers");
//                    modifiersField.setAccessible(true);
//                    modifiersField.setInt(f, f.getModifiers() & ~Modifier.PRIVATE );
                }catch
                (Exception e ){logger.debug(e.getMessage());}});
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
    public void setUpSoot() {
        Properties properties = Properties.getSingleton();
        Options.v().set_soot_classpath(Scene.v().defaultClassPath() + File.pathSeparator + properties.getInstrumentedBinPath() + File.pathSeparator + System.getProperty("java.class.path"));
        Options.v().set_output_dir(properties.getInstrumentedBinPath());
        Options.v().set_keep_line_number(true);
        Options.v().setPhaseOption("jb", "use-original-names:true");
//        Options.v().setm
        Pack jtp = PackManager.v().getPack("jtp");
        Instrumenter instrumenter = new Instrumenter();
        jtp.add(new Transform("jtp.instrumenter", instrumenter));
        logger.debug("Instrumentation begins");
        soot.Main.main(properties.getCUTs());
    }
    public void executeTests() {
        JUnitCore junit = new JUnitCore();
        Properties properties = Properties.getSingleton();
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
//                properties.getTestCases()
                String testName = description.getTestClass().getName()+properties.getClassCaseSeparator() + description.getMethodName();
                testInfoHashMap.put(testName, new TestInfo(Counter.getOccuranceCounter(), MethodLogger.getMethodParamValueMap(), MethodLogger.getMethodCallOrder()));
                Counter.reset();
                MethodLogger.reset();
            }

            @Override
            public void testFailure(Failure failure) throws Exception {
                logger.error(failure.getDescription().getMethodName()+" failed\n" + failure.getTrace());
            }
        });
        Arrays.stream(properties.getTestCases()).map(t -> {
            try {
                String[] testDetails = t.split(properties.getClassCaseSeparator());
                Request req = Request.method(Class.forName(testDetails[0]), testDetails[1]);
                return req;
            } catch (ClassNotFoundException e) {
                logger.error("Cannot find test case " + t + "for execution");
                return null;
            }
        }).filter(Objects::nonNull).forEach(req -> junit.run(req));
//        junit.run(testClasses);
    }
    public void output() throws IOException {
//        logger.debug(this.testInfoHashMap);
        Properties properties = Properties.getSingleton();
        FileOutputStream stream = new FileOutputStream(properties.getStmtCountFile(), true);
        // only matching ones are stored
        StringBuilder b = new StringBuilder(20);
        b.append(properties.getProjName()).append("\n").append(this.testInfoHashMap.values().stream().mapToLong(TestInfo::getTotalStmtRan).sum()).append("\n\n");

        stream.write(b.toString().getBytes(StandardCharsets.UTF_8));
        stream.close();
//        logger.info(this.testInfoHashMap.entrySet().stream().map(e -> e.getValue().getMethodParamValueMap().toString()).collect(Collectors.joining()));

        File file = new File(properties.getTestInfoFolder(), properties.getProjName()+".txt");
        if(!file.exists())
            file.createNewFile();
        stream = new FileOutputStream(file.getAbsoluteFile(), false);
        String content = this.testInfoHashMap.entrySet().stream().map(entry ->
            entry.getKey() + ":\n\n" + entry.getValue().getMethodParamValueMap().entrySet().stream().map(mdParamMap -> mdParamMap.getKey()+":\n"+mdParamMap.getValue().stream().map(exeParams -> exeParams.stream().map(kvMap-> kvMap.getKey()+"="+kvMap.getValue()).collect(Collectors.joining(","))).distinct().collect(Collectors.joining("\n"))).collect(Collectors.joining("\n"))
        ).collect(Collectors.joining("\n\n"));
        stream.write(content.toString().getBytes(StandardCharsets.UTF_8));;
//        stream.write(abc.getBytes(StandardCharsets.UTF_8));
        stream.close();
    }
}
