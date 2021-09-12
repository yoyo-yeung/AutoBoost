package helper.analyzer;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;

import java.io.File;
import java.io.IOException;

public class PathCovReport extends Report<JSONArray> {
    public PathCovReport() {
    }

    public PathCovReport(JSONObject testResults) {
        super(testResults);
    }

    public PathCovReport(File file) throws IOException, ParseException {
        super(file);
    }

    public JSONArray getTestResult(String test) {
        return (JSONArray) testResults.getOrDefault(test, new JSONArray());
    }

    @Override
    public String toString() {
        return "PathCovReport{" +
                "testResults=" + testResults.toJSONString() +
                '}';
    }
}
