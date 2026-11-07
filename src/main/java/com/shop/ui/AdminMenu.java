package com.shop.ui;

import com.shop.config.Config;
import com.shop.model.Order;
import com.shop.model.Product;
import com.shop.model.User;
import com.shop.service.AuthService;
import com.shop.service.AuthService.AuthException;
import com.shop.service.CartService;
import com.shop.service.OrderService;
import com.shop.service.OrderService.CategorySpend;
import com.shop.service.OrderService.ProductSales;
import com.shop.service.OrderService.UserActivity;
import com.shop.service.ProductService;
import com.shop.service.ProductService.ProductException;
import com.shop.service.RecommendationService;

import java.util.List;

/**
 * Admin menu: manage products (add / edit / remove), view users and their profiles and order
 * history, remove user accounts, and the sales overview.
 *
 * Data structures used (all through the services): HashMap product indexes (ProductService),
 * HashMap user table (AuthService), ArrayList of orders with a HashMap-by-user index (OrderService).
 * The menu itself only holds references and prints ArrayLists.
 * Complexity: every screen is O(rows shown) plus the service call it makes. Product changes also
 * rebuild the similarity graph, which is O(n^2) for n products (fine for a catalog of this size).
 */
public class AdminMenu {

    private static final int PRODUCT_PAGE_SIZE = 12;
    private static final int TOP_N = 5;
    private static final double MAX_PRICE = 10_000_000;
    private static final int MAX_STOCK = 1_000_000;
    /** Category and label names: 1-30 characters, starting with a letter or number. */
    private static final String NAME_PATTERN = "[A-Za-z0-9][A-Za-z0-9 &'-]{0,29}";

    private final AuthService auth;
    private final ProductService products;
    private final OrderService orders;
    private final RecommendationService recommendations;
    private final CartService cart;
    private final HistoryMenu history;

    public AdminMenu(AuthService auth, ProductService products, OrderService orders,
                     RecommendationService recommendations, CartService cart, HistoryMenu history) {
        this.auth = auth;
        this.products = products;
        this.orders = orders;
        this.recommendations = recommendations;
        this.cart = cart;
        this.history = history;
    }

    /** Runs until the admin chooses "Log out". */
    public void run(User admin) {
        while (true) {
            ScreenUtil.clear();
            ScreenUtil.banner("ADMIN  |  " + admin.getUsername());
            System.out.println();
            ScreenUtil.menuItem("1", "Manage products");
            ScreenUtil.menuItem("2", "View users (profiles and order history)");
            ScreenUtil.menuItem("3", "Remove a user account");
            ScreenUtil.menuItem("4", "Sales overview");
            ScreenUtil.menuItem("0", "Log out");
            System.out.println();

            int choice = InputUtil.readInt("Choose an option:", 0, 4);
            switch (choice) {
                case 1 -> manageProducts();
                case 2 -> viewUsers();
                case 3 -> removeUser(admin);
                case 4 -> salesOverview();
                default -> {
                    return;
                }
            }
        }
    }

    // ================================================================ products

    private void manageProducts() {
        while (true) {
            ScreenUtil.clear();
            ScreenUtil.banner("MANAGE PRODUCTS");
            System.out.println();
            System.out.println(ColorUtil.dim(products.size() + " products in "
                    + products.getCategories().size() + " categories"));
            System.out.println();
            ScreenUtil.menuItem("1", "List all products");
            ScreenUtil.menuItem("2", "Add a product");
            ScreenUtil.menuItem("3", "Edit a product");
            ScreenUtil.menuItem("4", "Remove a product");
            ScreenUtil.menuItem("0", "Back");
            System.out.println();

            int choice = InputUtil.readInt("Choose an option:", 0, 4);
            switch (choice) {
                case 1 -> listProducts();
                case 2 -> addProduct();
                case 3 -> editProduct();
                case 4 -> removeProduct();
                default -> {
                    return;
                }
            }
        }
    }

