package edu.wgu.pantryprep.domain;

public enum Unit {
    GRAM("g", Dimension.WEIGHT),
    KILOGRAM("kg", Dimension.WEIGHT),
    OUNCE("oz", Dimension.WEIGHT),
    POUND("lb", Dimension.WEIGHT),
    MILLILITER("ml", Dimension.VOLUME),
    LITER("L", Dimension.VOLUME),
    TEASPOON("tsp", Dimension.VOLUME),
    TABLESPOON("tbsp", Dimension.VOLUME),
    CUP("cup", Dimension.VOLUME),
    PIECE("pc", Dimension.COUNT);

    private final String abbreviation;
    private final Dimension dimension;

    Unit(String abbreviation, Dimension dimension) {
        this.abbreviation = abbreviation;
        this.dimension = dimension;
    }

    public String getAbbreviation() {
        return abbreviation;
    }

    public Dimension getDimension() {
        return dimension;
    }

    public Unit canonical() {
        return switch (dimension) {
            case WEIGHT -> GRAM;
            case VOLUME -> MILLILITER;
            case COUNT -> PIECE;
        };
    }

    public boolean sameDimensionAs(Unit other) {
        return this.dimension == other.dimension;
    }
}