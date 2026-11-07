package com.shop.model;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * Account record. Stored in a HashMap<String, User> (keyed by username) by AuthService.
 * Only a salted SHA-256 hash is stored, never the plain password.
 * Dates are kept as ISO strings so Gson can read/write them without custom adapters.
 * Getters/setters are O(1).
 */
public class User {

    private String username;
    private String passwordHash;
    private String salt;
    private Role role;
    private String joinDate;
    private RecommendationSettings settings;

    public User() { }

    public User(String username, String passwordHash, String salt, Role role, LocalDate joinDate) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.salt = salt;
        this.role = role;
        this.joinDate = joinDate.toString();
        this.settings = new RecommendationSettings();
    }

    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getSalt() { return salt; }
    public Role getRole() { return role; }

    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public void setSalt(String salt) { this.salt = salt; }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    public LocalDate getJoinDate() {
        try {
            return LocalDate.parse(joinDate);
        } catch (DateTimeParseException | NullPointerException e) {
            return LocalDate.of(1970, 1, 1);
        }
    }

    /** Never returns null, so old/hand-edited files without settings still work. */
    public RecommendationSettings getSettings() {
        if (settings == null) {
            settings = new RecommendationSettings();
        }
        return settings;
    }
}