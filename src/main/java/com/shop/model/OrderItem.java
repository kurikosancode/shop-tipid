package com.shop.model;

/**
 * One line of an order. Stores a snapshot (name, category, label, price at purchase)
 * so receipts and spending summaries stay correct even if the product is later edited or removed.
 * Getters are O(1).
 */
public class OrderItem {

    private String productId;
    private String productName;
    private String category;
    private String label;
    private int quantity;
    private double priceAtPurchase;

    public OrderItem() { }

    public OrderItem(String productId, String productName, String category, String label,
                     int quantity, double priceAtPurchase) {
        this.productId = productId;
        this.productName = productName;
        this.category = category;
        this.label = label;
        this.quantity = quantity;
        this.priceAtPurchase = priceAtPurchase;
    }

    public String getProductId() { return productId; }
    public String getProductName() { return productName; }
    public String getCategory() { return category; }
    public String getLabel() { return label; }
    public int getQuantity() { return quantity; }
    public double getPriceAtPurchase() { return priceAtPurchase; }

    public double getLineTotal() {
        return Math.round(priceAtPurchase * quantity * 100.0) / 100.0;
    }
}