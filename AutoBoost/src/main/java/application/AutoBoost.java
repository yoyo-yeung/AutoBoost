package application;


import helper.CommandLineParameters;
import helper.Help;
import helper.Properties;
import helper.analyzer.*;
import helper.testing.TestExecuter;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class AutoBoost {
//    public static final String FILE_FORMAT = ".json";
    private static final Logger logger = LoggerFactory.getLogger(AutoBoost.class);
    private Results<ResultReport> testResults = new Results<ResultReport>();
    private Results<PathCovReport> pathCovRes = new Results<PathCovReport>();
    private Results<StmtSetCovReport> stmtSetCovRes = new Results<StmtSetCovReport>();
    private List<TestDetails> allTests;
    private List<String> testNames;

    public static void main(String[] args) throws IOException, InterruptedException, org.json.simple.parser.ParseException {
        AutoBoost autoBoost = new AutoBoost();
        autoBoost.processCommand(args);
        autoBoost.executeTests();
        autoBoost.processResults();
    }

    public void processCommand(String[] args) {
        logger.debug("Processing arguments");
        CommandLineParser parser = new GnuParser();
        try {
            CommandLine line = parser.parse(CommandLineParameters.getCommandLineOptions(), args);
            if (line.hasOption(Help.NAME)) {
                Help.execute(CommandLineParameters.getCommandLineOptions());
                System.exit(0);
            }
            CommandLineParameters.handleFixedPath(line);
            CommandLineParameters.handlePlausibleFixesPaths(line);
            CommandLineParameters.handleInstrumentClasses(line);
            CommandLineParameters.handleTestClassPaths(line);
            CommandLineParameters.handleTestClassNames(line);
            CommandLineParameters.handleTestRunnerJarPath(line);
            CommandLineParameters.handleTestRunnerClass(line);
            CommandLineParameters.handleDependencyPaths(line);
            CommandLineParameters.handleResultDir(line);
        } catch (ParseException | IOException e) {
            logger.error(e.getMessage());
            logger.debug("Error: " + Arrays.toString(e.getStackTrace()));
            System.exit(-1);
        }
    }

    public void executeTests() throws IOException, InterruptedException {
        Process proc;
        String commandForFixed = TestExecuter.getInstance().composeTestCommand(Properties.getInstance().getFixedClassPath(), Properties.FIXED_FILE_PREFIX, true);
        int exitVal = 0 ;

        logger.debug(commandForFixed);
        logger.info("Executing test on fixed version");
        proc = TestExecuter.getInstance().executeCommands(commandForFixed);
        exitVal = proc.waitFor();
        logger.debug("Process exitValue: " + exitVal);

        List<String> commandList = new ArrayList<>(IntStream.range(0, Properties.getInstance().getUnacceptedClassPaths().length).mapToObj(i -> TestExecuter.getInstance().composeTestCommand(Properties.getInstance().getUnacceptedClassPaths()[i], Properties.PATCH_FILE_PREFIX + i, false)).collect(Collectors.toList()));
        for (int i = 0; i < commandList.size(); i++) {
            logger.info("Executing test on patch no. " + i);
            proc = TestExecuter.getInstance().executeCommands(commandList.get(i));
            exitVal = proc.waitFor();
            logger.debug("Process exitValue: " + exitVal);
        }
    }

    public void processResults() throws IOException, org.json.simple.parser.ParseException {
        logger.info("Processing Results in folder " + Properties.getInstance().getResultDir());
        testResults.setFixedReports(new ResultReport(new File(Properties.getInstance().getResultDir()+"/"+Properties.FIXED_FILE_PREFIX +Properties.RESULT_FILE_SUFFIX)));
        testResults.setPlausibleReports(IntStream.range(0, Properties.getInstance().getUnacceptedClassPaths().length).mapToObj(i-> {
            try {
                return new ResultReport(new File(Properties.getInstance().getResultDir()+"/"+Properties.PATCH_FILE_PREFIX+i+Properties.RESULT_FILE_SUFFIX));
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }).filter(rep -> rep!=null).collect(Collectors.toList()));
        allTests = testResults.getFixedReports().getKeys().stream().map(key -> new TestDetails(key)).collect(Collectors.toList());
        testNames = testResults.getFixedReports().getKeys();
        pathCovRes.setFixedReports(new PathCovReport(new File(Properties.getInstance().getResultDir()+"/"+Properties.FIXED_FILE_PREFIX +Properties.PATH_COV_FILE_SUFFIX)));
        pathCovRes.setPlausibleReports(IntStream.range(0, Properties.getInstance().getUnacceptedClassPaths().length).mapToObj(i-> {
            try {
                return new PathCovReport(new File(Properties.getInstance().getResultDir()+"/"+Properties.PATCH_FILE_PREFIX+i+Properties.PATH_COV_FILE_SUFFIX));
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }).filter(rep-> rep!=null).collect(Collectors.toList()));
        stmtSetCovRes.setFixedReports(new StmtSetCovReport(new File(Properties.getInstance().getResultDir()+"/"+Properties.FIXED_FILE_PREFIX+Properties.STMT_SET_COV_FILE_SUFFIX)));
        stmtSetCovRes.setPlausibleReports(IntStream.range(0, Properties.getInstance().getUnacceptedClassPaths().length).mapToObj(i-> {
            try {
                return new StmtSetCovReport(new File(Properties.getInstance().getResultDir()+"/"+Properties.PATCH_FILE_PREFIX+i+Properties.STMT_SET_COV_FILE_SUFFIX));
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }).filter(rep-> rep!=null).collect(Collectors.toList()));
    }
}
