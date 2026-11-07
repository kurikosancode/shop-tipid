package com.shop.model;

import java.util.Objects;

/**
 * Catalog item. Stored in a HashMap<String, Product> (keyed by id) by ProductService.
 * Equality is based on id only. Getters/setters are O(1).
 */
public class Product {

    private String id;
    private String name;
    private double price;
    private String category;
    private String label;
    private double rating;        // 0..5
    private int purchaseCount;
    private int stock;

    public Product() { }

    public Product(String id, String name, double price, String category, String label,
                   double rating, int purchaseCount, int stock) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.category = category;
        this.label = label;
        this.rating = rating;
        this.purchaseCount = purchaseCount;
        this.stock = stock;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public double getPrice() { return price; }
    public String getCategory() { return category; }
    public String getLabel() { return label; }
    public double getRating() { return rating; }
    public int getPurchaseCount() { return purchaseCount; }
    public int getStock() { return stock; }

    public void setName(String name) { this.name = name; }
    public void setPrice(double price) { this.price = price; }
    public void setCategory(String category) { this.category = category; }
    public void setLabel(String label) { this.label = label; }
    public void setRating(double rating) { this.rating = rating; }
    public void setPurchaseCount(int purchaseCount) { this.purchaseCount = purchaseCount; }
    public void setStock(int stock) { this.stock = stock; }

    public boolean isInStock() {
        return stock > 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Product)) {
            return false;
        }
        return Objects.equals(id, ((Product) o).id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return id + " - " + name + " (" + category + "/" + label + ")";
    }
}
