package application;


import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import helper.CommandLineParameters;
import helper.Help;
import helper.Properties;
import helper.analyzer.ResultReport;
import helper.analyzer.Results;
import helper.analyzer.ScoreCalculator;
import helper.analyzer.TestDetails;
import helper.testing.TestExecuter;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.ParseException;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class AutoBoost {
    private static final Logger logger = LoggerFactory.getLogger(AutoBoost.class);;
    public static final String FIXED_CODE_FILENAME = "fixed";
    public static final String PATCH_FILE_PREFIX = "patch-";
    public static final String FILE_FORMAT = ".json";
    public Results testResults = new Results();
    public List<TestDetails> allTests;
    public static void main(String[] args) throws IOException, InterruptedException, org.json.simple.parser.ParseException {
        AutoBoost autoBoost = new AutoBoost();
        autoBoost.processCommand(args);
//        autoBoost.executeTests();
        autoBoost.processResults();
        autoBoost.calculateScores();
        JSONObject obj = new JSONObject();
        for(TestDetails testDetails : autoBoost.allTests){
            obj.put(testDetails.getTestName(), testDetails.getScore());
        }
        System.out.println(obj);

    }

    public void processCommand(String[] args){
        logger.debug("Processing arguments");
        CommandLineParser parser = new GnuParser();
        try{
            CommandLine line = parser.parse(CommandLineParameters.getCommandLineOptions(), args);
            if(line.hasOption(Help.NAME)) {
                Help.execute(CommandLineParameters.getCommandLineOptions());
                System.exit(0);
            }
            CommandLineParameters.handleFixedPath(line);
            CommandLineParameters.handlePlausibleFixesPaths(line);
            CommandLineParameters.handleTestClassPaths(line);
            CommandLineParameters.handleTestClassNames(line);
            CommandLineParameters.handleTestRunnerJarPath(line);
            CommandLineParameters.handleTestRunnerClass(line);
            CommandLineParameters.handleDependencyPaths(line);
            CommandLineParameters.handleResultDir(line);
        } catch (ParseException e) {
            logger.error(e.getMessage());
            logger.debug("Error: "+ Arrays.toString(e.getStackTrace()));
            System.exit(-1);
        }
    }

    public void executeTests() throws IOException, InterruptedException {
        List<String> commandList = IntStream.range(0, Properties.getInstance().getUnacceptedClassPaths().length).mapToObj(i -> TestExecuter.getInstance().composeTestCommand(Properties.getInstance().getUnacceptedClassPaths()[i], PATCH_FILE_PREFIX+i + FILE_FORMAT)).collect(Collectors.toList());
        commandList.add(TestExecuter.getInstance().composeTestCommand(Properties.getInstance().getFixedClassPath(), FIXED_CODE_FILENAME+FILE_FORMAT));
        Process proc;
        for(int i =0 ; i < commandList.size() ; i++){
            logger.info("Executing test on patch no. " + i);
            proc = TestExecuter.getInstance().executeCommands(commandList.get(i));
            proc.waitFor();
        }
    }
    public void processResults() throws IOException, org.json.simple.parser.ParseException {
        logger.info("Processing Results in folder " + Properties.getInstance().getResultDir());
        testResults.setFixedReports(new ResultReport(new File(Properties.getInstance().getResultDir()+"/"+FIXED_CODE_FILENAME+FILE_FORMAT)));
        testResults.setPlausibleReports(IntStream.range(0, Properties.getInstance().getUnacceptedClassPaths().length).mapToObj(i-> {
            try {
                return new ResultReport(new File(Properties.getInstance().getResultDir()+"/"+ PATCH_FILE_PREFIX+i+FILE_FORMAT));
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }).filter(rep -> rep!=null).collect(Collectors.toList()));
        allTests = testResults.getFixedReports().getKeys().stream().map(key -> new TestDetails(key)).collect(Collectors.toList());
    }

    public void calculateScores() {
        logger.info("Calculating score for each test");
//        logger.debug(testResults.getFixedReports().toString());
        allTests.stream().forEach(test-> test.setScore(ScoreCalculator.getInstance().calculate(test.getTestName(), testResults)));
        logger.debug(allTests.toString());
    }
}
