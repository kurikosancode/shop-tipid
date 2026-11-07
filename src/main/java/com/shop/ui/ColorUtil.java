package com.shop.ui;

import com.shop.config.Config;

/**
 * All ANSI color codes live here, so colors can be switched off in one place.
 * Disable colors with the environment variable NO_COLOR (any non-empty value)
 * or the command-line flag --no-color.
 * Data structure: none (static helper methods). Every method is O(length of the text).
 *
 * Tip for tables: pad the PLAIN text to its column width first, then color it,
 * because the invisible ANSI codes would otherwise break the alignment.
 */
public final class ColorUtil {

    private static final String RESET = "\033[0m";
    private static final String BOLD = "\033[1m";
    private static final String DIM = "\033[2m";
    private static final String RED = "\033[31m";
    private static final String GREEN = "\033[32m";
    private static final String YELLOW = "\033[33m";
    private static final String MAGENTA = "\033[35m";
    private static final String CYAN = "\033[36m";

    private static boolean enabled = true;

    private ColorUtil() { }

    /** Call once at startup with the program arguments. */
    public static void init(String[] args) {
        String env = System.getenv(Config.NO_COLOR_ENV);
        boolean disable = env != null && !env.isEmpty();
        if (args != null) {
            for (String arg : args) {
                if (Config.NO_COLOR_FLAG.equalsIgnoreCase(arg)) {
                    disable = true;
                }
            }
        }
        enabled = !disable;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    private static String wrap(String codes, String text) {
        String safe = text == null ? "" : text;
        return enabled ? codes + safe + RESET : safe;
    }

    /** Cyan bold: banners and headers. */
    public static String header(String text) {
        return wrap(BOLD + CYAN, text);
    }

    /** Green: prices. */
    public static String price(String text) {
        return wrap(GREEN, text);
    }

    /** Green: success messages. */
    public static String success(String text) {
        return wrap(GREEN, text);
    }

    /** Red: errors. */
    public static String error(String text) {
        return wrap(RED, text);
    }

    /** Yellow: menu numbers like [1]. */
    public static String menuNumber(String text) {
        return wrap(YELLOW, text);
    }

    /** Yellow: warnings. */
    public static String warning(String text) {
        return wrap(YELLOW, text);
    }

    /** Magenta + dim: "why recommended" lines. */
    public static String why(String text) {
        return wrap(DIM + MAGENTA, text);
    }

    public static String dim(String text) {
        return wrap(DIM, text);
    }

    public static String bold(String text) {
        return wrap(BOLD, text);
    }
}