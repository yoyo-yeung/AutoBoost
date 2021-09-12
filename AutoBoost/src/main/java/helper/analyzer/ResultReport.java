package helper.analyzer;

import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;

import java.io.File;
import java.io.IOException;

public class ResultReport extends Report<Boolean> {
    public ResultReport() {
    }

    public ResultReport(JSONObject testResults) {
        super(testResults);
    }

    public ResultReport(File file) throws IOException, ParseException {
        super(file);
    }

    public Boolean getTestResult(String test) {
        return (boolean) testResults.getOrDefault(test, false);
    }

    @Override
    public String toString() {
        return "ResultReport{" +
                "testResults=" + testResults.toJSONString() +
                '}';
    }
}
