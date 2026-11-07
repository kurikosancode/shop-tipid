package com.shop.ui;

import com.shop.config.Config;
import com.shop.model.CartLine;
import com.shop.model.Order;
import com.shop.model.Product;
import com.shop.model.User;
import com.shop.service.CartService;
import com.shop.service.CartService.CartException;
import com.shop.service.OrderService;
import com.shop.service.OrderService.CategorySpend;
import com.shop.service.OrderService.OrderException;
import com.shop.service.ProductService;

import java.util.List;
import java.util.Locale;

/**
 * Customer main menu: browse by category / label, search, product details, cart, Buy now,
 * checkout, purchase history, profile, and the entry points to the recommendation screens.
 *
 * Data structures used (all through the services): HashMap category / label indexes
 * (ProductService), HashMap-of-LinkedHashMap carts (CartService), ArrayList of orders with a
 * HashMap-by-user index (OrderService). The menu itself only holds references.
 * Complexity: every screen is O(rows shown) plus the service call it makes.
 */
public class CustomerMenu {

    private final ProductService products;
    private final CartService cart;
    private final OrderService orders;
    private final HistoryMenu history;
    private final RecommendationScreens recommendations;

    public CustomerMenu(ProductService products, CartService cart, OrderService orders,
                        HistoryMenu history, RecommendationScreens recommendations) {
        this.products = products;
        this.cart = cart;
        this.orders = orders;
        this.history = history;
        this.recommendations = recommendations;
    }

    /** Runs until the customer chooses "Log out". */
    public void run(User user) {
        while (true) {
            int cartItems = cart.getItemCount(user.getUsername());
            ScreenUtil.clear();
            ScreenUtil.banner("SHOP  |  " + user.getUsername());
            System.out.println();
            ScreenUtil.menuItem("1", "Browse by category / label");
            ScreenUtil.menuItem("2", "Search by name");
            ScreenUtil.menuItem("3", "Recommended for you");
            ScreenUtil.menuItem("4", "View cart" + (cartItems > 0
                    ? ColorUtil.dim(" (" + cartItems + (cartItems == 1 ? " item)" : " items)")) : ""));
            ScreenUtil.menuItem("5", "Purchase history");
            ScreenUtil.menuItem("6", "My profile");
            ScreenUtil.menuItem("7", "Recommendation settings");
            ScreenUtil.menuItem("8", "Bundle Builder");
            ScreenUtil.menuItem("0", "Log out");
            System.out.println();

            int choice = InputUtil.readInt("Choose an option:", 0, 8);
            switch (choice) {
                case 1 -> browse(user);
                case 2 -> search(user);
                case 3 -> recommendations.showRecommended(user);
                case 4 -> viewCart(user);
                case 5 -> history.run(user.getUsername(), "PURCHASE HISTORY");
                case 6 -> profile(user);
                case 7 -> recommendations.openSettings(user);
                case 8 -> recommendations.openBundleBuilder(user);
                default -> {
                    return;
                }
            }
        }
    }

    // ------------------------------------------------------------ browsing

    private void browse(User user) {
        while (true) {
            List<String> categories = products.getCategories();
            ScreenUtil.clear();
            ScreenUtil.banner("BROWSE BY CATEGORY");
            System.out.println();
            if (categories.isEmpty()) {
                ScreenUtil.error("There are no products available yet.");
                System.out.println();
                InputUtil.pause();
                return;
            }
            for (int i = 0; i < categories.size(); i++) {
                int count = products.getByCategory(categories.get(i)).size();
                ScreenUtil.menuItem(String.valueOf(i + 1),
                        categories.get(i) + ColorUtil.dim(" (" + count + " items)"));
            }
            ScreenUtil.menuItem("0", "Back");
            System.out.println();

            int choice = InputUtil.readInt("Choose a category:", 0, categories.size());
            if (choice == 0) {
                return;
            }
            browseLabels(user, categories.get(choice - 1));
        }
    }

