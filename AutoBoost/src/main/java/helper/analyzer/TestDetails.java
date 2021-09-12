package helper.analyzer;


import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class TestDetails {
    String testName;
    Map<String, Double> scores = new HashMap<String, Double>();


    public TestDetails() {
    }

    public TestDetails(String testName) {
        this.testName = testName;
    }

    public String getTestName() {
        return testName;
    }

    public void setTestName(String testName) {
        this.testName = testName;
    }

    public Map<String, Double> getScores() {
        return scores;
    }

    public void setScores(Map<String, Double> scores) {
        this.scores = scores;
    }

    public double getScore(String key) {
        return this.scores.getOrDefault(key, 0.0);
    }
    
    public void addScore(String key, Double score) {
        this.scores.put(key, score);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TestDetails that = (TestDetails) o;
        return Objects.equals(testName, that.testName) && Objects.equals(scores, that.scores);
    }

    @Override
    public int hashCode() {
        return Objects.hash(testName, scores);
    }

    @Override
    public String toString() {
        return "TestDetails{" +
                "testName='" + testName + '\'' +
                ", scores=" + scores +
                '}';
    }
}
