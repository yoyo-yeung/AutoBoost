package helper.analyzer;

import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;

import java.io.File;
import java.io.IOException;

public class StmtSetCovReport extends Report<JSONObject> {
    public StmtSetCovReport() {
    }

    public StmtSetCovReport(JSONObject testResults) {
        super(testResults);
    }

    public StmtSetCovReport(File file) throws IOException, ParseException {
        super(file);
    }

    @Override
    public JSONObject getTestResult(String key) {
        return (JSONObject) testResults.getOrDefault(key, new JSONObject());
    }

    @Override
    public String toString() {
        return "StmtSetCovReport{" +
                "testResults=" + testResults.toJSONString() +
                '}';
    }
}
