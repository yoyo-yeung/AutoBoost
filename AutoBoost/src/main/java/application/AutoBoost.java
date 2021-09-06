package application;


import helper.CommandLineParameters;
import helper.Help;
import helper.Properties;
import helper.analyzer.Results;
import helper.analyzer.ScoreCalculator;
import helper.analyzer.TestDetails;
import helper.testing.TestExecuter;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class AutoBoost {
    public static final String FILE_FORMAT = ".json";
    private static final Logger logger = LoggerFactory.getLogger(AutoBoost.class);
    public Results testResults = new Results();
    public List<TestDetails> allTests;

    public static void main(String[] args) throws IOException, InterruptedException, org.json.simple.parser.ParseException {
        AutoBoost autoBoost = new AutoBoost();
        autoBoost.processCommand(args);
        autoBoost.executeTests();
//        autoBoost.processResults();
//        autoBoost.calculateScores();
//        JSONObject obj = new JSONObject();
//        for(TestDetails testDetails : autoBoost.allTests){
//            obj.put(testDetails.getTestName(), testDetails.getScore());
//        }
//        System.out.println(obj);

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
//        testResults.setFixedReports(new ResultReport(new File(Properties.getInstance().getResultDir()+"/"+Properties.FIXED_FILE_PREFIX +FILE_FORMAT)));
//        testResults.setPlausibleReports(IntStream.range(0, Properties.getInstance().getUnacceptedClassPaths().length).mapToObj(i-> {
//            try {
//                return new ResultReport(new File(Properties.getInstance().getResultDir()+"/"+Properties.PATCH_FILE_PREFIX+i+FILE_FORMAT));
//            } catch (Exception e) {
//                e.printStackTrace();
//                return null;
//            }
//        }).filter(rep -> rep!=null).collect(Collectors.toList()));
//        logger.debug("fixed reports:");
//        logger.debug(testResults.getFixedReports().toString());

        allTests = testResults.getFixedReports().getKeys().stream().map(key -> new TestDetails(key)).collect(Collectors.toList());

        logger.debug("test names:");
        logger.debug(allTests.toString());

    }

    public void calculateScores() {
        logger.info("Calculating score for each test");
//        logger.debug(testResults.getFixedReports().toString());
        allTests.stream().forEach(test -> test.setScore(ScoreCalculator.getInstance().calculate(test.getTestName(), testResults)));
        logger.debug(allTests.toString());
    }
}
