package com.shop.service;

import com.shop.model.CartLine;
import com.shop.model.Order;
import com.shop.model.OrderItem;
import com.shop.model.Product;
import com.shop.storage.JsonStore;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Checkout, order storage and purchase statistics.
 *
 * Data structures:
 *   orders : ArrayList<Order>                 all orders in the order they were placed
 *   byUser : HashMap<String, List<Order>>     HASH TABLE index, username -> that user's orders
 * Complexity: placeOrder is O(k) for k cart lines plus the file save O(total orders);
 * getOrdersForUser is O(m log m) for m orders of that user (sorting newest first);
 * statistics methods are O(m) for one user, or O(total items) for shop-wide numbers.
 *
 * Other classes can register a checkout listener (addCheckoutListener); Phase 4 uses it to
 * update the similarity graph after every checkout.
 */
public class OrderService {

    /** Error that is safe to show to the person using the program. */
    public static class OrderException extends Exception {
        public OrderException(String message) {
            super(message);
        }
    }

    public record CategorySpend(String category, int units, double spent) { }

    public record ProductSales(String productId, String name, int units, double revenue) { }

    public record UserActivity(String username, int orders, double spent) { }

    private final JsonStore store;
    private final ProductService products;
    private final List<Order> orders = new ArrayList<>();
    private final Map<String, List<Order>> byUser = new HashMap<>();
    private final List<Consumer<Order>> listeners = new ArrayList<>();
    private int nextNumber = 1;

    public OrderService(JsonStore store, ProductService products) {
        this.store = store;
        this.products = products;
        for (Order order : store.loadOrders()) {
            orders.add(order);
            byUser.computeIfAbsent(order.getUsername(), k -> new ArrayList<>()).add(order);
            nextNumber = Math.max(nextNumber, numberOf(order.getId()) + 1);
        }
    }

    public void addCheckoutListener(Consumer<Order> listener) {
        listeners.add(listener);
    }

    // ------------------------------------------------------------ checkout

    /**
     * Places an order for the given lines (used by cart checkout AND Buy now).
     * Checks every line first, then reduces stock, increases purchaseCount, saves the files,
     * and notifies listeners. If saving fails, everything is rolled back.
     */
    public Order placeOrder(String username, List<CartLine> lines) throws OrderException {
        if (lines == null || lines.isEmpty()) {
            throw new OrderException("There is nothing to order.");
        }

        List<CartLine> checked = new ArrayList<>();
        List<OrderItem> items = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (CartLine line : lines) {
            Product current = products.getById(line.product().getId());
            if (current == null) {
                throw new OrderException(line.product().getName() + " is no longer available.");
            }
            if (line.quantity() <= 0 || !seen.add(current.getId())) {
                throw new OrderException("Invalid quantity for " + current.getName() + ".");
            }
            if (current.getStock() < line.quantity()) {
                throw new OrderException(current.getName() + (current.getStock() == 0
                        ? " is out of stock." : " only has " + current.getStock() + " left."));
            }
            checked.add(new CartLine(current, line.quantity()));
            items.add(new OrderItem(current.getId(), current.getName(), current.getCategory(),
                    current.getLabel(), line.quantity(), current.getPrice()));
        }

        for (CartLine line : checked) {
            products.applyPurchase(line.product().getId(), line.quantity());
        }
        Order order = new Order(String.format("ORD-%05d", nextNumber++), username, LocalDateTime.now(), items);
        orders.add(order);
        byUser.computeIfAbsent(username, k -> new ArrayList<>()).add(order);

        boolean saved = store.saveOrders(orders) && products.save();
        if (!saved) {
            for (CartLine line : checked) {
                products.revertPurchase(line.product().getId(), line.quantity());
            }
            orders.remove(order);
            byUser.get(username).remove(order);
            store.saveOrders(orders);
            products.save();
            throw new OrderException("Could not save your order, so nothing was charged. Please try again.");
        }

        for (Consumer<Order> listener : listeners) {
            try {
                listener.accept(order);
            } catch (RuntimeException ignored) {
                // A listener problem must never undo a finished purchase.
            }
        }
        return order;
    }

    // ------------------------------------------------------------ queries

    /** The user's orders, newest first. */
    public List<Order> getOrdersForUser(String username) {
        List<Order> list = new ArrayList<>(byUser.getOrDefault(username, new ArrayList<>()));
        Comparator<Order> oldestFirst = Comparator.comparing(Order::getDateTime).thenComparing(Order::getId);
        list.sort(oldestFirst.reversed());
        return list;
    }