    /** Paged list of every product (12 per page). */
    private void listProducts() {
        int page = 0;
        while (true) {
            List<Product> list = products.getAll();
            ScreenUtil.clear();
            ScreenUtil.banner("ALL PRODUCTS");
            System.out.println();
            if (list.isEmpty()) {
                ScreenUtil.info("There are no products yet.");
                System.out.println();
                InputUtil.pause();
                return;
            }
            int pages = (list.size() + PRODUCT_PAGE_SIZE - 1) / PRODUCT_PAGE_SIZE;
            page = Math.max(0, Math.min(page, pages - 1));
            int start = page * PRODUCT_PAGE_SIZE;
            int end = Math.min(start + PRODUCT_PAGE_SIZE, list.size());

            printAdminProductTable(list.subList(start, end));
            System.out.println();
            System.out.println(ColorUtil.dim("Page " + (page + 1) + " of " + pages
                    + "  (" + list.size() + " products)"));
            if (pages == 1) {
                System.out.println();
                InputUtil.pause();
                return;
            }
            System.out.println(ColorUtil.dim("N = next page, P = previous page, 0 = back."));
            String input = InputUtil.readOptionalLine("Your choice:").toLowerCase();
            if (input.equals("0")) {
                return;
            } else if (input.equals("n")) {
                page++;
            } else if (input.equals("p")) {
                page--;
            } else if (!input.isEmpty()) {
                ScreenUtil.error("Invalid choice.");
                InputUtil.pause();
            }
        }
    }

    private void printAdminProductTable(List<Product> list) {
        System.out.println(ColorUtil.dim(ScreenUtil.padRight("ID", 5) + "  " + ScreenUtil.padRight("Name", 17)
                + "  " + ScreenUtil.padRight("Label", 16) + "  " + ScreenUtil.padLeft("Price", 13)
                + "  " + ScreenUtil.padLeft("Stock", 5)));
        ScreenUtil.divider();
        for (Product p : list) {
            String stockText = ScreenUtil.padLeft(p.isInStock() ? String.valueOf(p.getStock()) : "OUT", 5);
            System.out.println(ScreenUtil.padRight(p.getId(), 5)
                    + "  " + ScreenUtil.padRight(p.getName(), 17)
                    + "  " + ScreenUtil.padRight(p.getLabel(), 16)
                    + "  " + ColorUtil.price(ScreenUtil.padLeft(ScreenUtil.formatPrice(p.getPrice()), 13))
                    + "  " + (p.isInStock() ? stockText : ColorUtil.error(stockText)));
        }
    }

    /** Asks for a product ID (L shows the list). Returns null if the admin cancels. */
    private Product selectProduct(String title, String action) {
        while (true) {
            ScreenUtil.clear();
            ScreenUtil.banner(title);
            System.out.println();
            if (products.size() == 0) {
                ScreenUtil.info("There are no products yet.");
                System.out.println();
                InputUtil.pause();
                return null;
            }
            System.out.println(ColorUtil.dim("Type L to see the product list and their IDs."));
            System.out.println();
            String id = InputUtil.readOptionalLine("Product ID to " + action + " (blank to cancel):");
            if (id.isEmpty()) {
                return null;
            }
            if (id.equalsIgnoreCase("l")) {
                listProducts();
                continue;
            }
            Product product = products.getById(id);
            if (product != null) {
                return product;
            }
            ScreenUtil.error("No product with ID \"" + id + "\".");
            InputUtil.pause();
        }
    }

    private void addProduct() {
        ScreenUtil.clear();
        ScreenUtil.banner("ADD PRODUCT");
        System.out.println();
        System.out.println(ColorUtil.dim("Leave the name blank to cancel."));
        System.out.println();

        String name = readName("Product name:");
        if (name.isEmpty()) {
            return;
        }
        String category = chooseCategory();
        if (category == null) {
            return;
        }
        String label = chooseLabel(category);
        if (label == null) {
            return;
        }
        System.out.println();
        double price = InputUtil.readMoney("Price in PHP:", 0.01, MAX_PRICE);
        double rating = InputUtil.readDouble("Rating (0-5):", 0, 5);
        int stock = InputUtil.readInt("Stock quantity:", 0, MAX_STOCK);

        try {
            Product product = products.addProduct(name, price, category, label, rating, stock);
            recommendations.rebuildGraph();
            System.out.println();
            ScreenUtil.success("Added " + product.getId() + " - " + product.getName() + ".");
        } catch (ProductException e) {
            System.out.println();
            ScreenUtil.error(e.getMessage());
        }
        InputUtil.pause();
    }

