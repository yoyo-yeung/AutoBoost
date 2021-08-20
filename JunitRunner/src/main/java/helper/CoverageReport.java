package helper;

import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class CoverageReport {
    private static final Logger logger = LoggerFactory.getLogger(CoverageReport.class);
    private static final String PATH_REPORT_SUFFIX = "_path_coverage.json";
    private static final String SET_REPORT_SUFFIX = "_set_coverage.json";
    private JSONObject pathCoverage = new JSONObject();
    private JSONObject setCoverage = new JSONObject();

    public CoverageReport() {
    }

    public CoverageReport(JSONObject pathCoverage, JSONObject setCoverage) {
        this.pathCoverage = pathCoverage;
        this.setCoverage = setCoverage;
    }

    public JSONObject getPathCoverage() {
        return pathCoverage;
    }

    public void setPathCoverage(JSONObject pathCoverage) {
        this.pathCoverage = pathCoverage;
    }

    public JSONObject getSetCoverage() {
        return setCoverage;
    }

    public void setSetCoverage(JSONObject setCoverage) {
        this.setCoverage = setCoverage;
    }

    public void addPathCoverageInfo(String testMethod, ArrayList<Integer> travelledPath) {
        this.pathCoverage.put(testMethod, travelledPath);
    }

    public void addSetCoverageInfo(String testMethod, HashMap<Integer, Integer> travelledSet) {
        List coveredSet =  travelledSet.entrySet().stream().filter(map -> map.getValue()> 0).map(Map.Entry::getKey).collect(Collectors.toList());

        this.setCoverage.put(testMethod, coveredSet);
    }
    public void storeCoverages(String filePath, String filePrefix) throws IOException {
        this.storePathCoverage(filePath, filePrefix);
        this.storeSetCoverage(filePath, filePrefix);
    }
    private void storePathCoverage(String filePath, String filePrefix) throws IOException {
        File file = new File(filePath, filePrefix + PATH_REPORT_SUFFIX);
        logger.debug("Storing test path coverage to file " + file.getAbsolutePath());
        if (!file.exists())
            file.createNewFile();
        FileWriter writer = new FileWriter(file);
        writer.write(pathCoverage.toJSONString());
        writer.flush();
        writer.close();
    }

    private void storeSetCoverage(String filePath, String filePrefix) throws IOException {
        File file = new File(filePath, filePrefix + SET_REPORT_SUFFIX);
        logger.debug("Storing test set coverage to file " + file.getAbsolutePath());
        if (!file.exists())
            file.createNewFile();
        FileWriter writer = new FileWriter(file);
        writer.write(setCoverage.toJSONString());
        writer.flush();
        writer.close();
    }
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CoverageReport that = (CoverageReport) o;
        return Objects.equals(pathCoverage, that.pathCoverage) && Objects.equals(setCoverage, that.setCoverage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pathCoverage, setCoverage);
    }

    @Override
    public String toString() {
        return "CoverageReport{" +
                "pathCoverage=" + pathCoverage +
                ", setCoverage=" + setCoverage +
                '}';
    }

}
