package com.teamproteinpowder.lostfound.domain;

/**
 * Category tags, used for browsing and as one signal in match scoring.
 *
 * Kept as an enum rather than free text so filtering stays exact and two
 * people describing the same kind of object land in the same bucket.
 */
public enum Category {
    ELECTRONICS("Electronics"),
    ID_CARDS("ID cards"),
    KEYS("Keys"),
    BAGS("Bags"),
    CLOTHING("Clothing"),
    BOOKS("Books & stationery"),
    JEWELLERY("Jewellery"),
    OTHER("Other");

    private final String label;

    Category(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
