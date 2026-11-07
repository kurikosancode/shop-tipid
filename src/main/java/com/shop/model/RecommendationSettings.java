package com.shop.model;

import com.shop.config.Config;

/**
 * Per-user recommendation preferences, saved inside users.json.
 * Data structure: plain fields (no collections). All operations are O(1).
 * minPrice / maxPrice are null when the user has not set a price limit.
 */
public class RecommendationSettings {

    private RankBy rankBy = RankBy.BEST_MATCH;
    private double wSimilarity = Config.W_SIMILARITY;
    private double wPopularity = Config.W_POPULARITY;
    private double wRating = Config.W_RATING;
    private double wPriceFit = Config.W_PRICE_FIT;
    private Double minPrice = null;
    private Double maxPrice = null;

    public RecommendationSettings() { }

    public RankBy getRankBy() {
        return rankBy == null ? RankBy.BEST_MATCH : rankBy;
    }

    public void setRankBy(RankBy rankBy) {
        this.rankBy = rankBy;
    }

    public double getWSimilarity() { return wSimilarity; }
    public double getWPopularity() { return wPopularity; }
    public double getWRating() { return wRating; }
    public double getWPriceFit() { return wPriceFit; }

    public void setWSimilarity(double w) { this.wSimilarity = w; }
    public void setWPopularity(double w) { this.wPopularity = w; }
    public void setWRating(double w) { this.wRating = w; }
    public void setWPriceFit(double w) { this.wPriceFit = w; }

    public void resetWeights() {
        wSimilarity = Config.W_SIMILARITY;
        wPopularity = Config.W_POPULARITY;
        wRating = Config.W_RATING;
        wPriceFit = Config.W_PRICE_FIT;
    }

    public Double getMinPrice() { return minPrice; }
    public Double getMaxPrice() { return maxPrice; }
    public void setMinPrice(Double minPrice) { this.minPrice = minPrice; }
    public void setMaxPrice(Double maxPrice) { this.maxPrice = maxPrice; }

    public void clearPriceLimits() {
        minPrice = null;
        maxPrice = null;
    }

    public boolean hasPriceLimit() {
        return minPrice != null || maxPrice != null;
    }

    /** True if the price passes the user's optional min/max limit. */
    public boolean isPriceAllowed(double price) {
        if (minPrice != null && price < minPrice) {
            return false;
        }
        return maxPrice == null || price <= maxPrice;
    }
}