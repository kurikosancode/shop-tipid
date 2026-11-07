package com.shop.service;

import com.shop.config.Config;
import com.shop.model.Product;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * BUNDLE BUILDER using my own BACKTRACKING algorithm.
 *
 * Goal: choose at most one product per category, at most maxItems products in total, with total
 * price <= budget, maximizing the total item score
 *   itemScore = 0.5*rating + 0.3*popularity + 0.2*similarity-to-the-user's-history  (each 0..1).
 *
 * Backtracking: categories are the decision levels. At each level we either pick one item of that
 * category (and recurse) or skip the category (and recurse), then undo the choice (backtrack).
 * Pruning:
 *   - over budget: an item that would push the total price above the budget is skipped;
 *   - bound: if current score + the best score the remaining slots could possibly add can no longer
 *     beat the best bundle found so far, the whole branch is abandoned.
 * Categories are sorted by their best item score so the bound is tight and pruning is effective.
 *
 * Data structures: HashMap (category -> items), ArrayList (sorted groups), ArrayDeque (current picks).
 * Complexity: worst case O(prod(items_i + 1)) over the categories, but limited by maxItems (at most
 * 4 picks) and cut down heavily by the two prunings. The counters in Bundle show how many
 * branches were visited / pruned.
 */
public class BundleService {

    private static final double EPS = 1e-9;

    /** Result of a search. items is empty when nothing fits the budget. */
    public record Bundle(List<Product> items, double totalPrice, double totalScore,
                         int nodesExplored, int prunedOverBudget, int prunedByBound) {
        public boolean isEmpty() {
            return items.isEmpty();
        }
    }

    private record Scored(Product product, double score) { }

    private final ProductService products;

    public BundleService(ProductService products) {
        this.products = products;
    }

    /**
     * @param categories   categories to use (empty = all)
     * @param excludeIds   product ids that must not appear (items the user already bought)
     * @param similarity   productId -> 0..1 similarity to the user's history (may be empty)
     */
    public Bundle findBestBundle(double budget, int maxItems, Set<String> categories,
                                 Set<String> excludeIds, Map<String, Double> similarity) {
        Set<String> wanted = new HashSet<>();
        for (String category : categories) {
            wanted.add(category.toLowerCase(Locale.ROOT));
        }

        // Group in-stock, affordable, allowed products by category and score them.
        int maxPurchases = products.getMaxPurchaseCount();
        Map<String, List<Scored>> byCategory = new HashMap<>();
        for (Product product : products.getAll()) {
            String category = product.getCategory().toLowerCase(Locale.ROOT);
            if (!product.isInStock() || product.getPrice() > budget || excludeIds.contains(product.getId())) {
                continue;
            }
            if (!wanted.isEmpty() && !wanted.contains(category)) {
                continue;
            }
            double score = itemScore(product, similarity.getOrDefault(product.getId(), 0.0), maxPurchases);
            byCategory.computeIfAbsent(category, k -> new ArrayList<>()).add(new Scored(product, score));
        }
        if (byCategory.isEmpty()) {
            return new Bundle(new ArrayList<>(), 0, 0, 0, 0, 0);
        }

        // Best items first inside a group; groups with the best top item first.
        List<List<Scored>> groups = new ArrayList<>(byCategory.values());
        for (List<Scored> group : groups) {
            group.sort(Comparator.comparingDouble(Scored::score).reversed());
        }
        groups.sort(Comparator.comparingDouble((List<Scored> g) -> g.get(0).score()).reversed());

        Search search = new Search(groups, budget, maxItems);
        search.explore(0, 0, 0.0, 0.0);

        List<Product> chosen = new ArrayList<>();
        for (Scored scored : search.bestChosen) {
            chosen.add(scored.product());
        }
        chosen.sort(Comparator.comparing(Product::getCategory, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Product::getName));
        double total = Math.round(search.bestSpent * 100.0) / 100.0;
        double score = chosen.isEmpty() ? 0 : search.bestScore;
        return new Bundle(chosen, total, score, search.explored, search.prunedBudget, search.prunedBound);
    }

    private double itemScore(Product product, double similarity, int maxPurchases) {
        double rating = Math.max(0, Math.min(1.0, product.getRating() / 5.0));
        double popularity = Math.min(1.0, product.getPurchaseCount() / (double) maxPurchases);
        return Config.BUNDLE_W_RATING * rating
                + Config.BUNDLE_W_POPULARITY * popularity
                + Config.BUNDLE_W_SIMILARITY * similarity;
    }

    /** Holds the state of one backtracking run, so the service itself stays stateless. */
    private static final class Search {
        private final List<List<Scored>> groups;
        private final double[] topScore;           // best item score in each group (groups sorted desc)
        private final double budget;
        private final int maxItems;
        private final Deque<Scored> chosen = new ArrayDeque<>();

        private List<Scored> bestChosen = new ArrayList<>();
        private double bestScore = -1;
        private double bestSpent = 0;
        private int explored;
        private int prunedBudget;
        private int prunedBound;

        Search(List<List<Scored>> groups, double budget, int maxItems) {
            this.groups = groups;
            this.budget = budget;
            this.maxItems = maxItems;
            this.topScore = new double[groups.size()];
            for (int i = 0; i < groups.size(); i++) {
                topScore[i] = groups.get(i).get(0).score();
            }
        }

        /**
         * @param group  index of the category being decided
         * @param count  items picked so far
         * @param spent  total price so far
         * @param score  total score so far
         */
        void explore(int group, int count, double spent, double score) {
            explored++;

            // Every partial bundle is a valid answer. Keep the best; on a tie keep the cheaper one.
            if (count > 0 && (score > bestScore + EPS
                    || (Math.abs(score - bestScore) <= EPS && spent < bestSpent))) {
                bestScore = score;
                bestSpent = spent;
                bestChosen = new ArrayList<>(chosen);
            }
            if (group >= groups.size() || count >= maxItems) {
                return;
            }

            // Bound: the remaining slots can add at most the best scores of the next categories.
            int slots = maxItems - count;
            double bound = 0;
            for (int i = group; i < Math.min(groups.size(), group + slots); i++) {
                bound += topScore[i];
            }
            if (score + bound <= bestScore + EPS) {
                prunedBound++;
                return;
            }

            // Choice 1: take one item of this category.
            for (Scored item : groups.get(group)) {
                double price = item.product().getPrice();
                if (spent + price > budget + EPS) {
                    prunedBudget++;            // over budget: prune this branch
                    continue;
                }
                chosen.addLast(item);          // choose
                explore(group + 1, count + 1, spent + price, score + item.score());
                chosen.removeLast();           // un-choose (backtrack)
            }
            // Choice 2: skip this category.
            explore(group + 1, count, spent, score);
        }
    }
}