    private void browseLabels(User user, String category) {
        while (true) {
            List<String> labels = products.getLabels(category);
            ScreenUtil.clear();
            ScreenUtil.banner(category.toUpperCase(Locale.ROOT));
            System.out.println();
            ScreenUtil.menuItem("1", "All " + category);
            for (int i = 0; i < labels.size(); i++) {
                int count = products.getByCategoryAndLabel(category, labels.get(i)).size();
                ScreenUtil.menuItem(String.valueOf(i + 2),
                        labels.get(i) + ColorUtil.dim(" (" + count + " items)"));
            }
            ScreenUtil.menuItem("0", "Back");
            System.out.println();

            int choice = InputUtil.readInt("Choose a label:", 0, labels.size() + 1);
            if (choice == 0) {
                return;
            }
            if (choice == 1) {
                showProductList(user, category.toUpperCase(Locale.ROOT), products.getByCategory(category));
            } else {
                String label = labels.get(choice - 2);
                showProductList(user, (category + " > " + label).toUpperCase(Locale.ROOT),
                        products.getByCategoryAndLabel(category, label));
            }
        }
    }

    private void search(User user) {
        ScreenUtil.clear();
        ScreenUtil.banner("SEARCH PRODUCTS");
        System.out.println();
        String text = InputUtil.readOptionalLine("Enter part of a product name (blank to go back):");
        if (text.isEmpty()) {
            return;
        }
        showProductList(user, "RESULTS: " + ScreenUtil.truncate(text, 40), products.search(text));
    }

    private void showProductList(User user, String title, List<Product> list) {
        while (true) {
            ScreenUtil.clear();
            ScreenUtil.banner(title);
            System.out.println();
            if (list.isEmpty()) {
                ScreenUtil.warning("No products found.");
                System.out.println();
                InputUtil.pause();
                return;
            }
            ProductView.printTable(list);
            System.out.println();
            int choice = InputUtil.readInt("Select a product number (0 to go back):", 0, list.size());
            if (choice == 0) {
                return;
            }
            boolean purchased = productDetails(user, list.get(choice - 1));
            if (purchased) {
                return;
            }
        }
    }

    // ------------------------------------------------------------ product details

    /** Returns true if the customer bought something with Buy now (so the caller can leave the list). */
    private boolean productDetails(User user, Product shown) {
        while (true) {
            Product product = products.getById(shown.getId());
            if (product == null) {
                return false;
            }
            ScreenUtil.clear();
            ScreenUtil.banner("PRODUCT DETAILS");
            System.out.println();
            ProductView.printDetails(product);
            System.out.println();

            if (!product.isInStock()) {
                ScreenUtil.warning("This item is currently out of stock.");
                System.out.println();
                InputUtil.pause();
                return false;
            }

            ScreenUtil.menuItem("1", "Add to cart");
            ScreenUtil.menuItem("2", "Buy now");
            ScreenUtil.menuItem("0", "Back");
            System.out.println();
            int choice = InputUtil.readInt("Choose an option:", 0, 2);
            if (choice == 0) {
                return false;
            }
            if (choice == 1) {
                addToCart(user, product);
            } else if (buyNow(user, product)) {
                return true;
            }
        }
    }

    private void addToCart(User user, Product product) {
        int max = cart.maxAddable(user.getUsername(), product);
        if (max <= 0) {
            ScreenUtil.warning("You already have the maximum available quantity of this item in your cart.");
            InputUtil.pause();
            return;
        }
        Integer quantity = InputUtil.readOptionalInt(
                "Quantity (1-" + max + ", blank to cancel):", 1, max);
        if (quantity == null) {
            return;
        }
        try {
            cart.add(user.getUsername(), product, quantity);
            ScreenUtil.success("Added " + quantity + " x " + product.getName() + " to your cart.");
        } catch (CartException e) {
            ScreenUtil.error(e.getMessage());
        }
        InputUtil.pause();
    }

    // ------------------------------------------------------------ Buy now

