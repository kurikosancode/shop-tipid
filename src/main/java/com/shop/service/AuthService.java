package com.shop.service;

import com.shop.config.Config;
import com.shop.model.Role;
import com.shop.model.User;
import com.shop.storage.JsonStore;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Accounts, login/logout, and the current session.
 *
 * Data structure: HASH TABLE, HashMap<String, User> keyed by lowercase username
 * (so usernames are unique regardless of upper/lower case).
 * Complexity: register / login / findUser / usernameExists are O(1) average
 * (SHA-256 of a short string is a constant cost). getAllUsers is O(n log n) for sorting,
 * countAdmins is O(n), and every save is O(n) in the number of users (file write).
 *
 * Passwords: stored as SHA-256(salt + password) in hex, with a random 16-byte salt per user.
 * The plain password is never stored or written to disk.
 */
public class AuthService {

    /** Error that is safe to show to the person using the program. */
    public static class AuthException extends Exception {
        public AuthException(String message) {
            super(message);
        }
    }

    private final JsonStore store;
    private final Map<String, User> users = new HashMap<>();
    private final SecureRandom random = new SecureRandom();
    private User currentUser;

    public AuthService(JsonStore store) {
        this.store = store;
        for (User user : store.loadUsers()) {
            users.put(key(user.getUsername()), user);
        }
        seedDefaultAdmin();
    }

    // ------------------------------------------------------------ seeding

    /** First run (or no admin left): create admin / admin123. */
    private void seedDefaultAdmin() {
        if (countAdmins() > 0 || users.containsKey(key(Config.DEFAULT_ADMIN_USERNAME))) {
            return;
        }
        createUser(Config.DEFAULT_ADMIN_USERNAME, Config.DEFAULT_ADMIN_PASSWORD, Role.ADMIN);
        saveUsers();
    }

    // ------------------------------------------------------------ validation

    /** Returns an error message, or null when the username is acceptable. */
    public static String validateUsername(String username) {
        if (username == null || username.isBlank()) {
            return "Username cannot be empty.";
        }
        if (username.length() < Config.USERNAME_MIN_LENGTH || username.length() > Config.USERNAME_MAX_LENGTH) {
            return "Username must be " + Config.USERNAME_MIN_LENGTH + "-" + Config.USERNAME_MAX_LENGTH
                    + " characters long.";
        }
        if (!username.matches(Config.USERNAME_PATTERN)) {
            return "Username may only contain letters, numbers and underscores (no spaces).";
        }
        return null;
    }

    /** Returns an error message, or null when the password is acceptable. */
    public static String validatePassword(String password) {
        if (password == null || password.isBlank()) {
            return "Password cannot be empty.";
        }
        if (password.length() < Config.PASSWORD_MIN_LENGTH || password.length() > Config.PASSWORD_MAX_LENGTH) {
            return "Password must be " + Config.PASSWORD_MIN_LENGTH + "-" + Config.PASSWORD_MAX_LENGTH
                    + " characters long.";
        }
        return null;
    }

    public boolean usernameExists(String username) {
        return username != null && users.containsKey(key(username.trim()));
    }

    // ------------------------------------------------------------ sign up / log in / log out

    /** Creates a CUSTOMER account and saves users.json. */
    public User register(String username, String password) throws AuthException {
        String name = username == null ? "" : username.trim();
        String error = validateUsername(name);
        if (error == null) {
            error = validatePassword(password);
        }
        if (error != null) {
            throw new AuthException(error);
        }
        if (usernameExists(name)) {
            throw new AuthException("That username is already taken.");
        }
        User user = createUser(name, password, Role.CUSTOMER);
        if (!saveUsers()) {
            users.remove(key(name));
            throw new AuthException("Could not save your account. Please try again.");
        }
        return user;
    }

    /** Checks the credentials and starts the session. The error text never says which part was wrong. */
    public User login(String username, String password) throws AuthException {
        User user = users.get(key(username == null ? "" : username.trim()));
        if (user == null || password == null || !passwordMatches(user, password)) {
            throw new AuthException("Invalid username or password.");
        }
        currentUser = user;
        return user;
    }

    public void logout() {
        currentUser = null;
    }

    public User getCurrentUser() {
        return currentUser;
    }

    public boolean isLoggedIn() {
        return currentUser != null;
    }

    // ------------------------------------------------------------ admin helpers (used by AdminMenu in Phase 5)

    /** All users sorted by username. */
    public List<User> getAllUsers() {
        List<User> list = new ArrayList<>(users.values());
        list.sort(Comparator.comparing(u -> u.getUsername().toLowerCase(Locale.ROOT)));
        return list;
    }

    /** Returns the user, or null if no such username. */
    public User findUser(String username) {
        return username == null ? null : users.get(key(username.trim()));
    }

    public int countAdmins() {
        int count = 0;
        for (User user : users.values()) {
            if (user.isAdmin()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Removes an account. Not allowed for your own account or for the last remaining admin.
     * (The UI asks for confirmation before calling this.)
     */
    public void removeUser(String targetUsername, String requesterUsername) throws AuthException {
        User target = findUser(targetUsername);
        if (target == null) {
            throw new AuthException("User not found.");
        }
        if (requesterUsername != null && key(target.getUsername()).equals(key(requesterUsername))) {
            throw new AuthException("You cannot remove your own account.");
        }
        if (target.isAdmin() && countAdmins() <= 1) {
            throw new AuthException("You cannot remove the last admin account.");
        }
        users.remove(key(target.getUsername()));
        if (!saveUsers()) {
            users.put(key(target.getUsername()), target);
            throw new AuthException("Could not save the change. The account was not removed.");
        }
    }

    /** Writes users.json (call after changing a user's settings). Returns false if saving failed. */
    public boolean saveUsers() {
        return store.saveUsers(getAllUsers());
    }

    // ------------------------------------------------------------ internals

    private User createUser(String username, String password, Role role) {
        String salt = newSalt();
        User user = new User(username, hash(salt, password), salt, role, LocalDate.now());
        users.put(key(username), user);
        return user;
    }

    private boolean passwordMatches(User user, String password) {
        byte[] expected = user.getPasswordHash().getBytes(StandardCharsets.UTF_8);
        byte[] actual = hash(user.getSalt(), password).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual); // constant-time comparison
    }

    private String newSalt() {
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private static String hash(String salt, String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] out = digest.digest((salt + password).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(out);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available on this Java installation", e);
        }
    }

    private static String key(String username) {
        return username.toLowerCase(Locale.ROOT);
    }
}