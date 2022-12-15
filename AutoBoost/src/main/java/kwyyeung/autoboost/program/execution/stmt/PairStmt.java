package kwyyeung.autoboost.program.execution.stmt;

import kwyyeung.autoboost.helper.Properties;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PairStmt extends Stmt {
    private final Stmt keyStmt;
    private final Stmt valueStmt;

    public PairStmt(Stmt keyStmt, Stmt valueStmt) {
        this.keyStmt = keyStmt;
        this.valueStmt = valueStmt;
    }

    @Override
    public String getStmt(Set<Class<?>> fullCNameNeeded) {
        return keyStmt.getStmt(fullCNameNeeded) + Properties.getDELIMITER() + valueStmt.getStmt(fullCNameNeeded);
    }

    @Override
    public Set<Class<?>> getImports(String packageName) {
        return Stream.of(keyStmt.getImports(packageName), valueStmt.getImports(packageName)).flatMap(Collection::stream).collect(Collectors.toSet());
    }
}
