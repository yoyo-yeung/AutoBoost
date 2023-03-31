package instrumentation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class Counter {
    private static HashMap<String, Integer> stmtIdMap = new HashMap<>();
    private static int stmtCount=0;
    private static HashMap<Integer, Integer> occuranceCounter = new HashMap<Integer, Integer>();

    public Counter() {
    }

    public static void reset() {
        occuranceCounter = new HashMap<>();
    }

    public static HashMap<Integer, Integer> getOccuranceCounter() {
        return occuranceCounter;
    }

    public static void setOccuranceCounter(HashMap<Integer, Integer> occuranceCounter) {
        occuranceCounter = occuranceCounter;
    }

    public static void addOccurance(int stmtId) {
        occuranceCounter.put(stmtId, occuranceCounter.getOrDefault(stmtId, 0) + 1);
    }
    public synchronized static int getStmtId(String stmt) {
        if(!stmtIdMap.containsKey(stmt))
            stmtIdMap.put(stmt, ++stmtCount);
        return stmtIdMap.get(stmt);
    }

    public static HashMap<String, Integer> getStmtIdMap() {
        return stmtIdMap;
    }

    public static void setStmtIdMap(HashMap<String, Integer> stmtIdMap) {
        stmtIdMap = stmtIdMap;
    }

    public static int getStmtCount() {
        return stmtCount;
    }

    public static void setStmtCount(int stmtCount) {
        stmtCount = stmtCount;
    }
}
