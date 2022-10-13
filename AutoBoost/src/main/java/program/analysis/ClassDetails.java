package program.analysis;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ClassDetails {
    private String classFullName;
    private List<Field> classFields;

    public ClassDetails(String classFullName) {
        this.classFullName = classFullName;
        this.classFields = new ArrayList<>();
    }

    public ClassDetails(String classFullName, List<Field> classFields) {
        this.classFullName = classFullName;
        this.classFields = classFields;
    }

    public String getClassFullName() {
        return classFullName;
    }

    public void setClassFullName(String classFullName) {
        this.classFullName = classFullName;
    }

    public List<Field> getClassFields() {
        return classFields;
    }

    public void setClassFields(List<Field> classFields) {
        this.classFields = classFields;
    }

    public void addClassFileds(List<Field> classFields) {
        this.classFields.addAll(classFields);
    }

    @Override
    public String toString() {
        return "ClassDetails{" +
                "classFullName='" + classFullName + '\'' +
                ", classFields=" + classFields.stream().map(f -> f.getName()).collect(Collectors.joining(",")) +
                '}';
    }
}
