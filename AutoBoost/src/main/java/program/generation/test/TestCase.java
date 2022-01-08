package program.generation.test;

import program.execution.ExecutionTrace;
import program.execution.stmt.AssertStmt;
import program.execution.stmt.Stmt;
import program.execution.stmt.VarStmt;
import program.execution.variable.VarDetail;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class TestCase {
    private static final AtomicInteger testIDGenerator = new AtomicInteger(0);
    private final AtomicInteger varIDGenerator = new AtomicInteger(0);
    private final int ID;
    private final List<Stmt> stmtList = new ArrayList<>();
    private final List<VarStmt> varAvailable = new ArrayList<VarStmt>();
    private final Map<VarDetail, List<VarStmt>> detailToStmtMap = new HashMap<>();
    private AssertStmt assertion;
    private Set<Class<?>> allImports = new HashSet<>();

    public TestCase() {
        this.ID = testIDGenerator.incrementAndGet();
    }


    public int getID() {
        return ID;
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

    public AssertStmt getAssertion() {
        return assertion;
    }

    public void setAssertion(AssertStmt assertion) {
        this.assertion = assertion;
    }

    public Set<Class<?>> getAllImports() {
        return allImports;
    }

    public void setAllImports(Set<Class<?>> allImports) {
        this.allImports = allImports;
    }

    public void addImports(Set<Class<?>> imports) { this.allImports.addAll(imports);}

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
    @Override
    public String toString() {
        return "DefaultTestCase{" +
                "ID=" + ID +
                ", stmtList=" + stmtList +
                ", varAvailable=" + varAvailable +
                ", detailToStmtMap=" + detailToStmtMap +
                ", assertion=" + assertion +
                ", allImports=" + allImports +
                '}';
    }
}
