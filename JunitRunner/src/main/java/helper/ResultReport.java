package helper;

//import com.sun.org.slf4j.internal.Logger;
//import com.sun.org.slf4j.internal.LoggerFactory;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;


public class ResultReport {
    private static final Logger logger = LoggerFactory.getLogger(ResultReport.class);
    private static final String REPORT_SUFFIX = "_results.json";
    JSONObject testResults = new JSONObject();

    public JSONObject getTestResults() {
        return testResults;
    }

    public void setTestResults(JSONObject testResults) {
        this.testResults = testResults;
    }

    public void addTestResult(String methodName, boolean PFresult) {
        testResults.put(methodName, PFresult);
    }

    public void storeResult(String filePath, String filePrefix) throws IOException {
        File file = new File(filePath, filePrefix + REPORT_SUFFIX);
        logger.debug("Storing test results to file "+ file.getAbsolutePath());
        if(!file.exists())
            file.createNewFile();

        FileWriter writer = new FileWriter(file);
        writer.write(testResults.toJSONString());
        writer.flush();
        writer.close();
    }
}
