package program.generation.test;

import helper.Properties;
import program.execution.ExecutionTrace;
import program.execution.stmt.AssertStmt;
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
    private List<Stmt> stmtList = new ArrayList<>();
    private List<VarStmt> varAvailable = new ArrayList<VarStmt>();
    private final Map<VarDetail, List<VarStmt>> detailToStmtMap = new HashMap<>();
    private Set<Class<?>> allImports = new HashSet<>();

    public TestCase() {
        this.ID = testIDGenerator.incrementAndGet();
        if(Properties.getSingleton().getJunitVer()!=3)
            this.allImports.add(org.junit.Test.class);
    }


    public AtomicInteger getVarIDGenerator() {
        return varIDGenerator;
    }

    public int getID() {
        return ID;
    }

    public Map<VarDetail, List<VarStmt>> getDetailToStmtMap() {
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


    public void addImports(Set<Class<?>> imports) { this.allImports.addAll(imports); this.allImports.remove(null);}

    public void addImports(Class<?> imports) { this.allImports.add(imports); this.allImports.remove(null);}

    public void addOrUpdateVar(VarStmt stmt , VarDetail varDetail) {
        if(!this.varAvailable.contains(stmt)) {
            this.varAvailable.add(stmt);
            this.addImports(stmt.getImports());
        }
        if(!this.detailToStmtMap.containsKey(varDetail))
            this.detailToStmtMap.put(varDetail, new ArrayList<>());
        this.detailToStmtMap.get(varDetail).add(stmt);
    }

    public List<VarStmt> getExistingVar(VarDetail detail) {
        return this.detailToStmtMap.getOrDefault(detail, null);
    }

    public List<VarStmt> getExistingVar(int varDetailID) {
        return getExistingVar(ExecutionTrace.getSingleton().getVarDetailByID(varDetailID));
    }

    public int getNewVarID() {
        return varIDGenerator.incrementAndGet();
    }


    public abstract String output();

    protected String outputStmts(String indentation) {
        return indentation + this.getStmtList().stream().map(Stmt::getStmt).collect(Collectors.joining(";\n"+indentation)) + ";\n";
    }
}
