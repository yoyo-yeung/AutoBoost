package program.execution.variable;

import java.util.List;

public abstract class VarDetail{
    protected int ID;

    public int getID() {
        return ID;
    }

    public void setID(int ID) {
        this.ID = ID;
    }

    public abstract Object getValue();

    public abstract Class<?> getType();

    public abstract String getTypeSimpleName();

}