    /** One-item order that skips the cart. Returns true if the purchase was completed. */
    private boolean buyNow(User user, Product product) {
        int max = Math.min(product.getStock(), Config.MAX_CART_QTY_PER_ITEM);
        Integer quantity = InputUtil.readOptionalInt(
                "Quantity (1-" + max + ", blank to cancel):", 1, max);
        if (quantity == null) {
            return false;
        }
        CartLine line = new CartLine(product, quantity);

        ScreenUtil.clear();
        ScreenUtil.banner("BUY NOW");
        System.out.println();
        System.out.println(ColorUtil.dim(ScreenUtil.padRight("Item", 28) + "  " + ScreenUtil.padLeft("Qty", 4)
                + "  " + ScreenUtil.padLeft("Total", 16)));
        ScreenUtil.divider();
        System.out.println(ScreenUtil.padRight(product.getName(), 28)
                + "  " + ScreenUtil.padLeft(String.valueOf(quantity), 4)
                + "  " + ColorUtil.price(ScreenUtil.padLeft(ScreenUtil.formatPrice(line.lineTotal()), 16)));
        ScreenUtil.divider();
        System.out.println();

        if (!InputUtil.readYesNo("Confirm purchase of " + ScreenUtil.formatPrice(line.lineTotal()) + "?")) {
            ScreenUtil.info("Purchase cancelled.");
            InputUtil.pause();
            return false;
        }
        try {
            Order order = orders.placeOrder(user.getUsername(), List.of(line));
            completePurchase(user, order);
            return true;
        } catch (OrderException e) {
            ScreenUtil.error(e.getMessage());
            InputUtil.pause();
            return false;
        }
    }

    // ------------------------------------------------------------ cart

    private void viewCart(User user) {
        String username = user.getUsername();
        while (true) {
            List<CartLine> lines = cart.getLines(username);
            ScreenUtil.clear();
            ScreenUtil.banner("YOUR CART");
            System.out.println();
            if (lines.isEmpty()) {
                ScreenUtil.info("Your cart is empty.");
                System.out.println();
                InputUtil.pause();
                return;
            }

            System.out.println(ColorUtil.dim(ScreenUtil.padLeft("#", 3) + "  " + ScreenUtil.padRight("Item", 20)
                    + "  " + ScreenUtil.padLeft("Qty", 4) + "  " + ScreenUtil.padLeft("Price", 14)
                    + "  " + ScreenUtil.padLeft("Total", 15)));
            ScreenUtil.divider();
            boolean stockProblem = false;
            for (int i = 0; i < lines.size(); i++) {
                CartLine line = lines.get(i);
                if (line.product().getStock() < line.quantity()) {
                    stockProblem = true;
                }
                System.out.println(ColorUtil.menuNumber(ScreenUtil.padLeft(String.valueOf(i + 1), 3))
                        + "  " + ScreenUtil.padRight(line.product().getName(), 20)
                        + "  " + ScreenUtil.padLeft(String.valueOf(line.quantity()), 4)
                        + "  " + ColorUtil.price(ScreenUtil.padLeft(
                                ScreenUtil.formatPrice(line.product().getPrice()), 14))
                        + "  " + ColorUtil.price(ScreenUtil.padLeft(
                                ScreenUtil.formatPrice(line.lineTotal()), 15)));
            }
            ScreenUtil.divider();
            String total = ScreenUtil.formatPrice(cart.getTotal(username));
            System.out.println(" ".repeat(Math.max(0, Config.BANNER_WIDTH - 7 - total.length()))
                    + ColorUtil.bold("TOTAL: ") + ColorUtil.price(total));
            if (stockProblem) {
                System.out.println();
                ScreenUtil.warning("Some items are no longer available in the quantity you chose. "
                        + "Change or remove them before checkout.");
            }
            System.out.println();
            ScreenUtil.menuItem("1", "Checkout");
            ScreenUtil.menuItem("2", "Change quantity");
            ScreenUtil.menuItem("3", "Remove an item");
            ScreenUtil.menuItem("4", "Clear cart");
            ScreenUtil.menuItem("0", "Back");
            System.out.println();

            int choice = InputUtil.readInt("Choose an option:", 0, 4);
            switch (choice) {
                case 1 -> {
                    if (checkout(user, lines)) {
                        return;
                    }
                }
                case 2 -> changeQuantity(username, lines);
                case 3 -> removeItem(username, lines);
                case 4 -> {
                    if (InputUtil.readYesNo("Remove everything from your cart?")) {
                        cart.clear(username);
                        ScreenUtil.success("Cart cleared.");
                        InputUtil.pause();
                    }
                }
                default -> {
                    return;
                }
            }
        }
    }

