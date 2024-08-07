package kwyyeung.autoboost.program.execution.variable;

import kwyyeung.autoboost.entity.CREATION_TYPE;

public class EnumVarDetails extends VarDetail {
    private static final CREATION_TYPE createdBy = CREATION_TYPE.DIRECT_ASSIGN;
    private final Class<?> type;
    private final String value;

    public EnumVarDetails(int ID, Class<?> type, String value) {
        super(ID);
        this.type = type;
        this.value = value;
    }

    @Override
    public Object getGenValue() {
        return type.equals(Class.class) ? value.replace("$", ".") + ".class" : type.getName().replace("$", ".") + "." + value;
    }

    public String getValue() {
        return value;
    }

    @Override
    public Class<?> getType() {
        return type;
    }

    @Override
    public boolean sameTypeNValue(Class<?> type, Object v) {
        return this.type.equals(type) && this.value.equals(v);
    }

    @Override
    public boolean sameValue(Object v) {
        return this.value.equals(v);
    }

    @Override
    public String getTypeSimpleName() {
        return type.getSimpleName();
    }

    @Override
    public CREATION_TYPE getCreatedBy() {
        return createdBy;
    }

    @Override
    public String toString() {
        return "EnumVarDetails{" +
                "ID=" + getID() +
                ", type=" + (type == null ? "null" : type.getSimpleName()) +
                ", value=" + (value == null ? "null" : value) +
                '}';
    }

    @Override
    public String toDetailedString() {
        return toString();
    }
}
