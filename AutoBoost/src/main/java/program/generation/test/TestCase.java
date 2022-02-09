package program.generation.test;

import helper.Properties;
import program.execution.stmt.Stmt;
import program.execution.stmt.VarStmt;
import program.execution.variable.VarDetail;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public abstract class TestCase {
    private static final AtomicInteger testIDGenerator = new AtomicInteger(0);
    private final AtomicInteger varIDGenerator = new AtomicInteger(0);
    private final int ID;
    private final Map<Integer, List<VarStmt>> detailToStmtMap = new HashMap<>();
    private final List<Stmt> stmtList = new ArrayList<>();
    private final List<VarStmt> varAvailable = new ArrayList<VarStmt>();
    private final Set<Class<?>> allImports = new HashSet<>();

    public TestCase() {
        this.ID = testIDGenerator.incrementAndGet();
        if (Properties.getSingleton().getJunitVer() != 3)
            this.allImports.add(org.junit.Test.class);
    }


    public AtomicInteger getVarIDGenerator() {
        return varIDGenerator;
    }

    public int getID() {
        return ID;
    }

    public Map<Integer, List<VarStmt>> getDetailToStmtMap() {
        return detailToStmtMap;
    }

    public List<Stmt> getStmtList() {
        return stmtList;
    }


    public void addStmt(Stmt stmt) {
        this.stmtList.add(stmt);
        this.addImports(stmt.getImports());
    }

    public List<VarStmt> getVarAvailable() {
        return varAvailable;
    }

    public Set<Class<?>> getAllImports() {
        return allImports;
    }


    public void addImports(Set<Class<?>> imports) {
        this.allImports.addAll(imports);
        this.allImports.remove(null);
    }

    public void addImports(Class<?> imports) {
        this.allImports.add(imports);
        this.allImports.remove(null);
    }

    public void addOrUpdateVar(Integer varDetailID, VarStmt stmt) {
        if (!this.varAvailable.contains(stmt)) {
            this.varAvailable.add(stmt);
            this.addImports(stmt.getImports());
        }
        if (!this.detailToStmtMap.containsKey(varDetailID))
            this.detailToStmtMap.put(varDetailID, new ArrayList<>());
        this.detailToStmtMap.get(varDetailID).add(stmt);
    }

    public void removeVar(Integer varDetailsID, VarStmt stmt) {
        if (this.detailToStmtMap.containsKey(varDetailsID))
            this.detailToStmtMap.get(varDetailsID).remove(stmt);
    }

    public List<VarStmt> getExistingVar(VarDetail detail) {
        return getExistingVar(detail.getID());
    }

    public List<VarStmt> getExistingVar(Integer varDetailID) {
        return this.detailToStmtMap.getOrDefault(varDetailID, null);
    }

    public int getNewVarID() {
        return varIDGenerator.incrementAndGet();
    }


    public abstract String output();

    protected String outputStmts(String indentation) {
        return indentation + this.getStmtList().stream().map(Stmt::getStmt).collect(Collectors.joining(";\n" + indentation)) + ";\n";
    }
}
