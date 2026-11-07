package com.shop.ui;

import com.shop.config.Config;
import com.shop.model.Order;
import com.shop.model.OrderItem;
import com.shop.model.Product;
import com.shop.model.RankBy;
import com.shop.model.RecommendationSettings;
import com.shop.model.User;
import com.shop.service.AuthService;
import com.shop.service.BundleService;
import com.shop.service.BundleService.Bundle;
import com.shop.service.CartService;
import com.shop.service.CartService.CartException;
import com.shop.service.ProductService;
import com.shop.service.RecommendationService;
import com.shop.service.RecommendationService.Recommendation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Screens for recommendations: "Recommended for you", suggestions after a purchase,
 * recommendation settings (saved in users.json), and the Bundle Builder.
 * Data structures: none of its own; it displays the ArrayLists / records returned by
 * RecommendationService (heap-ranked) and BundleService (backtracking).
 * Complexity: every screen is O(rows shown) plus the service call it makes.
 */
public class RecommendationMenu implements RecommendationScreens {

    private static final double MAX_MONEY = 100_000_000;

    private final RecommendationService recommendations;
    private final BundleService bundles;
    private final CartService cart;
    private final AuthService auth;
    private final ProductService products;

    public RecommendationMenu(RecommendationService recommendations, BundleService bundles,
                              CartService cart, AuthService auth, ProductService products) {
        this.recommendations = recommendations;
        this.bundles = bundles;
        this.cart = cart;
        this.auth = auth;
        this.products = products;
    }

    // ------------------------------------------------------------ recommended screens

    @Override
    public void showRecommended(User user) {
        List<Recommendation> list = recommendations.recommendForUser(user, Config.DEFAULT_TOP_N);
        if (list.isEmpty()) {
            ScreenUtil.clear();
            ScreenUtil.banner("RECOMMENDED FOR YOU");
            System.out.println();
            ScreenUtil.info("No recommendations are available right now.");
            if (user.getSettings().hasPriceLimit()) {
                ScreenUtil.warning("Your price limit may be hiding items. Change it in Recommendation settings.");
            }
            System.out.println();
            InputUtil.pause();
            return;
        }
        boolean hasHistory = !recommendations.getPurchasedIds(user.getUsername()).isEmpty();
        String subtitle = hasHistory ? "Picked from what you have bought before."
                : "Popular items to get you started. Buy something to personalize this list.";
        browseList(user, "RECOMMENDED FOR YOU", subtitle, list);
    }

    @Override
    public void showAfterPurchase(User user, Order order) {
        List<Recommendation> list = recommendations.recommendAfterPurchase(user, order, Config.DEFAULT_TOP_N);
        if (list.isEmpty()) {
            return;
        }
        List<String> names = new ArrayList<>();
        for (OrderItem item : order.getItems()) {
            names.add(item.getProductName());
        }
        String subtitle = "Because you bought: " + ScreenUtil.truncate(String.join(", ", names), 40);
        browseList(user, "YOU MAY ALSO LIKE", subtitle, list);
    }

    private void browseList(User user, String title, String subtitle, List<Recommendation> list) {
        while (true) {
            RecommendationSettings settings = user.getSettings();
            ScreenUtil.clear();
            ScreenUtil.banner(title);
            System.out.println();
            if (subtitle != null) {
                System.out.println(subtitle);
            }
            System.out.println(ColorUtil.dim("Ranked by: " + settings.getRankBy().getLabel()
                    + (settings.hasPriceLimit() ? "  |  Price limit: " + describePriceLimit(settings) : "")));
            System.out.println();
            for (int i = 0; i < list.size(); i++) {
                printRecommendation(i + 1, list.get(i));
            }
            int choice = InputUtil.readInt("Enter a number to add that item to your cart (0 = back):",
                    0, list.size());
            if (choice == 0) {
                return;
            }
            addToCart(user, list.get(choice - 1).product());
        }
    }

