package program.execution.variable;

import java.util.List;

public abstract class VarDetailImpl implements VarDetail{
    List<String> usedMethodIds = null;

    @Override
    public List<String> getUsedMethodIds() {
        return usedMethodIds;
    }

    public void setUsedMethodIds(List<String> usedMethodIds) {
        this.usedMethodIds = usedMethodIds;
    }
}
