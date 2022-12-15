package kwyyeung.autoboost.program.execution.stmt;

import org.apache.commons.text.StringEscapeUtils;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FieldSetStmt extends Stmt {
    private final Stmt toSet;
    private final String fieldClass;
    private final String fieldName;
    private final Stmt value;

    public FieldSetStmt(Stmt toSet, String fieldClass, String fieldName, Stmt value) {
        this.toSet = toSet;
        this.fieldClass = fieldClass;
        this.fieldName = fieldName;
        this.value = value;
    }

    @Override
    public String getStmt(Set<Class<?>> fullCNameNeeded) {
        return String.format("setField(%s,\"%s\",\"%s\",%s)", toSet.getStmt(fullCNameNeeded), StringEscapeUtils.escapeJava(fieldClass), StringEscapeUtils.escapeJava(fieldName), value.getStmt(fullCNameNeeded));
    }

    @Override
    public Set<Class<?>> getImports(String packageName) {
        return Stream.of(toSet.getImports(packageName), value.getImports(packageName)).flatMap(Collection::stream).collect(Collectors.toSet());
    }
}