    private void printRecommendation(int index, Recommendation r) {
        Product p = r.product();
        System.out.println(ColorUtil.menuNumber(ScreenUtil.padLeft(String.valueOf(index), 2)) + "  "
                + ScreenUtil.padRight(p.getName(), 38) + "  "
                + ColorUtil.price(ScreenUtil.padLeft(ScreenUtil.formatPrice(p.getPrice()), 18)));
        System.out.println("    " + ColorUtil.dim(p.getCategory() + " > " + p.getLabel()));
        System.out.println("    " + ColorUtil.why(r.reason()));
        System.out.println();
    }

    private void addToCart(User user, Product product) {
        int max = cart.maxAddable(user.getUsername(), product);
        if (max <= 0) {
            ScreenUtil.warning("You already have the maximum available quantity of this item in your cart.");
            InputUtil.pause();
            return;
        }
        Integer quantity = InputUtil.readOptionalInt("Quantity (1-" + max + ", blank to cancel):", 1, max);
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

    // ------------------------------------------------------------ settings

    @Override
    public void openSettings(User user) {
        RecommendationSettings s = user.getSettings();
        while (true) {
            ScreenUtil.clear();
            ScreenUtil.banner("RECOMMENDATION SETTINGS");
            System.out.println();
            ScreenUtil.section("Current settings");
            settingRow("Ranking by", s.getRankBy().getLabel());
            settingRow("Similarity weight", String.format(Locale.US, "%.2f", s.getWSimilarity()));
            settingRow("Popularity weight", String.format(Locale.US, "%.2f", s.getWPopularity()));
            settingRow("Rating weight", String.format(Locale.US, "%.2f", s.getWRating()));
            settingRow("Price-fit weight", String.format(Locale.US, "%.2f", s.getWPriceFit()));
            settingRow("Price limit", describePriceLimit(s));
            System.out.println();
            ScreenUtil.menuItem("1", "Choose ranking parameter");
            ScreenUtil.menuItem("2", "Adjust weights");
            ScreenUtil.menuItem("3", "Set maximum price");
            ScreenUtil.menuItem("4", "Set minimum price");
            ScreenUtil.menuItem("5", "Remove price limit");
            ScreenUtil.menuItem("6", "Reset all to defaults");
            ScreenUtil.menuItem("0", "Back");
            System.out.println();

            int choice = InputUtil.readInt("Choose an option:", 0, 6);
            switch (choice) {
                case 1 -> chooseRanking(s);
                case 2 -> adjustWeights(s);
                case 3 -> setMaxPrice(s);
                case 4 -> setMinPrice(s);
                case 5 -> {
                    s.clearPriceLimits();
                    persist("Price limit removed.");
                }
                case 6 -> {
                    if (InputUtil.readYesNo("Reset ranking, weights and price limit to the defaults?")) {
                        s.setRankBy(RankBy.BEST_MATCH);
                        s.resetWeights();
                        s.clearPriceLimits();
                        persist("Settings reset to defaults.");
                    }
                }
                default -> {
                    return;
                }
            }
        }
    }

    private void chooseRanking(RecommendationSettings s) {
        RankBy[] values = RankBy.values();
        ScreenUtil.clear();
        ScreenUtil.banner("RANKING PARAMETER");
        System.out.println();
        for (int i = 0; i < values.length; i++) {
            ScreenUtil.menuItem(String.valueOf(i + 1), values[i].getLabel()
                    + (values[i] == s.getRankBy() ? ColorUtil.dim(" (current)") : ""));
        }
        ScreenUtil.menuItem("0", "Back");
        System.out.println();
        int choice = InputUtil.readInt("Choose a ranking parameter:", 0, values.length);
        if (choice > 0) {
            s.setRankBy(values[choice - 1]);
            persist("Ranking set to: " + values[choice - 1].getLabel());
        }
    }

    private void adjustWeights(RecommendationSettings s) {
        ScreenUtil.clear();
        ScreenUtil.banner("ADJUST WEIGHTS");
        System.out.println();
        System.out.println("Each weight is from 0 to 1. A higher weight means that factor matters more");
        System.out.println("in the \"Best match\" score. Press Enter to keep the current value.");
        System.out.println();
        Double sim = InputUtil.readOptionalDouble(String.format(Locale.US,
                "Similarity (now %.2f):", s.getWSimilarity()), 0, 1);
        Double pop = InputUtil.readOptionalDouble(String.format(Locale.US,
                "Popularity (now %.2f):", s.getWPopularity()), 0, 1);
        Double rat = InputUtil.readOptionalDouble(String.format(Locale.US,
                "Rating (now %.2f):", s.getWRating()), 0, 1);
        Double fit = InputUtil.readOptionalDouble(String.format(Locale.US,
                "Price fit (now %.2f):", s.getWPriceFit()), 0, 1);

        double newSim = sim != null ? sim : s.getWSimilarity();
        double newPop = pop != null ? pop : s.getWPopularity();
        double newRat = rat != null ? rat : s.getWRating();
        double newFit = fit != null ? fit : s.getWPriceFit();
        if (newSim + newPop + newRat + newFit <= 0) {
            ScreenUtil.error("At least one weight must be above 0. Nothing was changed.");
            InputUtil.pause();
            return;
        }
        s.setWSimilarity(newSim);
        s.setWPopularity(newPop);
        s.setWRating(newRat);
        s.setWPriceFit(newFit);
        persist("Weights updated.");
    }

    private void setMaxPrice(RecommendationSettings s) {
        Double value = InputUtil.readOptionalDouble("Maximum price in PHP (blank to cancel):", 1, MAX_MONEY);
        if (value == null) {
            return;
        }
        double max = round2(value);
        if (s.getMinPrice() != null && max < s.getMinPrice()) {
            ScreenUtil.error("The maximum cannot be lower than your minimum ("
                    + ScreenUtil.formatPrice(s.getMinPrice()) + ").");
            InputUtil.pause();
            return;
        }
        s.setMaxPrice(max);
        persist("Maximum price set to " + ScreenUtil.formatPrice(max) + ".");
    }

    private void setMinPrice(RecommendationSettings s) {
        Double value = InputUtil.readOptionalDouble("Minimum price in PHP (blank to cancel):", 0, MAX_MONEY);
        if (value == null) {
            return;
        }
        double min = round2(value);
        if (s.getMaxPrice() != null && min > s.getMaxPrice()) {
            ScreenUtil.error("The minimum cannot be higher than your maximum ("
                    + ScreenUtil.formatPrice(s.getMaxPrice()) + ").");
            InputUtil.pause();
            return;
        }
        s.setMinPrice(min);
        persist("Minimum price set to " + ScreenUtil.formatPrice(min) + ".");
    }

    /** Saves users.json (settings live inside each user) and reports the result. */
    private void persist(String successMessage) {
        if (auth.saveUsers()) {
            ScreenUtil.success(successMessage);
        } else {
            ScreenUtil.error("Could not save your settings to users.json. They apply until you log out.");
        }
        InputUtil.pause();
    }

    private static void settingRow(String label, String value) {
        System.out.println(ColorUtil.dim(ScreenUtil.padRight(label + ":", 22)) + value);
    }

    private static String describePriceLimit(RecommendationSettings s) {
        if (!s.hasPriceLimit()) {
            return "None";
        }
        if (s.getMinPrice() != null && s.getMaxPrice() != null) {
            return ScreenUtil.formatPrice(s.getMinPrice()) + " to " + ScreenUtil.formatPrice(s.getMaxPrice());
        }
        if (s.getMaxPrice() != null) {
            return "Up to " + ScreenUtil.formatPrice(s.getMaxPrice());
        }
        return "From " + ScreenUtil.formatPrice(s.getMinPrice());
    }

    // ------------------------------------------------------------ bundle builder

    @Override
    public void openBundleBuilder(User user) {
        List<String> allCategories = products.getCategories();
        ScreenUtil.clear();
        ScreenUtil.banner("BUNDLE BUILDER");
        System.out.println();
        if (allCategories.isEmpty()) {
            ScreenUtil.error("There are no products available yet.");
            System.out.println();
            InputUtil.pause();
            return;
        }
        System.out.println("Enter a budget and we find the best-scoring combination of items");
        System.out.println("(at most one item per category) that stays within it.");
        System.out.println(ColorUtil.dim("Items you already bought are left out. Leave the budget blank to go back."));
        System.out.println();

        Double budgetInput = InputUtil.readOptionalDouble("Budget in PHP:", 1, MAX_MONEY);
        if (budgetInput == null) {
            return;
        }
        double budget = round2(budgetInput);
        int maxItems = InputUtil.readInt("Maximum number of items (1-" + Config.BUNDLE_MAX_ITEMS + "):",
                1, Config.BUNDLE_MAX_ITEMS);

        System.out.println();
        for (int i = 0; i < allCategories.size(); i++) {
            ScreenUtil.menuItem(String.valueOf(i + 1), allCategories.get(i));
        }
        System.out.println();
        Set<String> categories = chooseCategories(allCategories);

        String username = user.getUsername();
        Bundle bundle = bundles.findBestBundle(budget, maxItems, categories,
                recommendations.getPurchasedIds(username), recommendations.getSimilarityMap(username));
        showBundle(user, bundle, budget);
    }

    /** Returns the chosen category names, or an empty set meaning "all categories". */
    private Set<String> chooseCategories(List<String> all) {
        while (true) {
            String line = InputUtil.readOptionalLine("Categories (numbers like 1,3,5 - blank for all):");
            if (line.isEmpty()) {
                return new LinkedHashSet<>();
            }
            Set<String> chosen = new LinkedHashSet<>();
            boolean valid = true;
            for (String part : line.split(",")) {
                String text = part.trim();
                if (!text.matches("\\d{1,3}")) {
                    valid = false;
                    break;
                }
                int number = Integer.parseInt(text);
                if (number < 1 || number > all.size()) {
                    valid = false;
                    break;
                }
                chosen.add(all.get(number - 1));
            }
            if (valid && !chosen.isEmpty()) {
                return chosen;
            }
            ScreenUtil.error("Please enter category numbers from 1 to " + all.size() + ", separated by commas.");
        }
    }

    private void showBundle(User user, Bundle bundle, double budget) {
        ScreenUtil.clear();
        ScreenUtil.banner("YOUR BUNDLE");
        System.out.println();
        if (bundle.isEmpty()) {
            ScreenUtil.warning("No bundle fits within " + ScreenUtil.formatPrice(budget) + ".");
            ScreenUtil.info("Try a higher budget, more categories, or a larger item limit.");
            System.out.println();
            InputUtil.pause();
            return;
        }

        System.out.println(ColorUtil.dim(ScreenUtil.padRight("Item", 30) + "  "
                + ScreenUtil.padRight("Category", 12) + "  " + ScreenUtil.padLeft("Price", 16)));
        ScreenUtil.divider();
        for (Product p : bundle.items()) {
            System.out.println(ScreenUtil.padRight(p.getName(), 30) + "  "
                    + ScreenUtil.padRight(p.getCategory(), 12) + "  "
                    + ColorUtil.price(ScreenUtil.padLeft(ScreenUtil.formatPrice(p.getPrice()), 16)));
        }
        ScreenUtil.divider();
        System.out.println(ColorUtil.bold("Total price:   ") + ColorUtil.price(ScreenUtil.formatPrice(bundle.totalPrice())));
        System.out.println(ColorUtil.dim("Budget:        ") + ScreenUtil.formatPrice(budget));
        System.out.println(ColorUtil.dim("Left over:     ")
                + ScreenUtil.formatPrice(Math.max(0, budget - bundle.totalPrice())));
        System.out.println(ColorUtil.dim("Match score:   ") + String.format(Locale.US, "%.2f", bundle.totalScore()));
        System.out.println();
        System.out.println(ColorUtil.dim("Backtracking explored " + bundle.nodesExplored() + " branches, pruned "
                + bundle.prunedOverBudget() + " over-budget items and " + bundle.prunedByBound()
                + " low-score branches."));
        System.out.println();

        if (InputUtil.readYesNo("Add all of these items to your cart?")) {
            List<String> failed = new ArrayList<>();
            for (Product p : bundle.items()) {
                try {
                    cart.add(user.getUsername(), p, 1);
                } catch (CartException e) {
                    failed.add(p.getName());
                }
            }
            if (failed.isEmpty()) {
                ScreenUtil.success("All items were added to your cart.");
            } else {
                ScreenUtil.warning("Some items could not be added: " + String.join(", ", failed));
            }
            InputUtil.pause();
        }
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}