package com.shop.ui;

import com.shop.config.Config;
import com.shop.model.Order;
import com.shop.model.OrderItem;
import com.shop.service.OrderService;

import java.util.List;

/**
 * Purchase history list (newest first, 8 per page) and full receipts.
 * Data structure: uses the ArrayList returned by OrderService.getOrdersForUser (sorted newest first).
 * Complexity: showing a page is O(page size); loading the list is O(m log m) for m orders.
 * Also used by AdminMenu to show a selected user's order history.
 */
public class HistoryMenu {

    private static final int PAGE_SIZE = 8;

    private final OrderService orders;

    public HistoryMenu(OrderService orders) {
        this.orders = orders;
    }

    /** Order list for one user. Pick an order number to open its receipt. */
    public void run(String username, String title) {
        int page = 0;
        while (true) {
            List<Order> list = orders.getOrdersForUser(username);
            ScreenUtil.clear();
            ScreenUtil.banner(title);
            System.out.println();
            if (list.isEmpty()) {
                ScreenUtil.info("No orders yet.");
                System.out.println();
                InputUtil.pause();
                return;
            }

            int pages = (list.size() + PAGE_SIZE - 1) / PAGE_SIZE;
            page = Math.max(0, Math.min(page, pages - 1));
            int start = page * PAGE_SIZE;
            int end = Math.min(start + PAGE_SIZE, list.size());

            System.out.println(ColorUtil.dim(ScreenUtil.padLeft("#", 3) + "  " + ScreenUtil.padRight("Order ID", 10)
                    + "  " + ScreenUtil.padRight("Date", 16) + "  " + ScreenUtil.padLeft("Items", 5)
                    + "  " + ScreenUtil.padLeft("Total", 15)));
            ScreenUtil.divider();
            for (int i = start; i < end; i++) {
                Order order = list.get(i);
                System.out.println(ColorUtil.menuNumber(ScreenUtil.padLeft(String.valueOf(i + 1), 3))
                        + "  " + ScreenUtil.padRight(order.getId(), 10)
                        + "  " + ScreenUtil.padRight(ScreenUtil.formatDateTime(order.getDateTime()), 16)
                        + "  " + ScreenUtil.padLeft(String.valueOf(order.getItemCount()), 5)
                        + "  " + ColorUtil.price(ScreenUtil.padLeft(ScreenUtil.formatPrice(order.getTotal()), 15)));
            }
            System.out.println();
            System.out.println(ColorUtil.dim("Page " + (page + 1) + " of " + pages + "  (" + list.size() + " orders)"));
            System.out.println(ColorUtil.dim("Enter an order number to view its receipt"
                    + (pages > 1 ? ", N = next page, P = previous page" : "") + ", 0 = back."));

            String input = InputUtil.readOptionalLine("Your choice:").toLowerCase();
            if (input.equals("0")) {
                return;
            } else if (input.equals("n") && page < pages - 1) {
                page++;
            } else if (input.equals("p") && page > 0) {
                page--;
            } else if (input.matches("\\d{1,9}")) {
                int number = Integer.parseInt(input);
                if (number >= 1 && number <= list.size()) {
                    showReceipt(list.get(number - 1));
                } else {
                    ScreenUtil.error("Please enter a number from 1 to " + list.size() + ".");
                    InputUtil.pause();
                }
            } else if (!input.isEmpty()) {
                ScreenUtil.error("Invalid choice.");
                InputUtil.pause();
            }
        }
    }

    /** Full receipt for one order, then waits for Enter. */
    public void showReceipt(Order order) {
        ScreenUtil.clear();
        ScreenUtil.banner("RECEIPT");
        System.out.println();
        System.out.println(ColorUtil.dim("Order ID:  ") + ColorUtil.bold(order.getId()));
        System.out.println(ColorUtil.dim("Date:      ") + ScreenUtil.formatDateTime(order.getDateTime()));
        System.out.println(ColorUtil.dim("Customer:  ") + order.getUsername());
        System.out.println();
        System.out.println(ColorUtil.dim(ScreenUtil.padRight("Item", 23) + "  " + ScreenUtil.padLeft("Qty", 4)
                + "  " + ScreenUtil.padLeft("Price", 14) + "  " + ScreenUtil.padLeft("Total", 16)));
        ScreenUtil.divider();
        for (OrderItem item : order.getItems()) {
            System.out.println(ScreenUtil.padRight(item.getProductName(), 23)
                    + "  " + ScreenUtil.padLeft(String.valueOf(item.getQuantity()), 4)
                    + "  " + ColorUtil.price(ScreenUtil.padLeft(ScreenUtil.formatPrice(item.getPriceAtPurchase()), 14))
                    + "  " + ColorUtil.price(ScreenUtil.padLeft(ScreenUtil.formatPrice(item.getLineTotal()), 16)));
        }
        ScreenUtil.divider();
        String total = ScreenUtil.formatPrice(order.getTotal());
        System.out.println(" ".repeat(Math.max(0, Config.BANNER_WIDTH - 7 - total.length()))
                + ColorUtil.bold("TOTAL: ") + ColorUtil.price(total));
        System.out.println();
        System.out.println(ColorUtil.dim("Prices shown are the prices at the time of purchase."));
        System.out.println();
        InputUtil.pause();
    }
}
