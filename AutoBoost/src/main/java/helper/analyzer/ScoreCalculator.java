package helper.analyzer;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ScoreCalculator {
    private static Logger logger = LoggerFactory.getLogger(ScoreCalculator.class);
    private static final ScoreCalculator singleton = new ScoreCalculator();

    private ScoreCalculator() {
    }

    public static ScoreCalculator getInstance() {
        return singleton;
    }

    public double calTestResult(String testName, Results<ResultReport> results) {
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

    /*
        2 based on path:
        1. simple unique path
        2. avg point of deviation from fixed path (with threshold to accommodate possible effects by def. of fixes
     */
    public double calUniquePath(String testName, Results<ResultReport> results, Results<PathCovReport> pathCovRes) {
        logger.debug("Calculating score for test "+ testName);
        List<JSONArray> allPathCov = pathCovRes.getPlausibleReports().stream().map(rep -> rep.getTestResult(testName)).collect(Collectors.toList());
        allPathCov.add(pathCovRes.getFixedReports().getTestResult(testName));
        return allPathCov.stream().distinct().count()/(double)allPathCov.size(); // use all instead of plausible only, as we also consider the path travelled by fixed version
    }

    public double calPathDiffWithFixed(String testName, Results<ResultReport> results, Results<PathCovReport> pathCovRes, int thr) {
        JSONArray fixedPath = pathCovRes.getFixedReports().getTestResult(testName);
        List<JSONArray> plausiblePaths = pathCovRes.getPlausibleReports().stream().map(rep -> rep.getTestResult(testName)).collect(Collectors.toList());
        double avgDev = plausiblePaths.stream().mapToInt(path-> {
            int localThr = thr;
            int deviationPoint = Math.min(path.size(), fixedPath.size());
            for(int i =0; i < Math.min(path.size(), fixedPath.size());  i ++) {
                if (path.get(i)!=fixedPath.get(i)) {
                    if(localThr <= 0 )
                        deviationPoint = i;
                    else localThr--;
                }
            }
            return deviationPoint;
        }).average().getAsDouble();

        return ((double)plausiblePaths.size())/avgDev; // the earlier deviation occurs, the better
    }

    /*
        two: 1. avg diff with fixed in terms on statement set (larger == better)
            2. no. of unique set
     */
    public double calUniqueStmtSet(String testName, Results<ResultReport> results, Results<StmtSetCovReport> stmtCovRes) {
        // first option: consider only set, not values
        List<Set> allStmtSet = stmtCovRes.getPlausibleReports().stream().map(rep -> rep.getTestResult(testName).keySet()).collect(Collectors.toList());
        allStmtSet.add(stmtCovRes.getFixedReports().getTestResult(testName).keySet());

        return allStmtSet.stream().distinct().count()/(double)allStmtSet.size();
    }

    public double calSetDiffWithFixed(String testName, Results<ResultReport> results, Results<StmtSetCovReport> stmtCovRes) {
        Set fixedStmtSet = stmtCovRes.getFixedReports().getTestResult(testName).keySet();
        List<Set> plauStmtSet = stmtCovRes.getPlausibleReports().stream().map(rep -> rep.getTestResult(testName).keySet()).collect(Collectors.toList());
        double avgDiff = plauStmtSet.stream().mapToInt(plau -> {
            Set symmetricDiff = new HashSet(plau);
            symmetricDiff.addAll(fixedStmtSet);
            Set tmp = new HashSet(plau);
            tmp.retainAll(fixedStmtSet);  //get common ones
            symmetricDiff.removeAll(tmp);
            return symmetricDiff.size();
        }).average().getAsDouble();
        return avgDiff/(double)plauStmtSet.size();
    }
}
