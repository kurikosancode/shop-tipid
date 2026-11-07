package com.shop.service;

import com.shop.config.Config;
import com.shop.model.CartLine;
import com.shop.model.Product;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shopping carts (kept in memory only; a cart is not saved to disk).
 *
 * Data structure: HASH TABLE of hash tables.
 *   carts : HashMap<String username, LinkedHashMap<String productId, Integer quantity>>
 * LinkedHashMap keeps items in the order they were added.
 * Complexity: add / setQuantity / remove / getQuantity are O(1) average;
 * getLines, getTotal, getItemCount are O(k) for k distinct items in the cart.
 */
public class CartService {

    /** Error that is safe to show to the person using the program. */
    public static class CartException extends Exception {
        public CartException(String message) {
            super(message);
        }
    }

    private final ProductService products;
    private final Map<String, Map<String, Integer>> carts = new HashMap<>();

    public CartService(ProductService products) {
        this.products = products;
    }

    private Map<String, Integer> cartOf(String username) {
        return carts.computeIfAbsent(username, k -> new LinkedHashMap<>());
    }

    public int getQuantity(String username, String productId) {
        Integer quantity = cartOf(username).get(productId);
        return quantity == null ? 0 : quantity;
    }

    /** How many more units of this product the user may still add. */
    public int maxAddable(String username, Product product) {
        int cap = Math.min(product.getStock(), Config.MAX_CART_QTY_PER_ITEM);
        return Math.max(0, cap - getQuantity(username, product.getId()));
    }

    public void add(String username, Product product, int quantity) throws CartException {
        if (quantity <= 0) {
            throw new CartException("Quantity must be at least 1.");
        }
        if (!product.isInStock()) {
            throw new CartException(product.getName() + " is out of stock.");
        }
        int max = maxAddable(username, product);
        if (max <= 0) {
            throw new CartException("You already have the maximum available quantity of this item in your cart.");
        }
        if (quantity > max) {
            throw new CartException("You can add at most " + max + " more of this item.");
        }
        cartOf(username).merge(product.getId(), quantity, Integer::sum);
    }

    /** Sets the quantity of an item already in the cart. */
    public void setQuantity(String username, Product product, int quantity) throws CartException {
        int cap = Math.min(product.getStock(), Config.MAX_CART_QTY_PER_ITEM);
        if (cap <= 0) {
            throw new CartException(product.getName() + " is out of stock. Remove it from your cart.");
        }
        if (quantity < 1 || quantity > cap) {
            throw new CartException("Quantity must be between 1 and " + cap + ".");
        }
        cartOf(username).put(product.getId(), quantity);
    }

    public void remove(String username, String productId) {
        cartOf(username).remove(productId);
    }

    public void clear(String username) {
        cartOf(username).clear();
    }

    /** Cart lines in the order added. Items whose product was deleted by an admin are dropped. */
    public List<CartLine> getLines(String username) {
        List<CartLine> lines = new ArrayList<>();
        Iterator<Map.Entry<String, Integer>> it = cartOf(username).entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Integer> entry = it.next();
            Product product = products.getById(entry.getKey());
            if (product == null) {
                it.remove();
            } else {
                lines.add(new CartLine(product, entry.getValue()));
            }
        }
        return lines;
    }

    public double getTotal(String username) {
        double sum = 0;
        for (CartLine line : getLines(username)) {
            sum += line.lineTotal();
        }
        return Math.round(sum * 100.0) / 100.0;
    }

    /** Total number of units in the cart. */
    public int getItemCount(String username) {
        int count = 0;
        for (CartLine line : getLines(username)) {
            count += line.quantity();
        }
        return count;
    }

    public boolean isEmpty(String username) {
        return getLines(username).isEmpty();
    }
}