    private void editProduct() {
        Product product = selectProduct("EDIT PRODUCT", "edit");
        if (product == null) {
            return;
        }
        ScreenUtil.clear();
        ScreenUtil.banner("EDIT PRODUCT");
        System.out.println();
        ProductView.printDetails(product);
        System.out.println();
        System.out.println(ColorUtil.dim("Press Enter on any question to keep the current value."));
        System.out.println();

        String name = readName("New name:");
        Double price = InputUtil.readOptionalDouble("New price in PHP:", 0.01, MAX_PRICE);
        String category = product.getCategory();
        String label = product.getLabel();
        if (InputUtil.readYesNo("Change category / label?")) {
            ScreenUtil.clear();
            ScreenUtil.banner("CHANGE CATEGORY / LABEL");
            String newCategory = chooseCategory();
            if (newCategory != null) {
                String newLabel = chooseLabel(newCategory);
                if (newLabel != null) {
                    category = newCategory;
                    label = newLabel;
                }
            }
            System.out.println();
        }
        Double rating = InputUtil.readOptionalDouble("New rating (0-5):", 0, 5);
        Integer stock = InputUtil.readOptionalInt("New stock quantity:", 0, MAX_STOCK);

        try {
            products.updateProduct(product.getId(),
                    name.isEmpty() ? product.getName() : name,
                    price != null ? price : product.getPrice(),
                    category, label,
                    rating != null ? rating : product.getRating(),
                    stock != null ? stock : product.getStock());
            recommendations.rebuildGraph();
            System.out.println();
            ScreenUtil.success("Product updated.");
        } catch (ProductException e) {
            System.out.println();
            ScreenUtil.error(e.getMessage());
        }
        InputUtil.pause();
    }

    private void removeProduct() {
        Product product = selectProduct("REMOVE PRODUCT", "remove");
        if (product == null) {
            return;
        }
        ScreenUtil.clear();
        ScreenUtil.banner("REMOVE PRODUCT");
        System.out.println();
        ProductView.printDetails(product);
        System.out.println();
        ScreenUtil.warning("Past orders keep their receipts. Carts holding this item will drop it.");
        System.out.println();
        if (!InputUtil.readYesNo("Remove \"" + product.getName() + "\"?")) {
            ScreenUtil.info("Cancelled. Nothing was removed.");
            InputUtil.pause();
            return;
        }
        try {
            products.removeProduct(product.getId());
            recommendations.rebuildGraph();
            ScreenUtil.success("Product removed.");
        } catch (ProductException e) {
            ScreenUtil.error(e.getMessage());
        }
        InputUtil.pause();
    }

    // ---- helpers for choosing category / label / name

    /** Product name; returns "" when the admin just presses Enter. Re-prompts while too long. */
    private String readName(String prompt) {
        while (true) {
            String name = InputUtil.readOptionalLine(prompt);
            if (name.length() <= Config.PRODUCT_NAME_MAX_LENGTH) {
                return name;
            }
            ScreenUtil.error("The name is too long (max " + Config.PRODUCT_NAME_MAX_LENGTH + " characters).");
        }
    }

    /** Existing category (numbered list) or a new one. Returns null if cancelled. */
    private String chooseCategory() {
        List<String> categories = products.getCategories();
        System.out.println();
        ScreenUtil.section("Category");
        for (int i = 0; i < categories.size(); i++) {
            ScreenUtil.menuItem(String.valueOf(i + 1), categories.get(i));
        }
        int newOption = categories.size() + 1;
        ScreenUtil.menuItem(String.valueOf(newOption), "New category");
        ScreenUtil.menuItem("0", "Cancel");
        System.out.println();

        int choice = InputUtil.readInt("Choose a category:", 0, newOption);
        if (choice == 0) {
            return null;
        }
        if (choice == newOption) {
            return readCategoryOrLabelName("New category name (blank to cancel):");
        }
        return categories.get(choice - 1);
    }

    /** Existing label of the category (numbered list) or a new one. Returns null if cancelled. */
    private String chooseLabel(String category) {
        List<String> labels = products.getLabels(category);
        if (labels.isEmpty()) {
            return readCategoryOrLabelName("New label name (blank to cancel):");
        }
        System.out.println();
        ScreenUtil.section("Label in " + category);
        for (int i = 0; i < labels.size(); i++) {
            ScreenUtil.menuItem(String.valueOf(i + 1), labels.get(i));
        }
        int newOption = labels.size() + 1;
        ScreenUtil.menuItem(String.valueOf(newOption), "New label");
        ScreenUtil.menuItem("0", "Cancel");
        System.out.println();

        int choice = InputUtil.readInt("Choose a label:", 0, newOption);
        if (choice == 0) {
            return null;
        }
        if (choice == newOption) {
            return readCategoryOrLabelName("New label name (blank to cancel):");
        }
        return labels.get(choice - 1);
    }

