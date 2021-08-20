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

public class ResultReport {
    JSONObject testResults = new JSONObject();

    public ResultReport() {
    }

    public ResultReport(JSONObject testResults) {
        this.testResults = testResults;
    }

    public ResultReport(File file) throws IOException, ParseException {
        JSONParser parser = new JSONParser();
        if(!file.exists())
            throw new IllegalArgumentException("File input "+file.getAbsolutePath()+"does not exist");
        testResults = (JSONObject) parser.parse(new FileReader(file));

    }

    public List<String> getKeys() {
        return (List<String>) testResults.keySet().stream().collect(Collectors.toList());
    }

    public JSONObject getTestResults() {
        return testResults;
    }

    public void setTestResults(JSONObject testResults) {
        this.testResults = testResults;
    }
    public boolean getTestResult(String test) {
        return (boolean) testResults.getOrDefault(test, false);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ResultReport that = (ResultReport) o;
        return Objects.equals(testResults, that.testResults);
    }

    @Override
    public int hashCode() {
        return Objects.hash(testResults);
    }

    @Override
    public String toString() {
        return "ResultReport{" +
                "testResults=" + testResults +
                '}';
    }
}
