package com.shop.config;

/**
 * Central place for every tunable constant (weights, limits, file names).
 * Data structure: none, this class only holds constants.
 * Complexity: every access is O(1).
 */

public final class Config {

    private Config() {
    }

    // ---------- Storage ----------
    public static final String DATA_DIR = "data";
    public static final String USERS_FILE = "users.json";
    public static final String PRODUCTS_FILE = "products.json";
    public static final String ORDERS_FILE = "orders.json";
    /**
     * Copy of products.json packaged inside the jar (used if the file is
     * missing/corrupt).
     */
    public static final String SEED_PRODUCTS_RESOURCE = "/seed/products.json";

    // ---------- Default admin (seeded on first run) ----------
    public static final String DEFAULT_ADMIN_USERNAME = "admin";
    public static final String DEFAULT_ADMIN_PASSWORD = "admin123";

    // ---------- Validation rules ----------
    public static final int USERNAME_MIN_LENGTH = 4;
    public static final int USERNAME_MAX_LENGTH = 20;
    public static final String USERNAME_PATTERN = "[A-Za-z0-9_]+";
    public static final int PASSWORD_MIN_LENGTH = 6;
    public static final int PASSWORD_MAX_LENGTH = 64;
    public static final int PRODUCT_NAME_MAX_LENGTH = 60;
    public static final int MAX_CART_QTY_PER_ITEM = 10;

    // ---------- Similarity graph edge weights ----------
    // edge = (sameLabel ? SAME_LABEL_WEIGHT : 0) + (sameCategory ?
    // SAME_CATEGORY_WEIGHT : 0)
    // + coPurchaseCount * CO_PURCHASE_WEIGHT
    public static final double SAME_LABEL_WEIGHT = 3.0;
    public static final double SAME_CATEGORY_WEIGHT = 1.0;
    public static final double CO_PURCHASE_WEIGHT = 0.5;

    // ---------- About ----------
    public static final String APP_NAME = "ShopTipid";
    public static final String APP_VERSION = "1.0.0";
    public static final String COURSE_NAME = "Data Structures and Algorithms";
    public static final String OUTPUT_NAME = "Final Course Output";
    public static final String[] TEAM_MEMBERS = {
            "Arnoco, Kurt Justine B.",
            "Malatbalat, Dan Francis B.",
            "Masallo, Yuan Andrei R.",
            "Peralta, Josh Jefrey C.",
            "Salac, Paul Runzel G."
    };

    // ---------- Recommendation scoring ----------
    // score = W_SIMILARITY*similarity + W_POPULARITY*popularity + W_RATING*rating +
    // W_PRICE_FIT*priceFit
    // (every factor is normalized to 0..1 before weighting; defaults sum to 1.0)
    public static final double W_SIMILARITY = 0.40;
    public static final double W_POPULARITY = 0.25;
    public static final double W_RATING = 0.25;
    public static final double W_PRICE_FIT = 0.10;

    /** BFS explores neighbours up to this many hops from the source products. */
    public static final int BFS_MAX_DEPTH = 2;
    /**
     * Similarity found at depth 2 is multiplied by this factor (closer = more
     * relevant).
     */
    public static final double DEPTH_DECAY = 0.5;
    /**
     * priceFit value used when the user has no purchase history to compare against.
     */
    public static final double PRICE_FIT_NEUTRAL = 0.5;
    /**
     * Popularity is shown to the user on a 0..100 scale in the "why recommended"
     * line.
     */
    public static final int POPULARITY_DISPLAY_SCALE = 100;

    public static final int DEFAULT_TOP_N = 5;
    public static final int MAX_TOP_N = 20;

    // ---------- Bundle Builder ----------
    public static final int BUNDLE_MAX_ITEMS = 4;
    // item score used by the backtracking search (sums to 1.0)
    public static final double BUNDLE_W_RATING = 0.50;
    public static final double BUNDLE_W_POPULARITY = 0.30;
    public static final double BUNDLE_W_SIMILARITY = 0.20;

    // ---------- UI ----------
    public static final String CURRENCY_CODE = "PHP";
    public static final int BANNER_WIDTH = 64;
    public static final String NO_COLOR_ENV = "NO_COLOR";
    public static final String NO_COLOR_FLAG = "--no-color";
}