    private String readCategoryOrLabelName(String prompt) {
        while (true) {
            String text = InputUtil.readOptionalLine(prompt);
            if (text.isEmpty()) {
                return null;
            }
            if (text.matches(NAME_PATTERN)) {
                return text;
            }
            ScreenUtil.error("Use 1-30 letters, numbers, spaces, & ' or - (start with a letter or number).");
        }
    }

    // ================================================================ users

    private void viewUsers() {
        while (true) {
            List<User> users = auth.getAllUsers();
            ScreenUtil.clear();
            ScreenUtil.banner("USERS");
            System.out.println();
            printUserTable(users);
            System.out.println();
            int choice = InputUtil.readInt("Select a user number to view their profile (0 = back):",
                    0, users.size());
            if (choice == 0) {
                return;
            }
            userDetail(users.get(choice - 1));
        }
    }

    private void printUserTable(List<User> users) {
        System.out.println(ColorUtil.dim(ScreenUtil.padLeft("#", 3) + "  " + ScreenUtil.padRight("Username", 20)
                + "  " + ScreenUtil.padRight("Role", 9) + "  " + ScreenUtil.padRight("Joined", 10)
                + "  " + ScreenUtil.padLeft("Orders", 6)));
        ScreenUtil.divider();
        for (int i = 0; i < users.size(); i++) {
            User u = users.get(i);
            System.out.println(ColorUtil.menuNumber(ScreenUtil.padLeft(String.valueOf(i + 1), 3))
                    + "  " + ScreenUtil.padRight(u.getUsername(), 20)
                    + "  " + ScreenUtil.padRight(u.getRole().name(), 9)
                    + "  " + ScreenUtil.padRight(ScreenUtil.formatDate(u.getJoinDate()), 10)
                    + "  " + ScreenUtil.padLeft(String.valueOf(orders.getOrderCount(u.getUsername())), 6));
        }
    }

    /** One user's profile (same facts as the customer's own profile) plus access to their order history. */
    private void userDetail(User target) {
        String name = target.getUsername();
        while (true) {
            ScreenUtil.clear();
            ScreenUtil.banner("USER PROFILE");
            System.out.println();
            double spent = orders.getTotalSpent(name);
            String favorite = orders.getFavoriteCategory(name);
            detailRow("Username", target.getUsername());
            detailRow("Role", target.getRole().name());
            detailRow("Member since", ScreenUtil.formatDate(target.getJoinDate()));
            detailRow("Total orders", String.valueOf(orders.getOrderCount(name)));
            detailRow("Total spent", ColorUtil.price(ScreenUtil.formatPrice(spent)));
            detailRow("Favorite category", favorite == null ? "-" : favorite);
            System.out.println();

            ScreenUtil.section("Spending by category");
            List<CategorySpend> spending = orders.getSpendingByCategory(name);
            if (spending.isEmpty()) {
                ScreenUtil.info("No purchases yet.");
            } else {
                System.out.println(ColorUtil.dim(ScreenUtil.padRight("Category", 18) + "  "
                        + ScreenUtil.padLeft("Items", 6) + "  " + ScreenUtil.padLeft("Spent", 16)));
                for (CategorySpend s : spending) {
                    System.out.println(ScreenUtil.padRight(s.category(), 18)
                            + "  " + ScreenUtil.padLeft(String.valueOf(s.units()), 6)
                            + "  " + ColorUtil.price(ScreenUtil.padLeft(ScreenUtil.formatPrice(s.spent()), 16)));
                }
            }
            System.out.println();
            ScreenUtil.menuItem("1", "View order history");
            ScreenUtil.menuItem("0", "Back");
            System.out.println();

            int choice = InputUtil.readInt("Choose an option:", 0, 1);
            if (choice == 0) {
                return;
            }
            history.run(name, "ORDERS: " + name);
        }
    }