    /** Fake checkout (no real payment). Returns true when the order was placed. */
    private boolean checkout(User user, List<CartLine> lines) {
        double total = cart.getTotal(user.getUsername());
        System.out.println();
        if (!InputUtil.readYesNo("Place this order for " + ScreenUtil.formatPrice(total) + "?")) {
            return false;
        }
        try {
            Order order = orders.placeOrder(user.getUsername(), lines);
            cart.clear(user.getUsername());
            completePurchase(user, order);
            return true;
        } catch (OrderException e) {
            ScreenUtil.error(e.getMessage());
            InputUtil.pause();
            return false;
        }
    }

    private void changeQuantity(String username, List<CartLine> lines) {
        Integer number = InputUtil.readOptionalInt("Item number to change (blank to cancel):", 1, lines.size());
        if (number == null) {
            return;
        }
        Product product = lines.get(number - 1).product();
        int max = Math.min(product.getStock(), Config.MAX_CART_QTY_PER_ITEM);
        if (max <= 0) {
            ScreenUtil.error(product.getName() + " is out of stock. Please remove it from your cart.");
            InputUtil.pause();
            return;
        }
        Integer quantity = InputUtil.readOptionalInt(
                "New quantity (1-" + max + ", blank to cancel):", 1, max);
        if (quantity == null) {
            return;
        }
        try {
            cart.setQuantity(username, product, quantity);
            ScreenUtil.success("Quantity updated.");
        } catch (CartException e) {
            ScreenUtil.error(e.getMessage());
        }
        InputUtil.pause();
    }

    private void removeItem(String username, List<CartLine> lines) {
        Integer number = InputUtil.readOptionalInt("Item number to remove (blank to cancel):", 1, lines.size());
        if (number == null) {
            return;
        }
        Product product = lines.get(number - 1).product();
        cart.remove(username, product.getId());
        ScreenUtil.success("Removed " + product.getName() + " from your cart.");
        InputUtil.pause();
    }

    /** Shared by cart checkout and Buy now: success message, receipt, then suggestions. */
    private void completePurchase(User user, Order order) {
        ScreenUtil.clear();
        ScreenUtil.banner("CHECKOUT");
        System.out.println();
        ScreenUtil.success("Payment successful! (demo checkout, no real payment was made)");
        ScreenUtil.info("Order " + order.getId() + " has been placed.");
        System.out.println();
        InputUtil.pause();
        history.showReceipt(order);
        recommendations.showAfterPurchase(user, order);
    }

    // ------------------------------------------------------------ profile

    private void profile(User user) {
        String username = user.getUsername();
        ScreenUtil.clear();
        ScreenUtil.banner("MY PROFILE");
        System.out.println();
        double totalSpent = orders.getTotalSpent(username);
        String favorite = orders.getFavoriteCategory(username);
        profileRow("Username", user.getUsername());
        profileRow("Member since", ScreenUtil.formatDate(user.getJoinDate()));
        profileRow("Total orders", String.valueOf(orders.getOrderCount(username)));
        profileRow("Total spent", ColorUtil.price(ScreenUtil.formatPrice(totalSpent)));
        profileRow("Favorite category", favorite == null ? "-" : favorite);
        System.out.println();

        ScreenUtil.section("Spending by category");
        List<CategorySpend> spending = orders.getSpendingByCategory(username);
        if (spending.isEmpty()) {
            ScreenUtil.info("No purchases yet.");
        } else {
            System.out.println(ColorUtil.dim(ScreenUtil.padRight("Category", 18) + "  "
                    + ScreenUtil.padLeft("Items", 6) + "  " + ScreenUtil.padLeft("Spent", 16)
                    + "  " + ScreenUtil.padLeft("Share", 7)));
            for (CategorySpend spend : spending) {
                double share = totalSpent > 0 ? spend.spent() * 100.0 / totalSpent : 0;
                System.out.println(ScreenUtil.padRight(spend.category(), 18)
                        + "  " + ScreenUtil.padLeft(String.valueOf(spend.units()), 6)
                        + "  " + ColorUtil.price(ScreenUtil.padLeft(ScreenUtil.formatPrice(spend.spent()), 16))
                        + "  " + ScreenUtil.padLeft(String.format(Locale.US, "%.1f%%", share), 7));
            }
        }
        System.out.println();
        InputUtil.pause();
    }

    private static void profileRow(String label, String value) {
        System.out.println(ColorUtil.dim(ScreenUtil.padRight(label + ":", 20)) + value);
    }
}