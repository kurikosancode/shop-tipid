package com.shop.ui;

import com.shop.config.Config;
import com.shop.model.User;
import com.shop.service.AuthService;
import com.shop.service.AuthService.AuthException;

/**
 * Start screen: Log in, Sign up, About and credits, Exit.
 * Data structure: none of its own, it calls AuthService (HashMap lookups, O(1)
 * average).
 * The About screen only prints constants from Config, so it is O(number of
 * lines).
 */
public class AuthMenu {

    private static final int MAX_LOGIN_ATTEMPTS = 3;

    private final AuthService auth;

    public AuthMenu(AuthService auth) {
        this.auth = auth;
    }

    /**
     * Shows the start menu until someone logs in (returns that User) or chooses
     * Exit (returns null).
     */
    public User run() {
        while (true) {
            ScreenUtil.clear();
            ScreenUtil.banner("Welcome to " + Config.APP_NAME);
            System.out.println();
            ScreenUtil.menuItem("1", "Log in");
            ScreenUtil.menuItem("2", "Sign up");
            ScreenUtil.menuItem("3", "About");
            ScreenUtil.menuItem("0", "Exit");
            System.out.println();

            int choice = InputUtil.readInt("Choose an option:", 0, 3);
            switch (choice) {
                case 1 -> {
                    User user = login();
                    if (user != null) {
                        return user;
                    }
                }
                case 2 -> {
                    User user = signUp();
                    if (user != null) {
                        return user;
                    }
                }
                case 3 -> showAbout();
                default -> {
                    return null;
                }
            }
        }
    }

    // ------------------------------------------------------------ about & credits

    private void showAbout() {
        ScreenUtil.clear();
        ScreenUtil.banner("ABOUT");
        System.out.println();
        System.out.println(ColorUtil.bold(Config.APP_NAME) + ColorUtil.dim("  v" + Config.APP_VERSION));
        System.out.println();

        ScreenUtil.section("Description");
        System.out.println("A command-line online shop that suggests products you may like.");
        System.out.println("Customers browse, add to cart, buy and view receipts. After a");
        System.out.println("purchase, the system recommends similar items, and the Bundle");
        System.out.println("Builder finds the best set of items that fits a budget.");
        System.out.println();

        ScreenUtil.section("How it works");
        aboutRow("Hash tables", "users, products, category and label indexes");
        aboutRow("Similarity graph", "products linked by label, category, co-purchases");
        aboutRow("BFS", "finds related products up to 2 steps away");
        aboutRow("Heap", "PriorityQueue ranks the top recommendations");
        aboutRow("Backtracking", "builds the best bundle within a budget");
        System.out.println();

        ScreenUtil.section("Built with");
        System.out.println("Java, Maven, Gson (JSON files stored locally in /data)");
        System.out.println();

        ScreenUtil.section("Credits");
        System.out.println(ColorUtil.dim("Developed by:"));
        for (String member : Config.TEAM_MEMBERS) {
            System.out.println("  - " + member);
        }
        System.out.println();
        System.out.println(ColorUtil.dim("Course:       ") + Config.COURSE_NAME);
        System.out.println(ColorUtil.dim("Output:       ") + Config.OUTPUT_NAME);
        System.out.println();
        InputUtil.pause();
    }

    private static void aboutRow(String label, String value) {
        System.out.println(ColorUtil.menuNumber(ScreenUtil.padRight(label, 17)) + value);
    }

    // ------------------------------------------------------------ log in

    private User login() {
        int attempts = 0;
        while (attempts < MAX_LOGIN_ATTEMPTS) {
            ScreenUtil.clear();
            ScreenUtil.banner("LOG IN");
            System.out.println();
            System.out.println(ColorUtil.dim("Leave the username blank to go back."));
            System.out.println();

            String username = InputUtil.readOptionalLine("Username:");
            if (username.isEmpty()) {
                return null;
            }
            String password = InputUtil.readPassword("Password:");

            try {
                User user = auth.login(username, password);
                System.out.println();
                ScreenUtil.success("Welcome back, " + user.getUsername() + "!");
                InputUtil.pause();
                return user;
            } catch (AuthException e) {
                attempts++;
                System.out.println();
                ScreenUtil.error(e.getMessage());
                if (attempts < MAX_LOGIN_ATTEMPTS) {
                    ScreenUtil.warning("Attempts left: " + (MAX_LOGIN_ATTEMPTS - attempts));
                }
                InputUtil.pause();
            }
        }
        ScreenUtil.clear();
        ScreenUtil.banner("LOG IN");
        System.out.println();
        ScreenUtil.error("Too many failed attempts. Returning to the main menu.");
        InputUtil.pause();
        return null;
    }

    // ------------------------------------------------------------ sign up

    private User signUp() {
        ScreenUtil.clear();
        ScreenUtil.banner("SIGN UP");
        System.out.println();
        System.out.println("Username: " + Config.USERNAME_MIN_LENGTH + "-" + Config.USERNAME_MAX_LENGTH
                + " characters, letters, numbers and underscores only.");
        System.out.println("Password: at least " + Config.PASSWORD_MIN_LENGTH + " characters.");
        System.out.println(ColorUtil.dim("Leave the username blank to go back."));
        System.out.println();

        String username;
        while (true) {
            username = InputUtil.readOptionalLine("Choose a username:");
            if (username.isEmpty()) {
                return null;
            }
            String error = AuthService.validateUsername(username);
            if (error == null && auth.usernameExists(username)) {
                error = "That username is already taken.";
            }
            if (error == null) {
                break;
            }
            ScreenUtil.error(error);
        }

        String password;
        while (true) {
            password = InputUtil.readPassword("Choose a password:");
            String error = AuthService.validatePassword(password);
            if (error != null) {
                ScreenUtil.error(error);
                continue;
            }
            String confirm = InputUtil.readPassword("Confirm password:");
            if (!password.equals(confirm)) {
                ScreenUtil.error("Passwords do not match. Please try again.");
                continue;
            }
            break;
        }

        try {
            auth.register(username, password);
            User user = auth.login(username, password);
            System.out.println();
            ScreenUtil.success("Account created! Welcome, " + user.getUsername() + ".");
            InputUtil.pause();
            return user;
        } catch (AuthException e) {
            System.out.println();
            ScreenUtil.error(e.getMessage());
            InputUtil.pause();
            return null;
        }
    }
}