package program.execution.variable;

import program.execution.ExecutionTrace;

public class EntryVarDetails extends VarDetail{
    Class<?> type;
    int keyID;
    int valueID;
    Object value;

    public EntryVarDetails(int ID, Class<?> type, int keyID, int valueID, Object value) {
        super(ID);
        this.type = type;
        this.keyID = keyID;
        this.valueID = valueID;
        this.value = value;
    }

    @Override
    public Class<?> getType() {
        return type;
    }

    @Override
    public String getTypeSimpleName() {
        return this.type.getSimpleName();
    }

    public void setType(Class<?> type) {
        this.type = type;
    }

    public int getKeyID() {
        return keyID;
    }

    public void setKeyID(int keyID) {
        this.keyID = keyID;
    }

    public int getValueID() {
        return valueID;
    }

    public void setValueID(int valueID) {
        this.valueID = valueID;
    }

    @Override
    public Object getValue() {
        return keyID+ "=" + valueID;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return "EntryVarDetails{" +
                "ID=" + getID() +
                ", type=" + (type==null?"null":type.getSimpleName())+
                ", keyID=" + keyID +
                ", valueID=" + valueID +
                ", value=" + value +
                '}';
    }

    @Override
    public String toDetailedString() {
        return "EntryVarDetails{" +
                "ID=" + getID() +
                ", type=" + (type==null?"null":type.getSimpleName())+
                ", key=" + (keyID==-1?"null": ExecutionTrace.getSingleton().getVarDetailByID(keyID).toDetailedString()) +
                ", value=" + (valueID==-1?"null": ExecutionTrace.getSingleton().getVarDetailByID(valueID).toDetailedString()) +
                ", value=" + value +
                '}';
    }
}
