package helper.analyzer;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public abstract class Report<T> {
    JSONObject testResults = new JSONObject();

    public Report() {
    }

    public Report(JSONObject testResults) {
        this.testResults = testResults;
    }

    public Report(File file) throws IOException, ParseException {
        JSONParser parser = new JSONParser();
        if (!file.exists())
            throw new IllegalArgumentException("File input " + file.getAbsolutePath() + "does not exist");
        testResults = (JSONObject) parser.parse(new FileReader(file));
    }

    public abstract T getTestResult(String key);

    public JSONObject getTestResults() {
        return testResults;
    }

    public void setTestResults(JSONObject testResults) {
        this.testResults = testResults;
    }

    public List<String> getKeys() {
        return (List<String>) testResults.keySet().stream().collect(Collectors.toList());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Report report = (Report) o;
        return Objects.equals(testResults, report.testResults);
    }

    @Override
    public int hashCode() {
        return Objects.hash(testResults);
    }

    @Override
    public String toString() {
        return "Report{" +
                "testResults=" + testResults.toJSONString() +
                '}';
    }
}
