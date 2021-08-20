package helper.analyzer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class ScoreCalculator {
    private static Logger logger = LoggerFactory.getLogger(ScoreCalculator.class);
    private static final ScoreCalculator singleton = new ScoreCalculator();

    private ScoreCalculator() {
    }

    public static ScoreCalculator getInstance() {
        return singleton;
    }

    public double calculate(String testName, Results results) {
        logger.debug("Calculating score for test "+ testName);
        ResultReport fixedResult = results.getFixedReports();
        List<ResultReport> plausibleReports = results.getPlausibleReports();
        if(!fixedResult.getTestResult(testName)){
            logger.debug("test "+ testName+" failed for fixed version of code");
            return -1;
        }
        double failedCount = plausibleReports.stream().filter(report-> !report.getTestResult(testName)).count();
        return failedCount/(double)(plausibleReports.size());
    }

}
