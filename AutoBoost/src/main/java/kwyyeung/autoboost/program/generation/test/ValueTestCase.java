package kwyyeung.autoboost.program.generation.test;

import kwyyeung.autoboost.helper.Properties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import kwyyeung.autoboost.program.execution.stmt.AssertStmt;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class ValueTestCase extends TestCase {
    private static final Logger logger = LogManager.getLogger(ValueTestCase.class);

    private AssertStmt assertion;

    public ValueTestCase() {
    }

    public AssertStmt getAssertion() {
        return assertion;
    }

    public void setAssertion(AssertStmt assertion) {
        this.assertion = assertion;
        this.addImports(assertion.getImports(this.getPackageName()));
    }

    public String output(Set<Class<?>> fullCNameNeeded) {
        String indentation = "\t";
        StringBuilder result = new StringBuilder();
        if (Properties.getSingleton().getJunitVer() != 3)
            result.append(indentation).append("@Test\n");
        result.append(indentation).append("public void test").append(this.getID()).append("() throws Exception {\n");
        result.append(outputStmts(indentation + indentation, fullCNameNeeded));
        result.append(indentation).append("}\n");
        return result.toString();
    }

    @Override
    protected String outputStmts(String indentation, Set<Class<?>> fullCNameNeeded) {
        return super.outputStmts(indentation, fullCNameNeeded) + indentation + this.getAssertion().getStmt(fullCNameNeeded) + ";\n";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ValueTestCase that = (ValueTestCase) o;
        return this.outputStmts("", new HashSet<>()).equals(that.outputStmts("", new HashSet<>()));
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), assertion);
    }
}
