package helper.analyzer;

import java.util.Objects;

public class TestDetails {
    String testName;
    double score = 0 ;

    public TestDetails(String testName, double score) {
        this.testName = testName;
        this.score = score;
    }

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

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TestDetails that = (TestDetails) o;
        return Double.compare(that.score, score) == 0 && Objects.equals(testName, that.testName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(testName, score);
    }

    @Override
    public String toString() {
        return "TestDetails{" +
                "testName='" + testName + '\'' +
                ", score=" + score +
                '}';
    }
}
