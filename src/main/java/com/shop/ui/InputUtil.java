package com.shop.ui;

import java.io.Console;
import java.util.Arrays;
import java.util.NoSuchElementException;
import java.util.Scanner;

/**
 * Reusable, crash-proof keyboard input. Every "read" method keeps re-prompting until the
 * value is valid (empty, non-numeric, out of range, wrong format, negative numbers...).
 * Data structure: one shared Scanner over System.in. Each read is O(length of the line).
 *
 * If the input stream is closed (Ctrl+D / Ctrl+Z), InputClosedException is thrown so Main
 * can exit cleanly instead of looping forever.
 */
public final class InputUtil {

    /** Thrown when System.in is closed. Caught in Main to exit quietly. */
    public static class InputClosedException extends RuntimeException {
        public InputClosedException() {
            super("Input stream closed");
        }
    }

    private static final Scanner SCANNER = new Scanner(System.in);
    private static final String NUMBER_PATTERN = "-?(\\d+(\\.\\d+)?|\\.\\d+)";

    private InputUtil() { }

    // ------------------------------------------------------------ raw reading

    private static String readRaw() {
        try {
            if (!SCANNER.hasNextLine()) {
                throw new InputClosedException();
            }
            return SCANNER.nextLine().trim();
        } catch (IllegalStateException | NoSuchElementException e) {
            throw new InputClosedException();
        }
    }

    /** Reads one trimmed line. An empty answer is allowed (used for "blank = cancel"). */
    public static String readOptionalLine(String prompt) {
        System.out.print(ColorUtil.bold(prompt) + " ");
        System.out.flush();
        return readRaw();
    }

    /** "Press Enter to continue" so messages are not wiped by the next screen clear. */
    public static void pause() {
        System.out.print(ColorUtil.dim("Press Enter to continue... "));
        System.out.flush();
        readRaw();
    }

    // ------------------------------------------------------------ text

    public static String readNonEmpty(String prompt) {
        while (true) {
            String line = readOptionalLine(prompt);
            if (!line.isEmpty()) {
                return line;
            }
            ScreenUtil.error("Input cannot be empty.");
        }
    }

    /** Re-prompts until the text fully matches the regex. */
    public static String readPattern(String prompt, String regex, String errorMessage) {
        while (true) {
            String line = readNonEmpty(prompt);
            if (line.matches(regex)) {
                return line;
            }
            ScreenUtil.error(errorMessage);
        }
    }

    /** Password input. Hidden when the terminal supports it, otherwise visible (and labeled so). */
    public static String readPassword(String prompt) {
        Console console = System.console();
        if (console != null) {
            try {
                char[] chars = console.readPassword("%s ", prompt);
                if (chars != null) {
                    String password = new String(chars).trim();
                    Arrays.fill(chars, ' ');
                    return password;
                }
            } catch (RuntimeException e) {
                // fall back to a visible prompt below
            }
        }
        return readOptionalLine(prompt + " (visible)");
    }

    // ------------------------------------------------------------ yes / no

    public static boolean readYesNo(String prompt) {
        while (true) {
            String line = readOptionalLine(prompt + " (y/n):").toLowerCase();
            if (line.equals("y") || line.equals("yes")) {
                return true;
            }
            if (line.equals("n") || line.equals("no")) {
                return false;
            }
            ScreenUtil.error("Please answer with y or n.");
        }
    }

    // ------------------------------------------------------------ whole numbers

    /** Whole number between min and max (inclusive). Re-prompts until valid. */
    public static int readInt(String prompt, int min, int max) {
        while (true) {
            String line = readOptionalLine(prompt);
            if (line.isEmpty()) {
                ScreenUtil.error("Input cannot be empty.");
                continue;
            }
            Integer value = parseInt(line, min, max);
            if (value != null) {
                return value;
            }
        }
    }

    /** Like readInt, but a blank answer returns null (meaning "skip / cancel"). */
    public static Integer readOptionalInt(String prompt, int min, int max) {
        while (true) {
            String line = readOptionalLine(prompt);
            if (line.isEmpty()) {
                return null;
            }
            Integer value = parseInt(line, min, max);
            if (value != null) {
                return value;
            }
        }
    }

    private static Integer parseInt(String line, int min, int max) {
        try {
            long value = Long.parseLong(line.replace(",", ""));
            if (value < 0 && min >= 0) {
                ScreenUtil.error("Negative numbers are not allowed.");
                return null;
            }
            if (value < min || value > max) {
                ScreenUtil.error("Please enter a number from " + min + " to " + max + ".");
                return null;
            }
            return (int) value;
        } catch (NumberFormatException e) {
            ScreenUtil.error("Please enter a whole number (digits only).");
            return null;
        }
    }

    // ------------------------------------------------------------ decimal numbers

    /** Decimal number between min and max (inclusive). Use Double.MAX_VALUE for "no upper limit". */
    public static double readDouble(String prompt, double min, double max) {
        while (true) {
            String line = readOptionalLine(prompt);
            if (line.isEmpty()) {
                ScreenUtil.error("Input cannot be empty.");
                continue;
            }
            Double value = parseDouble(line, min, max);
            if (value != null) {
                return value;
            }
        }
    }

    /** Like readDouble, but a blank answer returns null (meaning "skip / not set"). */
    public static Double readOptionalDouble(String prompt, double min, double max) {
        while (true) {
            String line = readOptionalLine(prompt);
            if (line.isEmpty()) {
                return null;
            }
            Double value = parseDouble(line, min, max);
            if (value != null) {
                return value;
            }
        }
    }

    /** Money amount, rounded to 2 decimal places. */
    public static double readMoney(String prompt, double min, double max) {
        double value = readDouble(prompt, min, max);
        return Math.round(value * 100.0) / 100.0;
    }

    private static Double parseDouble(String line, double min, double max) {
        String cleaned = line.replace(",", "");
        if (!cleaned.matches(NUMBER_PATTERN)) {
            ScreenUtil.error("Please enter a valid number (example: 1250.50).");
            return null;
        }
        double value;
        try {
            value = Double.parseDouble(cleaned);
        } catch (NumberFormatException e) {
            ScreenUtil.error("Please enter a valid number (example: 1250.50).");
            return null;
        }
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            ScreenUtil.error("Please enter a valid number.");
            return null;
        }
        if (value < 0 && min >= 0) {
            ScreenUtil.error("Negative numbers are not allowed.");
            return null;
        }
        if (value < min || value > max) {
            if (max >= Double.MAX_VALUE) {
                ScreenUtil.error("Please enter " + plain(min) + " or more.");
            } else {
                ScreenUtil.error("Please enter a number from " + plain(min) + " to " + plain(max) + ".");
            }
            return null;
        }
        return value;
    }

    private static String plain(double d) {
        return d == Math.floor(d) ? String.valueOf((long) d) : String.valueOf(d);
    }
}