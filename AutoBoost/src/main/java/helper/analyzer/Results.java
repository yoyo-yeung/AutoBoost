package helper.analyzer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Results {
    List<ResultReport> plausibleReports = new ArrayList<ResultReport>();
    ResultReport fixedReports ;

    public Results() {
    }

    public Results(List<ResultReport> plausibleReports, ResultReport fixedReports) {
        this.plausibleReports = plausibleReports;
        this.fixedReports = fixedReports;
    }


    public List<ResultReport> getPlausibleReports() {
        return plausibleReports;
    }

    public void setPlausibleReports(List<ResultReport> plausibleReports) {
        this.plausibleReports = plausibleReports;
    }

    public ResultReport getFixedReports() {
        return fixedReports;
    }

    public void setFixedReports(ResultReport fixedReports) {
        this.fixedReports = fixedReports;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Results results = (Results) o;
        return Objects.equals(plausibleReports, results.plausibleReports) && Objects.equals(fixedReports, results.fixedReports);
    }

    @Override
    public int hashCode() {
        return Objects.hash(plausibleReports, fixedReports);
    }

    @Override
    public String toString() {
        return "Results{" +
                "plausibleReports=" + plausibleReports +
                ", fixedReports=" + fixedReports +
                '}';
    }
}