    private void removeUser(User admin) {
        List<User> users = auth.getAllUsers();
        ScreenUtil.clear();
        ScreenUtil.banner("REMOVE USER");
        System.out.println();
        printUserTable(users);
        System.out.println();
        Integer choice = InputUtil.readOptionalInt("Select a user number to remove (blank to cancel):",
                1, users.size());
        if (choice == null) {
            return;
        }
        User target = users.get(choice - 1);
        System.out.println();

        if (target.getUsername().equalsIgnoreCase(admin.getUsername())) {
            ScreenUtil.error("You cannot remove your own account.");
            InputUtil.pause();
            return;
        }
        if (target.isAdmin() && auth.countAdmins() <= 1) {
            ScreenUtil.error("You cannot remove the last admin account.");
            InputUtil.pause();
            return;
        }

        ScreenUtil.warning("This permanently deletes the account \"" + target.getUsername() + "\".");
        ScreenUtil.info("Their past orders stay in the shop's sales records.");
        System.out.println();
        if (!InputUtil.readYesNo("Remove user " + target.getUsername() + "?")) {
            ScreenUtil.info("Cancelled. Nothing was removed.");
            InputUtil.pause();
            return;
        }
        try {
            auth.removeUser(target.getUsername(), admin.getUsername());
            cart.clear(target.getUsername());
            ScreenUtil.success("User removed.");
        } catch (AuthException e) {
            ScreenUtil.error(e.getMessage());
        }
        InputUtil.pause();
    }

    // ================================================================ sales

    private void salesOverview() {
        ScreenUtil.clear();
        ScreenUtil.banner("SALES OVERVIEW");
        System.out.println();

        List<Order> all = orders.getAllOrders();
        int units = 0;
        for (Order order : all) {
            units += order.getItemCount();
        }
        detailRow("Total revenue", ColorUtil.price(ScreenUtil.formatPrice(orders.getTotalRevenue())));
        detailRow("Orders placed", String.valueOf(all.size()));
        detailRow("Units sold", String.valueOf(units));
        System.out.println();

        ScreenUtil.section("Best-selling products (top " + TOP_N + ")");
        List<ProductSales> sales = orders.getSalesByProduct();
        if (sales.isEmpty()) {
            ScreenUtil.info("No sales yet.");
        } else {
            System.out.println(ColorUtil.dim(ScreenUtil.padLeft("#", 3) + "  " + ScreenUtil.padRight("Product", 28)
                    + "  " + ScreenUtil.padLeft("Units", 6) + "  " + ScreenUtil.padLeft("Revenue", 17)));
            for (int i = 0; i < Math.min(TOP_N, sales.size()); i++) {
                ProductSales s = sales.get(i);
                System.out.println(ColorUtil.menuNumber(ScreenUtil.padLeft(String.valueOf(i + 1), 3))
                        + "  " + ScreenUtil.padRight(s.name(), 28)
                        + "  " + ScreenUtil.padLeft(String.valueOf(s.units()), 6)
                        + "  " + ColorUtil.price(ScreenUtil.padLeft(ScreenUtil.formatPrice(s.revenue()), 17)));
            }
        }
        System.out.println();

        ScreenUtil.section("Most active users (top " + TOP_N + ")");
        List<UserActivity> activity = orders.getUserActivity();
        if (activity.isEmpty()) {
            ScreenUtil.info("No orders yet.");
        } else {
            System.out.println(ColorUtil.dim(ScreenUtil.padLeft("#", 3) + "  " + ScreenUtil.padRight("Username", 22)
                    + "  " + ScreenUtil.padLeft("Orders", 6) + "  " + ScreenUtil.padLeft("Spent", 17)));
            for (int i = 0; i < Math.min(TOP_N, activity.size()); i++) {
                UserActivity a = activity.get(i);
                System.out.println(ColorUtil.menuNumber(ScreenUtil.padLeft(String.valueOf(i + 1), 3))
                        + "  " + ScreenUtil.padRight(a.username(), 22)
                        + "  " + ScreenUtil.padLeft(String.valueOf(a.orders()), 6)
                        + "  " + ColorUtil.price(ScreenUtil.padLeft(ScreenUtil.formatPrice(a.spent()), 17)));
            }
        }
        System.out.println();
        InputUtil.pause();
    }

    private static void detailRow(String label, String value) {
        System.out.println(ColorUtil.dim(ScreenUtil.padRight(label + ":", 20)) + value);
    }
}