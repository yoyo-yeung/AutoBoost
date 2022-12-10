package program.execution.variable;

import entity.CREATION_TYPE;
import org.mockito.MockingDetails;

public class MockVarDetails extends VarDetail {
    private static final CREATION_TYPE createdBy = CREATION_TYPE.CONSTRUCTOR;
    Class<?> type;
    MockingDetails value;

    public MockVarDetails(int ID, Class<?> type, MockingDetails value) {
        super(ID);
        this.type = type;
        this.value = value;
    }

    @Override
    public Object getGenValue() {
        return value;
    }

    @Override
    public Class<?> getType() {
        return type;
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
        return "MockVarDetails{" +
                "type=" + type +
                ", value=" + value +
                '}';
    }
}
