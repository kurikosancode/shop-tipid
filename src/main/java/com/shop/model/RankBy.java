package com.shop.model;

public enum RankBy {
    POPULARITY("Popularity"),
    RATING("Rating"),
    PRICE_LOW_TO_HIGH("Price: low to high"),
    PRICE_HIGH_TO_LOW("Price: high to low"),
    BEST_MATCH("Best match");

    private final String label;

    RankBy(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}