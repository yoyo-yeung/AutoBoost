package helper.analyzer;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class PathCovReport extends Report<JSONArray>{
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

}
