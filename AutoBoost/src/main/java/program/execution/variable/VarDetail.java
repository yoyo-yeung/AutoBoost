package program.execution.variable;

import entity.CREATION_TYPE;

public abstract class VarDetail{
    private final int ID;

    public VarDetail(int ID) {
        this.ID = ID;
    }

    public int getID() {
        return ID;
    }

    public abstract Object getGenValue();

    public abstract Class<?> getType();

    public abstract String getTypeSimpleName();

    @Override
    public String toString() {
        return "VarDetail{" +
                "ID=" + ID +
                '}';
    }

    public String toDetailedString() {
        return this.toString();
    }
    public abstract CREATION_TYPE getCreatedBy();
}
