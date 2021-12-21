package program.execution.variable;

import java.util.List;

public abstract class VarDetail{
    private int ID;

    public int getID() {
        return ID;
    }

    protected void setID(int ID) {
        this.ID = ID;
    }

    public abstract Object getValue() throws Exception;

    public abstract Class<?> getType();

    public abstract String getTypeSimpleName();

}