    public List<Order> getAllOrders() {
        return new ArrayList<>(orders);
    }

    public int getOrderCount(String username) {
        return byUser.getOrDefault(username, new ArrayList<>()).size();
    }

    public double getTotalSpent(String username) {
        double sum = 0;
        for (Order order : byUser.getOrDefault(username, new ArrayList<>())) {
            sum += order.getTotal();
        }
        return Math.round(sum * 100.0) / 100.0;
    }

    /** Product ids the user has ever bought (recommendations must never suggest these again). */
    public Set<String> getPurchasedProductIds(String username) {
        Set<String> ids = new HashSet<>();
        for (Order order : byUser.getOrDefault(username, new ArrayList<>())) {
            for (OrderItem item : order.getItems()) {
                ids.add(item.getProductId());
            }
        }
        return ids;
    }

    /** Units and money spent per category for one user, biggest spending first. */
    public List<CategorySpend> getSpendingByCategory(String username) {
        Map<String, double[]> totals = new LinkedHashMap<>();
        for (Order order : byUser.getOrDefault(username, new ArrayList<>())) {
            for (OrderItem item : order.getItems()) {
                String category = item.getCategory() == null ? "Other" : item.getCategory();
                double[] t = totals.computeIfAbsent(category, k -> new double[2]);
                t[0] += item.getQuantity();
                t[1] += item.getLineTotal();
            }
        }
        List<CategorySpend> result = new ArrayList<>();
        for (Map.Entry<String, double[]> entry : totals.entrySet()) {
            result.add(new CategorySpend(entry.getKey(), (int) entry.getValue()[0],
                    Math.round(entry.getValue()[1] * 100.0) / 100.0));
        }
        result.sort(Comparator.comparingDouble(CategorySpend::spent).reversed());
        return result;
    }

    /** Category with the most units bought (ties: higher spending). Null if no purchases. */
    public String getFavoriteCategory(String username) {
        CategorySpend best = null;
        for (CategorySpend spend : getSpendingByCategory(username)) {
            if (best == null || spend.units() > best.units()
                    || (spend.units() == best.units() && spend.spent() > best.spent())) {
                best = spend;
            }
        }
        return best == null ? null : best.category();
    }

    // ------------------------------------------------------------ shop-wide statistics (used by AdminMenu)

    public double getTotalRevenue() {
        double sum = 0;
        for (Order order : orders) {
            sum += order.getTotal();
        }
        return Math.round(sum * 100.0) / 100.0;
    }

    /** Sales per product, best-selling (most units) first. */
    public List<ProductSales> getSalesByProduct() {
        Map<String, Object[]> totals = new LinkedHashMap<>(); // id -> {name, units, revenue}
        for (Order order : orders) {
            for (OrderItem item : order.getItems()) {
                Object[] t = totals.computeIfAbsent(item.getProductId(),
                        k -> new Object[]{item.getProductName(), 0, 0.0});
                t[1] = (Integer) t[1] + item.getQuantity();
                t[2] = (Double) t[2] + item.getLineTotal();
            }
        }
        List<ProductSales> result = new ArrayList<>();
        for (Map.Entry<String, Object[]> entry : totals.entrySet()) {
            Object[] t = entry.getValue();
            result.add(new ProductSales(entry.getKey(), (String) t[0], (Integer) t[1],
                    Math.round((Double) t[2] * 100.0) / 100.0));
        }
        result.sort(Comparator.comparingInt(ProductSales::units).reversed()
                .thenComparing(Comparator.comparingDouble(ProductSales::revenue).reversed()));
        return result;
    }

    /** Orders and spending per user, most orders first. */
    public List<UserActivity> getUserActivity() {
        List<UserActivity> result = new ArrayList<>();
        for (Map.Entry<String, List<Order>> entry : byUser.entrySet()) {
            double spent = 0;
            for (Order order : entry.getValue()) {
                spent += order.getTotal();
            }
            result.add(new UserActivity(entry.getKey(), entry.getValue().size(),
                    Math.round(spent * 100.0) / 100.0));
        }
        result.sort(Comparator.comparingInt(UserActivity::orders).reversed()
                .thenComparing(Comparator.comparingDouble(UserActivity::spent).reversed()));
        return result;
    }

    // ------------------------------------------------------------ internals

    private static int numberOf(String orderId) {
        try {
            return Integer.parseInt(orderId.replaceAll("\\D", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
