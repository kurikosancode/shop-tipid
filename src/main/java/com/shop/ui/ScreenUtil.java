package com.shop.ui;

import com.shop.config.Config;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Screen and layout helpers: clearing the screen, boxed banners, menu lines,
 * message lines, text padding for aligned tables, and the single formatPrice helper.
 * Data structure: none (static helpers). Every method is O(length of the text).
 *
 * Only plain ASCII characters are used for boxes so the layout looks right
 * in any Windows or Linux terminal, whatever its code page.
 */
public final class ScreenUtil {

    private static final boolean IS_WINDOWS =
            System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private ScreenUtil() { }

    // ------------------------------------------------------------ screen

    /**
     * Clears the terminal. Linux/Mac use the ANSI sequence. Windows consoles use
     * "cmd /c cls" through ProcessBuilder, and fall back to the ANSI sequence if that fails.
     */
    public static void clear() {
        System.out.flush();
        if (IS_WINDOWS) {
            try {
                new ProcessBuilder("cmd", "/c", "cls").inheritIO().start().waitFor();
                return;
            } catch (IOException e) {
                // fall through to the ANSI sequence
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        System.out.print("\033[H\033[2J");
        System.out.flush();
    }

    // ------------------------------------------------------------ layout

    /** Boxed banner with a centered title. */
    public static void banner(String title) {
        int inner = Config.BANNER_WIDTH - 2;
        String border = "+" + "=".repeat(inner) + "+";
        System.out.println(ColorUtil.header(border));
        System.out.println(ColorUtil.header("|" + center(title, inner) + "|"));
        System.out.println(ColorUtil.header(border));
    }

    /** Section title followed by a thin divider line. */
    public static void section(String title) {
        System.out.println(ColorUtil.header(title));
        divider();
    }

    public static void divider() {
        System.out.println(ColorUtil.dim("-".repeat(Config.BANNER_WIDTH)));
    }

    /** One menu line: "  [1] Text" with a yellow number. Same style on every screen. */
    public static void menuItem(String key, String text) {
        System.out.println("  " + ColorUtil.menuNumber("[" + key + "]") + " " + text);
    }

    // ------------------------------------------------------------ messages

    public static void success(String message) {
        System.out.println(ColorUtil.success(message));
    }

    public static void error(String message) {
        System.out.println(ColorUtil.error(message));
    }

    public static void warning(String message) {
        System.out.println(ColorUtil.warning(message));
    }

    public static void info(String message) {
        System.out.println(message);
    }

    /** Shows storage warnings (corrupted file, etc.) on their own screen, then waits for Enter. */
    public static void showWarnings(List<String> warnings) {
        if (warnings == null || warnings.isEmpty()) {
            return;
        }
        clear();
        banner("NOTICE");
        System.out.println();
        for (String warning : warnings) {
            warning(warning);
        }
        System.out.println();
        InputUtil.pause();
    }

    // ------------------------------------------------------------ formatting

    /** The single price formatter used everywhere: "PHP 1,196.00". Plain text, color it at the call site. */
    public static String formatPrice(double amount) {
        return String.format(Locale.US, "%s %,.2f", Config.CURRENCY_CODE, amount);
    }

    public static String formatDateTime(LocalDateTime dateTime) {
        return dateTime.format(DATE_TIME);
    }

    public static String formatDate(LocalDate date) {
        return date.toString();
    }

    // ------------------------------------------------------------ text padding

    /** Cuts the text to max characters, ending with "..." when shortened. */
    public static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        if (text.length() <= max) {
            return text;
        }
        if (max <= 3) {
            return text.substring(0, Math.max(max, 0));
        }
        return text.substring(0, max - 3) + "...";
    }

    /** Left-aligned text padded (or truncated) to exactly width characters. */
    public static String padRight(String text, int width) {
        String t = truncate(text, width);
        return t + " ".repeat(width - t.length());
    }

    /** Right-aligned text padded (or truncated) to exactly width characters. */
    public static String padLeft(String text, int width) {
        String t = truncate(text, width);
        return " ".repeat(width - t.length()) + t;
    }

    public static String center(String text, int width) {
        String t = truncate(text, width);
        int total = width - t.length();
        int left = total / 2;
        return " ".repeat(left) + t + " ".repeat(total - left);
    }
}