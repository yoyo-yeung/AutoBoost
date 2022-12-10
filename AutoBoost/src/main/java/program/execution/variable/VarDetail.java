package program.execution.variable;

import entity.CREATION_TYPE;

import java.util.Objects;

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

    public Object getValue(){
        return null;
    }
    @Override
    public String toString() {
        return "VarDetail{" +
                "ID=" + ID +
                '}';
    }
    public boolean sameTypeNValue(Class<?> type, Object v) {
        return this.getType().equals(type) && Objects.equals(this.getGenValue(), v);
    }

    public boolean sameValue(Object v) {
        return Objects.equals(this.getGenValue(), v);
    }

    public String toDetailedString() {
        return this.toString();
    }
    public abstract CREATION_TYPE getCreatedBy();
}
