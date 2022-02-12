package program.execution.stmt;

import org.apache.commons.lang3.ClassUtils;

public class VarStmt extends Stmt{
    String varName;
    String varType;
    Class<?> actualType;

    public VarStmt(Class<?> actualType, int varID, int resultVarDetailID) {
        this.varName = "var"+ varID;
        this.varType = actualType.getSimpleName();
        this.actualType = actualType;
        this.resultVarDetailID = resultVarDetailID;
        if(!ClassUtils.isPrimitiveOrWrapper(actualType) && !actualType.isArray()) {
            if(!actualType.getName().contains("$"))
                this.imports.add(actualType);
        }
        if(actualType.isArray()) {
            Class<?> importType = actualType;
            while(importType.isArray()) {
                importType = importType.getComponentType();
            }
            if(!ClassUtils.isPrimitiveOrWrapper(importType))
                this.imports.add(importType);
        }
    }

    public VarStmt(String varType, Class<?> actualType, int varID, int resultVarDetailID) {
        this.varName = "var"+ varID;
        this.actualType = actualType;
        this.varType = varType;
        this.resultVarDetailID = resultVarDetailID;
        if(!ClassUtils.isPrimitiveOrWrapper(actualType) && !actualType.isArray()) {
            if(!actualType.getName().contains("$"))
                this.imports.add(actualType);
            try {
                this.imports.add(Class.forName(varType.substring(varType.lastIndexOf(" ")+1)));
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public String getStmt() {
        return varName;
    }

    public String getDeclarationStmt() {
        return "final "+ varType.substring(varType.lastIndexOf(".")+1).replaceAll("\\$", ".") + " " + varName;
    }

}
