package com.shop.service;

import com.shop.config.Config;
import com.shop.model.Product;
import com.shop.storage.JsonStore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Product catalog with three HASH TABLES:
 *   products   : HashMap<String, Product>        id -> product            (lookup O(1) average)
 *   byCategory : HashMap<String, List<Product>>  category -> products     (lookup O(1) average)
 *   byLabel    : HashMap<String, List<Product>>  label -> products        (lookup O(1) average)
 * The category and label indexes are rebuilt after every catalog change: O(n).
 *
 * Complexity: getById O(1); getByCategory / getByLabel O(1) + copying the result list;
 * getByCategoryAndLabel O(k) for k products in the category; search O(n); getAll and
 * getCategories O(n log n) for sorting; add / update / remove O(n) because of the index
 * rebuild and the file save.
 */
public class ProductService {

    /** Error that is safe to show to the person using the program. */
    public static class ProductException extends Exception {
        public ProductException(String message) {
            super(message);
        }
    }

    private static final int MAX_CATEGORY_LABEL_LENGTH = 30;
    private static final double MAX_PRICE = 10_000_000;

    private final JsonStore store;
    private final Map<String, Product> products = new HashMap<>();
    private final Map<String, List<Product>> byCategory = new HashMap<>();
    private final Map<String, List<Product>> byLabel = new HashMap<>();

    public ProductService(JsonStore store) {
        this.store = store;
        for (Product product : store.loadProducts()) {
            products.put(idKey(product.getId()), product);
        }
        rebuildIndexes();
    }

    // ------------------------------------------------------------ lookups

    /** Returns the product, or null if there is no such id. */
    public Product getById(String id) {
        return id == null ? null : products.get(idKey(id));
    }

    /** All products sorted by id. */
    public List<Product> getAll() {
        List<Product> list = new ArrayList<>(products.values());
        list.sort(Comparator.comparing(Product::getId));
        return list;
    }

    public int size() {
        return products.size();
    }

    /** Category names (as displayed), sorted alphabetically. */
    public List<String> getCategories() {
        List<String> result = new ArrayList<>();
        for (List<Product> list : byCategory.values()) {
            result.add(list.get(0).getCategory());
        }
        result.sort(String.CASE_INSENSITIVE_ORDER);
        return result;
    }

