package program.generation.test;

import helper.Properties;
import program.execution.stmt.AssertStmt;

import java.util.stream.Collectors;

public class ValueTestCase extends TestCase{

    private AssertStmt assertion;

    public ValueTestCase() {
        super();
    }

    public AssertStmt getAssertion() {
        return assertion;
    }

    public void setAssertion(AssertStmt assertion) {
        this.assertion = assertion;
        this.addImports(assertion.getImports());
    }

    public String output() {
        String indentation = "\t";
        StringBuilder result = new StringBuilder();
        if(Properties.getSingleton().getJunitVer()!=3)
            result.append(indentation).append("@Test\n");
        result.append(indentation).append("public void test").append(this.getID()).append("() throws Exception {\n");
        result.append(outputStmts(indentation+indentation));
        result.append(indentation).append("}\n");
        return result.toString();
    }

    @Override
    protected String outputStmts(String indentation) {
        return super.outputStmts(indentation) + indentation + this.getAssertion().getAssertStmts().stream().collect(Collectors.joining(";\n"+indentation)) + ";\n";
    }
}
