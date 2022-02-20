package program.execution.stmt;

import helper.Properties;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PairStmt extends Stmt{
    private final Stmt keyStmt;
    private final Stmt valueStmt;

    public PairStmt(Stmt keyStmt, Stmt valueStmt) {
        this.keyStmt = keyStmt;
        this.valueStmt = valueStmt;
    }

    @Override
    public String getStmt() {
        return keyStmt.getStmt() + Properties.getDELIMITER() + valueStmt.getStmt();
    }

    @Override
    public Set<Class<?>> getImports() {
        return Stream.of(keyStmt.getImports(), valueStmt.getImports()).flatMap(Collection::stream).collect(Collectors.toSet());
    }
}
