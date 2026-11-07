package com.shop.model;

public record CartLine(Product product, int quantity) {

    public double lineTotal() {
        return Math.round(product.getPrice() * quantity * 100.0) / 100.0;
    }
}