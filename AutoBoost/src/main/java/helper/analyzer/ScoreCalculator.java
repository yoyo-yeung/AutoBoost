package helper.analyzer;

import org.json.simple.JSONArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ScoreCalculator {
    private static final Logger logger = LoggerFactory.getLogger(ScoreCalculator.class);
    private static final ScoreCalculator singleton = new ScoreCalculator();

    private ScoreCalculator() {
    }

    public static ScoreCalculator getInstance() {
        return singleton;
    }

    public double calTestResult(String testName, Results<ResultReport> results) {
        logger.debug("Calculating score for test " + testName);
        ResultReport fixedResult = results.getFixedReports();
        List<ResultReport> plausibleReports = results.getPlausibleReports();
        if (!fixedResult.getTestResult(testName)) {
            logger.debug("test " + testName + " failed for fixed version of code");
            return -1;
        }
        double failedCount = plausibleReports.stream().filter(report -> !report.getTestResult(testName)).count();
        return failedCount / (double) (plausibleReports.size()); // failed count larger -> ability to 'kill' plausible fix mutants -> better
    }

    /*
        2 based on path:
        1. simple unique path
        2. avg point of deviation from fixed path (with threshold to accommodate possible effects by def. of fixes
     */
    public double calUniquePath(String testName, Results<ResultReport> results, Results<PathCovReport> pathCovRes) {
        logger.debug("Calculating score for test " + testName);
        return getNoOfUniquePaths(testName, pathCovRes);
//                / (double) (pathCovRes.getPlausibleReports().size() + 1); // use all instead of plausible only, as we also consider the path travelled by fixed version
        // no. of distinct path travelled larger -> would spawn different parts of program when given different code same input -> better
    }
    public long getNoOfUniquePaths(String testName, Results<PathCovReport> pathCovRes) {
        List<JSONArray> allPathCov = pathCovRes.getPlausibleReports().stream().map(rep -> rep.getTestResult(testName)).collect(Collectors.toList());
        allPathCov.add(pathCovRes.getFixedReports().getTestResult(testName));
        return allPathCov.stream().distinct().count();
    }
    public double calPathDiffWithFixed(String testName, Results<ResultReport> results, Results<PathCovReport> pathCovRes, int thr) {
        JSONArray fixedPath = pathCovRes.getFixedReports().getTestResult(testName);
        List<JSONArray> plausiblePaths = pathCovRes.getPlausibleReports().stream().map(rep -> rep.getTestResult(testName)).collect(Collectors.toList());
        if (getNoOfUniquePaths(testName, pathCovRes) == 1 ) // same -> no deviation
            return 0;
        double avgDev = plausiblePaths.stream().mapToDouble(path -> {
            int localThr = thr;
            int deviationPoint = Math.min(path.size(), fixedPath.size()) + 1;
            for (int i = 0; i < Math.min(path.size(), fixedPath.size()); i++) {
                if (path.get(i) == fixedPath.get(i))
                    continue;
                if (localThr <= 0) {
                    deviationPoint = i + 1; // first position as 1
                    break;
                } else localThr--;

            }
            return ((double) deviationPoint)/ ((double)path.size()); // % where deviation starts in the path 
        }).average().getAsDouble();

        /*
        Options:
            1. plausiblePaths.size() / avgDev;
            2. avg paths length / avg dev
         */
//        return ((double) plausiblePaths.size()) / avgDev; // the earlier deviation occurs (smaller index) ->  better?
        return ((double) 1)/avgDev;
    }

    /*
        two: 1. avg diff with fixed in terms on statement set (larger == better)
            2. no. of unique set
     */
    public double calUniqueStmtSet(String testName, Results<ResultReport> results, Results<StmtSetCovReport> stmtCovRes) {
        // first option: consider only set, not values
        List<Set> allStmtSet = stmtCovRes.getPlausibleReports().stream().map(rep -> rep.getTestResult(testName).keySet()).collect(Collectors.toList());
        allStmtSet.add(stmtCovRes.getFixedReports().getTestResult(testName).keySet());
        return allStmtSet.stream().distinct().count();
//                / (double) allStmtSet.size();
        // more unique set of statements -> spawn different statements (may be good for FL) -> better
    }

    public double calSetDiffWithFixed(String testName, Results<ResultReport> results, Results<StmtSetCovReport> stmtCovRes) {
        if (getMaxStmtSetSize(testName, stmtCovRes) == 0)
            return 0;
        Set fixedStmtSet = stmtCovRes.getFixedReports().getTestResult(testName).keySet();
        List<Set> plauStmtSet = stmtCovRes.getPlausibleReports().stream().map(rep -> rep.getTestResult(testName).keySet()).collect(Collectors.toList());
        double avgDiff = plauStmtSet.stream().mapToDouble(plau -> {
            Set symmetricDiff = new HashSet(plau);
            symmetricDiff.addAll(fixedStmtSet);
            Set tmp = new HashSet(plau);
            tmp.retainAll(fixedStmtSet);  //get common ones
            symmetricDiff.removeAll(tmp);
            return ((double)symmetricDiff.size()/((double) plau.size())); // % of diff. to no. of stmt
        }).average().getAsDouble();

        return avgDiff ;
        // options: / no. of plausible elements
        // options: / average no. of statements in paths
        // the avg no. of different elements in plausible fix sets and fixed set -> FL better?
    }
    public int getMaxPathLength(String testName, Results<PathCovReport> pathCovRes) {
        return Math.max(pathCovRes.getPlausibleReports().stream().mapToInt(rep -> rep.getTestResult(testName).size()).max().getAsInt(), pathCovRes.getFixedReports().getTestResult(testName).size());
    }
    public int getMaxStmtSetSize(String testname, Results<StmtSetCovReport> stmtCovRes) {
        return Math.max(stmtCovRes.getFixedReports().getTestResult(testname).keySet().size(), stmtCovRes.getPlausibleReports().stream().mapToInt(rep -> rep.getTestResult(testname).size()).max().getAsInt());
    }
}
