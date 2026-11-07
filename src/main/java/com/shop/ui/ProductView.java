package com.shop.ui;

import com.shop.model.Product;

import java.util.List;
import java.util.Locale;

/**
 * Shared product table and detail layout (used by customer and admin screens).
 * Data structure: none (static printing helpers). O(n) for n rows printed.
 * Text is padded to its column width BEFORE coloring, so colors never break alignment.
 */
public final class ProductView {

    private ProductView() { }

    /** Numbered table: # | Name | Price | Rating | Stock (64 characters wide). */
    public static void printTable(List<Product> list) {
        System.out.println(ColorUtil.dim(ScreenUtil.padLeft("#", 3) + "  " + ScreenUtil.padRight("Name", 28)
                + "  " + ScreenUtil.padLeft("Price", 14) + "  " + ScreenUtil.padLeft("Rate", 5)
                + "  " + ScreenUtil.padLeft("Stock", 6)));
        ScreenUtil.divider();
        for (int i = 0; i < list.size(); i++) {
            Product p = list.get(i);
            String stockText = p.isInStock() ? String.valueOf(p.getStock()) : "OUT";
            String stock = ScreenUtil.padLeft(stockText, 6);
            System.out.println(ColorUtil.menuNumber(ScreenUtil.padLeft(String.valueOf(i + 1), 3))
                    + "  " + ScreenUtil.padRight(p.getName(), 28)
                    + "  " + ColorUtil.price(ScreenUtil.padLeft(ScreenUtil.formatPrice(p.getPrice()), 14))
                    + "  " + ScreenUtil.padLeft(String.format(Locale.US, "%.1f", p.getRating()), 5)
                    + "  " + (p.isInStock() ? stock : ColorUtil.error(stock)));
        }
    }

    /** Detail block for one product. The caller prints the banner. */
    public static void printDetails(Product p) {
        row("Name", ColorUtil.bold(p.getName()));
        row("Product ID", p.getId());
        row("Category", p.getCategory() + " > " + p.getLabel());
        row("Price", ColorUtil.price(ScreenUtil.formatPrice(p.getPrice())));
        row("Rating", String.format(Locale.US, "%.1f / 5", p.getRating()));
        row("Purchased", p.getPurchaseCount() + " time(s)");
        row("Availability", p.isInStock()
                ? ColorUtil.success("In stock (" + p.getStock() + " left)")
                : ColorUtil.error("Out of stock"));
    }

    private static void row(String label, String value) {
        System.out.println(ColorUtil.dim(ScreenUtil.padRight(label + ":", 14)) + value);
    }
}