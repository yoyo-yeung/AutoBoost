package kwyyeung.autoboost.entity;

public enum ACCESS {
    PUBLIC(1),
    PRIVATE(3),
    PROTECTED(2);
    private final int perferenceLv;

    ACCESS(int perferenceLv) {
        this.perferenceLv = perferenceLv;
    }

    public int getPerferenceLv() {
        return perferenceLv;
    }
}
