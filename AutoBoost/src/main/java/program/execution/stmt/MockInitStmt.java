package program.execution.stmt;

import org.mockito.Mockito;

import java.util.Collections;
import java.util.Set;

public class MockInitStmt extends Stmt{
    Class<?> mockType;

    public MockInitStmt(Class<?> mockType) {
        this.mockType = mockType;
    }

    @Override
    public String getStmt(Set<Class<?>> fullCNameNeeded) {
        return "Mockito.mock("+ (fullCNameNeeded.contains(mockType)? mockType.getName().replace("$", ".") : mockType.getSimpleName()) + ".class)";
    }

    @Override
    public Set<Class<?>> getImports(String packageName) {
        return Collections.singleton(Mockito.class);
    }
}
