package program.execution.variable;

import java.util.List;

public abstract class VarDetail{
    private final int ID;

    public VarDetail(int ID) {
        this.ID = ID;
    }

    public int getID() {
        return ID;
    }

    public abstract Object getValue();

    public abstract Class<?> getType();

    public abstract String getTypeSimpleName();

}
