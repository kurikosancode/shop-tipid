package com.shop.model;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * A completed checkout. Uses an ArrayList<OrderItem> for its lines.
 * computeTotal is O(k) for k lines; all other accessors are O(1).
 */
public class Order {

    private String id;
    private String username;
    private String date;                 // ISO_LOCAL_DATE_TIME
    private List<OrderItem> items = new ArrayList<>();
    private double total;

    public Order() { }

    public Order(String id, String username, LocalDateTime dateTime, List<OrderItem> items) {
        this.id = id;
        this.username = username;
        this.date = dateTime.truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        this.items = new ArrayList<>(items);
        this.total = computeTotal(this.items);
    }

    public static double computeTotal(List<OrderItem> items) {
        double sum = 0;
        for (OrderItem item : items) {
            sum += item.getLineTotal();
        }
        return Math.round(sum * 100.0) / 100.0;
    }

    public String getId() { return id; }
    public String getUsername() { return username; }
    public double getTotal() { return total; }

    public List<OrderItem> getItems() {
        return items == null ? new ArrayList<>() : items;
    }

    public LocalDateTime getDateTime() {
        try {
            return LocalDateTime.parse(date);
        } catch (DateTimeParseException | NullPointerException e) {
            return LocalDateTime.of(1970, 1, 1, 0, 0);
        }
    }

    /** Total number of units across all lines. */
    public int getItemCount() {
        int count = 0;
        for (OrderItem item : getItems()) {
            count += item.getQuantity();
        }
        return count;
    }
}