    /** Distinct labels inside one category, sorted alphabetically. */
    public List<String> getLabels(String category) {
        Set<String> labels = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Product product : getByCategory(category)) {
            labels.add(product.getLabel());
        }
        return new ArrayList<>(labels);
    }

    public List<Product> getByCategory(String category) {
        List<Product> list = byCategory.get(key(category));
        return list == null ? new ArrayList<>() : new ArrayList<>(list);
    }

    public List<Product> getByLabel(String label) {
        List<Product> list = byLabel.get(key(label));
        return list == null ? new ArrayList<>() : new ArrayList<>(list);
    }

    public List<Product> getByCategoryAndLabel(String category, String label) {
        List<Product> result = new ArrayList<>();
        for (Product product : getByCategory(category)) {
            if (product.getLabel().equalsIgnoreCase(label)) {
                result.add(product);
            }
        }
        return result;
    }

    /** Case-insensitive "name contains text" search, sorted by name. */
    public List<Product> search(String text) {
        List<Product> result = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return result;
        }
        String needle = text.trim().toLowerCase(Locale.ROOT);
        for (Product product : products.values()) {
            if (product.getName().toLowerCase(Locale.ROOT).contains(needle)) {
                result.add(product);
            }
        }
        result.sort(Comparator.comparing(p -> p.getName().toLowerCase(Locale.ROOT)));
        return result;
    }

    /** Highest purchaseCount in the catalog (at least 1). Used to normalize popularity to 0..1. */
    public int getMaxPurchaseCount() {
        int max = 1;
        for (Product product : products.values()) {
            max = Math.max(max, product.getPurchaseCount());
        }
        return max;
    }

    /** Highest price in the catalog (at least 1). */
    public double getMaxPrice() {
        double max = 1;
        for (Product product : products.values()) {
            max = Math.max(max, product.getPrice());
        }
        return max;
    }

    // ------------------------------------------------------------ admin changes

    public Product addProduct(String name, double price, String category, String label,
                              double rating, int stock) throws ProductException {
        validate(name, price, category, label, rating, stock);
        Product product = new Product(nextId(), name.trim(), round2(price),
                canonicalCategory(category), canonicalLabel(label), round1(rating), 0, stock);
        products.put(idKey(product.getId()), product);
        rebuildIndexes();
        if (!save()) {
            products.remove(idKey(product.getId()));
            rebuildIndexes();
            throw new ProductException("Could not save the product. Nothing was changed.");
        }
        return product;
    }

    public void updateProduct(String id, String name, double price, String category, String label,
                              double rating, int stock) throws ProductException {
        Product product = getById(id);
        if (product == null) {
            throw new ProductException("Product not found.");
        }
        validate(name, price, category, label, rating, stock);

        String oldName = product.getName();
        double oldPrice = product.getPrice();
        String oldCategory = product.getCategory();
        String oldLabel = product.getLabel();
        double oldRating = product.getRating();
        int oldStock = product.getStock();

        String newCategory = canonicalCategory(category);
        String newLabel = canonicalLabel(label);
        product.setName(name.trim());
        product.setPrice(round2(price));
        product.setCategory(newCategory);
        product.setLabel(newLabel);
        product.setRating(round1(rating));
        product.setStock(stock);
        rebuildIndexes();

        if (!save()) {
            product.setName(oldName);
            product.setPrice(oldPrice);
            product.setCategory(oldCategory);
            product.setLabel(oldLabel);
            product.setRating(oldRating);
            product.setStock(oldStock);
            rebuildIndexes();
            throw new ProductException("Could not save the changes. Nothing was changed.");
        }
    }

    public void removeProduct(String id) throws ProductException {
        Product product = getById(id);
        if (product == null) {
            throw new ProductException("Product not found.");
        }
        products.remove(idKey(product.getId()));
        rebuildIndexes();
        if (!save()) {
            products.put(idKey(product.getId()), product);
            rebuildIndexes();
            throw new ProductException("Could not save the change. The product was not removed.");
        }
    }

    // ------------------------------------------------------------ used by OrderService

    /** Reduces stock and increases purchaseCount. Does NOT save (OrderService saves once). */
    void applyPurchase(String productId, int quantity) {
        Product product = getById(productId);
        if (product != null) {
            product.setStock(product.getStock() - quantity);
            product.setPurchaseCount(product.getPurchaseCount() + quantity);
        }
    }

    /** Undoes applyPurchase (used when saving an order fails). */
    void revertPurchase(String productId, int quantity) {
        Product product = getById(productId);
        if (product != null) {
            product.setStock(product.getStock() + quantity);
            product.setPurchaseCount(Math.max(0, product.getPurchaseCount() - quantity));
        }
    }

    public boolean save() {
        return store.saveProducts(getAll());
    }

    // ------------------------------------------------------------ internals

    private void rebuildIndexes() {
        byCategory.clear();
        byLabel.clear();
        for (Product product : getAll()) {
            byCategory.computeIfAbsent(key(product.getCategory()), k -> new ArrayList<>()).add(product);
            byLabel.computeIfAbsent(key(product.getLabel()), k -> new ArrayList<>()).add(product);
        }
    }

    private void validate(String name, double price, String category, String label,
                          double rating, int stock) throws ProductException {
        if (name == null || name.isBlank()) {
            throw new ProductException("Product name cannot be empty.");
        }
        if (name.trim().length() > Config.PRODUCT_NAME_MAX_LENGTH) {
            throw new ProductException("Product name is too long (max " + Config.PRODUCT_NAME_MAX_LENGTH
                    + " characters).");
        }
        if (Double.isNaN(price) || price <= 0 || price > MAX_PRICE) {
            throw new ProductException("Price must be greater than 0.");
        }
        if (category == null || category.isBlank() || label == null || label.isBlank()) {
            throw new ProductException("Category and label cannot be empty.");
        }
        if (category.trim().length() > MAX_CATEGORY_LABEL_LENGTH
                || label.trim().length() > MAX_CATEGORY_LABEL_LENGTH) {
            throw new ProductException("Category and label must be at most " + MAX_CATEGORY_LABEL_LENGTH
                    + " characters.");
        }
        if (Double.isNaN(rating) || rating < 0 || rating > 5) {
            throw new ProductException("Rating must be between 0 and 5.");
        }
        if (stock < 0) {
            throw new ProductException("Stock cannot be negative.");
        }
    }

    /** Reuses the spelling of an existing category (so "books" does not create a second "Books"). */
    private String canonicalCategory(String category) {
        List<Product> existing = byCategory.get(key(category));
        return existing == null || existing.isEmpty() ? category.trim() : existing.get(0).getCategory();
    }

    private String canonicalLabel(String label) {
        List<Product> existing = byLabel.get(key(label));
        return existing == null || existing.isEmpty() ? label.trim() : existing.get(0).getLabel();
    }

    private String nextId() {
        int max = 0;
        for (Product product : products.values()) {
            String id = product.getId().toUpperCase(Locale.ROOT);
            if (id.matches("P\\d+")) {
                try {
                    max = Math.max(max, Integer.parseInt(id.substring(1)));
                } catch (NumberFormatException ignored) {
                    // very long digit strings are ignored
                }
            }
        }
        return String.format("P%03d", max + 1);
    }

    private static String idKey(String id) {
        return id.trim().toUpperCase(Locale.ROOT);
    }

    private static String key(String text) {
        return text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}