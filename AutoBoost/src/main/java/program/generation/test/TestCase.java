package program.generation.test;

import helper.Properties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import program.execution.stmt.Stmt;
import program.execution.stmt.VarStmt;
import program.execution.variable.VarDetail;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public abstract class TestCase {
    private final Logger logger = LogManager.getLogger(TestCase.class);
    private static final AtomicInteger testIDGenerator = new AtomicInteger(0);
    private final AtomicInteger varIDGenerator = new AtomicInteger(0);
    private final int ID;
    private final Map<Integer, VarStmt> varToVarStmtMap = new HashMap<>();
    private final Map<VarDetail, VarStmt> varToMockedVarStmtMap = new HashMap<>();
    private final List<Stmt> stmtList = new ArrayList<>();
    private final Set<Class<?>> allImports = new HashSet<>();
    private final Map<Integer, Object> varToObjMap = new HashMap<>();
    private String packageName = null;
    private boolean recreated = true;

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
        if (this.varToMockedVarStmtMap.containsKey(detail)) return this.varToMockedVarStmtMap.get(detail);
        else return this.varToVarStmtMap.getOrDefault(detail.getID(), null);
    }

    public Map<Integer, VarStmt> getVarToVarStmtMap() {
        return varToVarStmtMap;
    }

    public Map<VarDetail, VarStmt> getVarToMockedVarStmtMap() {
        return varToMockedVarStmtMap;
    }

    public abstract String output(Set<Class<?>> fullCNameNeeded);

    protected String outputStmts(String indentation, Set<Class<?>> fullCNameNeeded) {
        if (this.getStmtList().size() > 0)
            return indentation + this.getStmtList().stream().map(s -> s.getStmt(fullCNameNeeded)).collect(Collectors.joining(";\n" + indentation)) + ";\n";
        return "";
    }

    public Set<Class<?>> getMockedTypes() {
        return this.getVarToMockedVarStmtMap().values().stream().map(VarStmt::getVarType)
                .collect(Collectors.toSet());
    }

    public Object getObjForVar(int varID) {
        return this.varToObjMap.getOrDefault(varID, null);
    }

    public void addObjForVar(int varID, Object obj) {
        this.varToObjMap.put(varID, obj);
    }

    public boolean createdObjForVar(int varID) {
        return this.varToObjMap.containsKey(varID);
    }

    public void keepOnlyTargetCalleeVar(int varID) {
        this.varToObjMap.keySet().retainAll(Collections.singleton(varID));
        this.varToMockedVarStmtMap.clear();
    }

    public boolean isRecreated() {
        return recreated;
    }

    public void setRecreated(boolean recreated) {
        this.recreated = recreated;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TestCase testCase = (TestCase) o;
        return this.outputStmts("", new HashSet<>()).equals(testCase.outputStmts("", new HashSet<>()));
    }

    @Override
    public int hashCode() {
        return Objects.hash(varIDGenerator, ID, varToVarStmtMap, varToMockedVarStmtMap, stmtList, allImports, packageName);
    }
}
