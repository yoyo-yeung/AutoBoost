package instrumentation;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class TestInfo {
    private static Logger logger = LogManager.getLogger(TestInfo.class);
    private HashMap<Integer, Integer> stmtOccurCounter = null ;
    private HashMap<String, List<List<Map.Entry<String, String>>>> methodParamValueMap = null;
    private List<String> methodOrder = null;
    private long totalStmtRan = 0;
    private long distinctStmtRan = 0;

    public TestInfo() {
    }

    public TestInfo(HashMap<Integer, Integer> stmtOccurCounter, HashMap<String, List<List<Map.Entry<String, String>>>> methodParamValueMap, List<String> methodOrder) {
        this.stmtOccurCounter = stmtOccurCounter;
        this.methodParamValueMap = methodParamValueMap;
        this.methodOrder = methodOrder;
        this.calStmtRan();
    }
    private void calStmtRan() {
        if(this.stmtOccurCounter==null)
            return;
        this.distinctStmtRan = this.stmtOccurCounter.keySet().size();
        this.totalStmtRan =  this.stmtOccurCounter.values().stream().mapToLong(v -> v).sum();
//        this.totalStmtRan = this.stmtOccurCounter.values().stream().reduce(0, Integer::sum);
    }
    public HashMap<Integer, Integer> getStmtOccurCounter() {
        return stmtOccurCounter;
    }

    public void setStmtOccurCounter(HashMap<Integer, Integer> stmtOccurCounter) {
        this.stmtOccurCounter = stmtOccurCounter;
        this.calStmtRan();
    }

    public HashMap<String, List<List<Map.Entry<String, String>>>> getMethodParamValueMap() {
        return methodParamValueMap;
    }

    public void setMethodParamValueMap(HashMap<String, List<List<Map.Entry<String, String>>>> methodParamValueMap) {
        this.methodParamValueMap = methodParamValueMap;
    }

    public List<String> getMethodOrder() {
        return methodOrder;
    }

    public void setMethodOrder(List<String> methodOrder) {
        this.methodOrder = methodOrder;
    }

    public long getTotalStmtRan() {
        return totalStmtRan;
    }

    public double getDistinctStmtRan() {
        return distinctStmtRan;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TestInfo testInfo = (TestInfo) o;
        return totalStmtRan == testInfo.totalStmtRan && distinctStmtRan == testInfo.distinctStmtRan && Objects.equals(stmtOccurCounter, testInfo.stmtOccurCounter) && Objects.equals(methodParamValueMap, testInfo.methodParamValueMap) && Objects.equals(methodOrder, testInfo.methodOrder);
    }

    @Override
    public int hashCode() {
        return Objects.hash(stmtOccurCounter, methodParamValueMap, methodOrder, totalStmtRan, distinctStmtRan);
    }

    @Override
    public String toString() {
        return "TestInfo{" +
                "stmtOccurCounter=" + stmtOccurCounter +
                ", methodParamValueMap=" + methodParamValueMap +
                ", methodOrder=" + methodOrder +
                ", totalStmtRan=" + totalStmtRan +
                ", distinctStmtRan=" + distinctStmtRan +
                '}';
    }
}
