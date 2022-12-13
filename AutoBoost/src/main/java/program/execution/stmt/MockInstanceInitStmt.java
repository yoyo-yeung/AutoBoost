package program.execution.stmt;

import helper.Helper;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.Set;

public class MockInstanceInitStmt extends Stmt {
    Class<?> mockType;

    public MockInstanceInitStmt(Class<?> mockType) {
        this.mockType = mockType;
    }

    @Override
    public String getStmt(Set<Class<?>> fullCNameNeeded) {
        return "Mockito.mock(" + Helper.getClassNameToOutput(fullCNameNeeded, mockType) + ".class)";
    }

    @Override
    public Set<Class<?>> getImports(String packageName) {
        return Collections.singleton(Mockito.class);
    }
}
