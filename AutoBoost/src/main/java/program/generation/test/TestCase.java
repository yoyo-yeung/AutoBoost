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
    private final Map<Integer, VarStmt> varToVarStmtMap = new HashMap<>();
    private final Map<VarDetail, VarStmt> varToMockedVarStmtMap = new HashMap<>();
    private final List<Stmt> stmtList = new ArrayList<>();
    private final Set<Class<?>> allImports = new HashSet<>();
    private String packageName = null;

    public TestCase(String packageName) {
        this.ID = testIDGenerator.incrementAndGet();
        if (Properties.getSingleton().getJunitVer() != 3)
            this.allImports.add(org.junit.Test.class);
        this.packageName = packageName;
    }


    public AtomicInteger getVarIDGenerator() {
        return varIDGenerator;
    }

    public int getID() {
        return ID;
    }


    public List<Stmt> getStmtList() {
        return stmtList;
    }


    public void addStmt(Stmt stmt) {
        this.stmtList.add(stmt);
        this.addImports(stmt.getImports(packageName));
    }

    public Set<Class<?>> getAllImports() {
        return allImports;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
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

        this.varToVarStmtMap.put(varDetailID, stmt);
        this.addImports(stmt.getImports(packageName));
    }

    public void addOrUpdateMockedVar(VarDetail varDetail, VarStmt stmt) {
        this.addImports(stmt.getImports(packageName));
        this.varToMockedVarStmtMap.put(varDetail, stmt);
    }

    public void removeVar(Integer varDetailsID, VarStmt stmt) {
        if (this.varToVarStmtMap.containsKey(varDetailsID))
            this.varToVarStmtMap.remove(varDetailsID);
    }

    public VarStmt getExistingVar(VarDetail detail) {
        return getExistingVar(detail.getID());
    }

    public VarStmt getExistingMockedVar(VarDetail detail) {
        return this.varToMockedVarStmtMap.getOrDefault(detail, null);
    }
    public VarStmt getExistingVar(Integer varDetailID) {
        return this.varToVarStmtMap.getOrDefault(varDetailID, null);
    }

    public int getNewVarID() {
        return varIDGenerator.incrementAndGet();
    }

    public VarStmt getExistingCreatedOrMockedVar(VarDetail detail) {
        if(this.varToVarStmtMap.containsKey(detail.getID())) return this.varToVarStmtMap.get(detail.getID());
        else return this.varToMockedVarStmtMap.getOrDefault(detail, null);
    }
    public abstract String output(Set<Class<?>>fullCNameNeeded);

    protected String outputStmts(String indentation, Set<Class<?>>fullCNameNeeded) {
        if(this.getStmtList().size() > 0 )
            return indentation + this.getStmtList().stream().map(s ->  s.getStmt(fullCNameNeeded)).collect(Collectors.joining(";\n" + indentation)) + ";\n";
        return "";
    }
}
