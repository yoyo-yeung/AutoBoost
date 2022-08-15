package entity;

public enum METHOD_TYPE {
    STATIC(1),
    MEMBER(2),
    CONSTRUCTOR(1),
    STATIC_INITIALIZER(1000);

    private final int rank;

    METHOD_TYPE(int rank) {
        this.rank = rank;
    }

    public int getRank() {
        return rank;
    }
}
