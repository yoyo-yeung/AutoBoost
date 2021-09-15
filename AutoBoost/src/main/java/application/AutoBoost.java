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
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class AutoBoost {
    //    public static final String FILE_FORMAT = ".json";
    private final String SCORE_FILE = "score.csv";
    private static final Logger logger = LoggerFactory.getLogger(AutoBoost.class);
    private final Results<ResultReport> testResults = new Results<ResultReport>();
    private final Results<PathCovReport> pathCovRes = new Results<PathCovReport>();
    private final Results<StmtSetCovReport> stmtSetCovRes = new Results<StmtSetCovReport>();
    private final List<TestDetails> allTests = new ArrayList<TestDetails>();

    public static void main(String[] args) throws IOException, InterruptedException, org.json.simple.parser.ParseException {
        AutoBoost autoBoost = new AutoBoost();
        autoBoost.processCommand(args);
        autoBoost.executeTests();
        autoBoost.processResults();
        autoBoost.calculateScores();
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
        Properties properties = Properties.getInstance();
        Process proc;
        String commandForFixed = TestExecuter.getInstance().composeTestCommand(properties.getFixedClassPath(), Properties.FIXED_FILE_PREFIX, true);
        int exitVal = 0;

        logger.debug(commandForFixed);
        logger.info("Executing test on fixed version");
        proc = TestExecuter.getInstance().executeCommands(commandForFixed);
        exitVal = proc.waitFor();
        logger.debug("Process exitValue: " + exitVal);

        List<String> commandList = new ArrayList<>(IntStream.range(0, properties.getUnacceptedClassPaths().length).mapToObj(i -> TestExecuter.getInstance().composeTestCommand(properties.getUnacceptedClassPaths()[i], Properties.PATCH_FILE_PREFIX + i, false)).collect(Collectors.toList()));
        for (int i = 0; i < commandList.size(); i++) {
            logger.info("Executing test on patch no. " + i);
            proc = TestExecuter.getInstance().executeCommands(commandList.get(i));
            exitVal = proc.waitFor();
            logger.debug("Process exitValue: " + exitVal);
        }
    }

    public void processResults() throws IOException, org.json.simple.parser.ParseException {
        Properties properties = Properties.getInstance();
        logger.info("Processing Results in folder " + properties.getResultDir());
        testResults.setFixedReports(new ResultReport(new File(properties.getResultDir() + File.separator + Properties.FIXED_FILE_PREFIX + Properties.RESULT_FILE_SUFFIX)));
        testResults.setPlausibleReports(IntStream.range(0, properties.getUnacceptedClassPaths().length).mapToObj(i -> {
            try {
                return new ResultReport(new File(properties.getResultDir() + File.separator + Properties.PATCH_FILE_PREFIX + i + Properties.RESULT_FILE_SUFFIX));
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }).filter(rep -> rep != null).collect(Collectors.toList()));
        pathCovRes.setFixedReports(new PathCovReport(new File(properties.getResultDir() + File.separator + Properties.FIXED_FILE_PREFIX + Properties.PATH_COV_FILE_SUFFIX)));
        pathCovRes.setPlausibleReports(IntStream.range(0, properties.getUnacceptedClassPaths().length).mapToObj(i -> {
            try {
                return new PathCovReport(new File(properties.getResultDir() + File.separator + Properties.PATCH_FILE_PREFIX + i + Properties.PATH_COV_FILE_SUFFIX));
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }).filter(rep -> rep != null).collect(Collectors.toList()));
        stmtSetCovRes.setFixedReports(new StmtSetCovReport(new File(properties.getResultDir() + File.separator + Properties.FIXED_FILE_PREFIX + Properties.STMT_SET_COV_FILE_SUFFIX)));
        stmtSetCovRes.setPlausibleReports(IntStream.range(0, properties.getUnacceptedClassPaths().length).mapToObj(i -> {
            try {
                return new StmtSetCovReport(new File(properties.getResultDir() + File.separator + Properties.PATCH_FILE_PREFIX + i + Properties.STMT_SET_COV_FILE_SUFFIX));
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }).filter(rep -> rep != null).collect(Collectors.toList()));
    }

    public void calculateScores() {
        ScoreCalculator calculator = ScoreCalculator.getInstance();
        List<String> testNames = testResults.getFixedReports().getKeys();
        testNames.forEach(name -> {
            TestDetails detail = new TestDetails(name);
            detail.addScore("testResult", calculator.calTestResult(name, this.testResults));
            detail.addScore("uniquePaths", calculator.calUniquePath(name, this.testResults, this.pathCovRes));
            detail.addScore("pathDeviation_thr0", calculator.calPathDiffWithFixed(name, this.testResults, this.pathCovRes, 0));
            detail.addScore("pathDeviation_thr1", calculator.calPathDiffWithFixed(name, this.testResults, this.pathCovRes, 1));//guessing the statement no. of the fixed line, change according to result
            detail.addScore("pathDeviation_thr2", calculator.calPathDiffWithFixed(name, this.testResults, this.pathCovRes, 2));//possible values: avg. no. of statements in all fixes (may be more troublesome)
            detail.addScore("pathDeviation_thr3", calculator.calPathDiffWithFixed(name, this.testResults, this.pathCovRes, 3));//possible values: no. of statements in modified line of correct fix
            detail.addScore("uniqueStmtSets", calculator.calSetDiffWithFixed(name, this.testResults, this.stmtSetCovRes));
            detail.addScore("uniqueStmtSets", calculator.calUniqueStmtSet(name, this.testResults, this.stmtSetCovRes));
            detail.addScore("setDeviation", calculator.calSetDiffWithFixed(name, this.testResults, this.stmtSetCovRes));
            allTests.add(detail);
        });
    }
}
