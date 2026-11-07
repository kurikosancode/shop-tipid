package com.shop.ui;

import com.shop.model.Order;
import com.shop.model.User;

/**
 * The recommendation-related screens CustomerMenu opens. Phase 4 provides the real
 * implementation (RecommendationMenu); this interface lets CustomerMenu compile on its own.
 * Data structure: none (interface).
 */
public interface RecommendationScreens {

    /** "Recommended for you" screen. */
    void showRecommended(User user);

    /** Suggestions shown right after a purchase (cart checkout or Buy now). */
    void showAfterPurchase(User user, Order order);

    /** Choose ranking parameter, weights and optional price limit. */
    void openSettings(User user);

    /** Bundle Builder (backtracking). */
    void openBundleBuilder(User user);
}