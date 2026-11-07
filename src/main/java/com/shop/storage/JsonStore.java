package com.shop.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.shop.config.Config;
import com.shop.model.Order;
import com.shop.model.OrderItem;
import com.shop.model.Product;
import com.shop.model.User;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Reads and writes users.json, products.json and orders.json using Gson.
 * Data structures: ArrayList for the in-memory lists, HashSet to drop duplicate product/order ids.
 * Complexity: load and save are O(n) in the number of records (plus file I/O).
 *
 * Safety features (the program must never crash on bad data):
 *  - A missing file gives an empty list (products fall back to the seed catalog inside the jar).
 *  - A corrupted file is renamed to "<name>.corrupt-<timestamp>" and a fresh one is used.
 *  - Invalid individual records are skipped instead of failing the whole file.
 *  - Saves write to a temporary file first, then replace the real file.
 *  - Problems are collected as warnings; the UI shows them with drainWarnings().
 */
public class JsonStore {

    private static final Type USER_LIST = new TypeToken<List<User>>() { }.getType();
    private static final Type PRODUCT_LIST = new TypeToken<List<Product>>() { }.getType();
    private static final Type ORDER_LIST = new TypeToken<List<Order>>() { }.getType();

    private final Path dataDir;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private final List<String> warnings = new ArrayList<>();

    public JsonStore() {
        this(Paths.get(Config.DATA_DIR));
    }

    public JsonStore(Path dataDir) {
        this.dataDir = dataDir;
        try {
            Files.createDirectories(dataDir);
        } catch (IOException | RuntimeException e) {
            warn("Could not create the data folder: " + e.getMessage());
        }
    }

    /** Returns and clears all warnings collected so far. */
    public List<String> drainWarnings() {
        List<String> copy = new ArrayList<>(warnings);
        warnings.clear();
        return copy;
    }

    // ------------------------------------------------------------------ users

    public List<User> loadUsers() {
        List<User> raw = readList(Config.USERS_FILE, USER_LIST);
        List<User> result = new ArrayList<>();
        int skipped = 0;
        Set<String> seen = new HashSet<>();
        if (raw != null) {
            for (User u : raw) {
                boolean valid = u != null && notBlank(u.getUsername()) && notBlank(u.getPasswordHash())
                        && notBlank(u.getSalt()) && u.getRole() != null
                        && seen.add(u.getUsername().toLowerCase());
                if (valid) {
                    result.add(u);
                } else {
                    skipped++;
                }
            }
        }
        reportSkipped(skipped, Config.USERS_FILE);
        return result;
    }

    public boolean saveUsers(List<User> users) {
        return writeList(Config.USERS_FILE, users, USER_LIST);
    }

    // --------------------------------------------------------------- products

    public List<Product> loadProducts() {
        List<Product> raw = readList(Config.PRODUCTS_FILE, PRODUCT_LIST);
        if (raw == null) {
            // File missing or unreadable: restore the catalog bundled in the jar.
            List<Product> seed = sanitizeProducts(loadSeedProducts(), "built-in seed");
            if (!seed.isEmpty()) {
                warn(Config.PRODUCTS_FILE + " was missing or unreadable. The built-in product catalog was restored.");
                saveProducts(seed);
            } else {
                warn("No product catalog is available. An admin can add products from the Admin menu.");
            }
            return seed;
        }
        return sanitizeProducts(raw, Config.PRODUCTS_FILE);
    }

    public boolean saveProducts(List<Product> products) {
        return writeList(Config.PRODUCTS_FILE, products, PRODUCT_LIST);
    }

    private List<Product> sanitizeProducts(List<Product> raw, String source) {
        List<Product> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int skipped = 0;
        if (raw != null) {
            for (Product p : raw) {
                boolean valid = p != null && notBlank(p.getId()) && notBlank(p.getName())
                        && notBlank(p.getCategory()) && notBlank(p.getLabel())
                        && p.getPrice() >= 0 && p.getStock() >= 0 && p.getPurchaseCount() >= 0
                        && p.getRating() >= 0 && p.getRating() <= 5
                        && seen.add(p.getId());
                if (valid) {
                    result.add(p);
                } else {
                    skipped++;
                }
            }
        }
        reportSkipped(skipped, source);
        return result;
    }

    private List<Product> loadSeedProducts() {
        try (InputStream in = JsonStore.class.getResourceAsStream(Config.SEED_PRODUCTS_RESOURCE)) {
            if (in == null) {
                return new ArrayList<>();
            }
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                List<Product> list = gson.fromJson(reader, PRODUCT_LIST);
                return list == null ? new ArrayList<>() : list;
            }
        } catch (IOException | RuntimeException e) {
            return new ArrayList<>();
        }
    }

    // ----------------------------------------------------------------- orders

    public List<Order> loadOrders() {
        List<Order> raw = readList(Config.ORDERS_FILE, ORDER_LIST);
        List<Order> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int skipped = 0;
        if (raw != null) {
            for (Order o : raw) {
                boolean valid = o != null && notBlank(o.getId()) && notBlank(o.getUsername())
                        && !o.getItems().isEmpty() && seen.add(o.getId());
                if (valid) {
                    for (OrderItem item : o.getItems()) {
                        if (item == null || !notBlank(item.getProductId()) || item.getQuantity() <= 0) {
                            valid = false;
                            break;
                        }
                    }
                }
                if (valid) {
                    result.add(o);
                } else {
                    skipped++;
                }
            }
        }
        reportSkipped(skipped, Config.ORDERS_FILE);
        return result;
    }

    public boolean saveOrders(List<Order> orders) {
        return writeList(Config.ORDERS_FILE, orders, ORDER_LIST);
    }

    // ---------------------------------------------------------------- helpers

    /** Returns null when the file is missing, empty, or corrupted (corrupted files are backed up). */
    private <T> List<T> readList(String fileName, Type type) {
        Path path = dataDir.resolve(fileName);
        if (!Files.exists(path)) {
            return null;
        }
        try {
            String text = Files.readString(path, StandardCharsets.UTF_8);
            if (text.isBlank()) {
                warn(fileName + " was empty and will be recreated.");
                return null;
            }
            List<T> list = gson.fromJson(text, type);
            if (list == null) {
                warn(fileName + " had no usable data and will be recreated.");
            }
            return list;
        } catch (IOException | RuntimeException e) {
            backupCorrupt(path);
            warn(fileName + " could not be read (corrupted data). A backup copy was kept and a fresh file will be used.");
            return null;
        }
    }

    private boolean writeList(String fileName, Object list, Type type) {
        Path target = dataDir.resolve(fileName);
        Path temp = dataDir.resolve(fileName + ".tmp");
        try {
            Files.createDirectories(dataDir);
            Files.writeString(temp, gson.toJson(list, type), StandardCharsets.UTF_8);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException | RuntimeException e) {
            warn("Could not save " + fileName + ": " + e.getMessage());
            return false;
        }
    }

    private void backupCorrupt(Path path) {
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path backup = path.resolveSibling(path.getFileName() + ".corrupt-" + stamp);
        try {
            Files.move(path, backup, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException ignored) {
            // Best effort only.
        }
    }

    private void reportSkipped(int skipped, String source) {
        if (skipped > 0) {
            warn("Skipped " + skipped + " invalid record(s) in " + source + ".");
        }
    }

    private void warn(String message) {
        warnings.add(message);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}