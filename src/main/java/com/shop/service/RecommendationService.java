package com.shop.service;

import com.shop.config.Config;
import com.shop.model.Order;
import com.shop.model.OrderItem;
import com.shop.model.Product;
import com.shop.model.RankBy;
import com.shop.model.RecommendationSettings;
import com.shop.model.User;
import com.shop.structures.SimilarityGraph;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Produces recommendations.
 *
 * Steps:
 *  1. BFS (depth 1-2) over the SimilarityGraph from the user's bought products collects candidates.
 *  2. Candidates that are bought, out of stock, or outside the user's price limit are dropped.
 *  3. Each candidate gets   score = w1*similarity + w2*popularity + w3*rating + w4*priceFit
 *     (all four factors normalized to 0..1; weights come from the user's settings).
 *  4. Candidates go into a PRIORITY QUEUE (heap) ordered by the user's chosen ranking
 *     parameter, and the top N are popped.
 *
 * Data structures: SimilarityGraph (HashMap adjacency list + ArrayDeque BFS), PriorityQueue (heap),
 * HashMap / HashSet for lookups.
 * Complexity (m candidates, N results): BFS O(n + e); scoring O(m); heap inserts O(m log m);
 * popping the top N O(N log m). The graph is updated after each checkout through a listener.
 */
public class RecommendationService {

    /** One suggestion with its normalized factors and the "why recommended" text. */
    public record Recommendation(Product product, double similarity, double popularity,
                                 double rating, double priceFit, double score, String reason) { }

    private final ProductService products;
    private final OrderService orders;
    private final SimilarityGraph graph = new SimilarityGraph();

    public RecommendationService(ProductService products, OrderService orders) {
        this.products = products;
        this.orders = orders;
        rebuildGraph();
        orders.addCheckoutListener(graph::recordOrder);
    }

    // ------------------------------------------------------------ graph upkeep

    /** Rebuilds the graph from products and orders (startup, and after admins edit the catalog). */
    public void rebuildGraph() {
        graph.build(products.getAll(), orders.getAllOrders());
    }

    /** Safety net: if the number of products changed, rebuild before using the graph. */
    private void ensureGraphCurrent() {
        if (graph.nodeCount() != products.size()) {
            rebuildGraph();
        }
    }

    public SimilarityGraph getGraph() {
        return graph;
    }

    // ------------------------------------------------------------ public API

    /** "Recommended for you": based on everything the user has bought. */
    public List<Recommendation> recommendForUser(User user, int n) {
        return recommend(user, purchasedProducts(user.getUsername()), n);
    }

    /** Suggestions right after a purchase: based on the items of that order. */
    public List<Recommendation> recommendAfterPurchase(User user, Order order, int n) {
        List<Product> sources = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (OrderItem item : order.getItems()) {
            Product product = products.getById(item.getProductId());
            if (product != null && seen.add(product.getId())) {
                sources.add(product);
            }
        }
        return recommend(user, sources, n);
    }

    public Set<String> getPurchasedIds(String username) {
        return orders.getPurchasedProductIds(username);
    }

    /** Normalized (0..1) similarity of every product to the user's purchase history. Used by the Bundle Builder. */
    public Map<String, Double> getSimilarityMap(String username) {
        ensureGraphCurrent();
        Map<String, Double> raw = graph.collectCandidates(
                idsOf(purchasedProducts(username)), Config.BFS_MAX_DEPTH, Config.DEPTH_DECAY);
        double max = 0;
        for (double value : raw.values()) {
            max = Math.max(max, value);
        }
        Map<String, Double> normalized = new HashMap<>();
        if (max > 0) {
            for (Map.Entry<String, Double> entry : raw.entrySet()) {
                normalized.put(entry.getKey(), entry.getValue() / max);
            }
        }
        return normalized;
    }

    // ------------------------------------------------------------ core

    private List<Recommendation> recommend(User user, List<Product> sources, int n) {
        ensureGraphCurrent();
        int limit = Math.max(1, Math.min(n, Config.MAX_TOP_N));
        RecommendationSettings settings = user.getSettings();
        Set<String> bought = orders.getPurchasedProductIds(user.getUsername());
        Set<String> sourceIds = idsOf(sources);

        // 1) BFS candidates
        Map<String, Double> rawSimilarity =
                graph.collectCandidates(sourceIds, Config.BFS_MAX_DEPTH, Config.DEPTH_DECAY);

        // 2) filter
        List<Product> pool = new ArrayList<>();
        for (String id : rawSimilarity.keySet()) {
            Product product = products.getById(id);
            if (product != null && eligible(product, bought, sourceIds, settings)) {
                pool.add(product);
            }
        }
        if (pool.size() < limit) {
            // New customer, or the graph found too few: fall back to the whole catalog.
            pool.clear();
            for (Product product : products.getAll()) {
                if (eligible(product, bought, sourceIds, settings)) {
                    pool.add(product);
                }
            }
        }

        // 3) score
        double maxSimilarity = 0;
        for (Product product : pool) {
            maxSimilarity = Math.max(maxSimilarity, rawSimilarity.getOrDefault(product.getId(), 0.0));
        }
        int maxPurchases = products.getMaxPurchaseCount();
        double referencePrice = averagePurchasePrice(user.getUsername());

        // 4) heap ordered by the user's chosen ranking parameter
        PriorityQueue<Recommendation> heap = new PriorityQueue<>(comparatorFor(settings.getRankBy()));
        for (Product product : pool) {
            double similarity = maxSimilarity > 0
                    ? rawSimilarity.getOrDefault(product.getId(), 0.0) / maxSimilarity : 0;
            double popularity = Math.min(1.0, product.getPurchaseCount() / (double) maxPurchases);
            double rating = Math.max(0, Math.min(1.0, product.getRating() / 5.0));
            double priceFit = referencePrice <= 0 ? Config.PRICE_FIT_NEUTRAL
                    : Math.max(0, 1 - Math.abs(product.getPrice() - referencePrice) / referencePrice);
            double score = settings.getWSimilarity() * similarity
                    + settings.getWPopularity() * popularity
                    + settings.getWRating() * rating
                    + settings.getWPriceFit() * priceFit;
            heap.add(new Recommendation(product, similarity, popularity, rating, priceFit, score,
                    explain(product, sources, popularity)));
        }

        List<Recommendation> result = new ArrayList<>();
        while (!heap.isEmpty() && result.size() < limit) {
            result.add(heap.poll());
        }
        return result;
    }

    private boolean eligible(Product product, Set<String> bought, Set<String> sourceIds,
                             RecommendationSettings settings) {
        return product.isInStock()
                && !bought.contains(product.getId())
                && !sourceIds.contains(product.getId())
                && settings.isPriceAllowed(product.getPrice());
    }

    /** Heap order: the chosen parameter first, then the combined score, then id (so results are stable). */
    private static Comparator<Recommendation> comparatorFor(RankBy rankBy) {
        Comparator<Recommendation> byScore = Comparator.comparingDouble(Recommendation::score).reversed();
        Comparator<Recommendation> primary = switch (rankBy) {
            case POPULARITY -> Comparator.comparingInt(
                    (Recommendation r) -> r.product().getPurchaseCount()).reversed();
            case RATING -> Comparator.comparingDouble(
                    (Recommendation r) -> r.product().getRating()).reversed();
            case PRICE_LOW_TO_HIGH -> Comparator.comparingDouble(
                    (Recommendation r) -> r.product().getPrice());
            case PRICE_HIGH_TO_LOW -> Comparator.comparingDouble(
                    (Recommendation r) -> r.product().getPrice()).reversed();
            case BEST_MATCH -> byScore;
        };
        return primary.thenComparing(byScore).thenComparing(r -> r.product().getId());
    }

    /** "Same label: Shirt | Popularity 87 | Rating 4.5" */
    private String explain(Product candidate, List<Product> sources, double popularity) {
        String prefix = null;
        for (Product source : sources) {
            if (candidate.getLabel().equalsIgnoreCase(source.getLabel())) {
                prefix = "Same label: " + candidate.getLabel();
                break;
            }
        }
        if (prefix == null) {
            for (Product source : sources) {
                if (candidate.getCategory().equalsIgnoreCase(source.getCategory())) {
                    prefix = "Same category: " + candidate.getCategory();
                    break;
                }
            }
        }
        if (prefix == null) {
            for (Product source : sources) {
                if (graph.getCoPurchaseCount(candidate.getId(), source.getId()) > 0) {
                    prefix = "Bought together with " + source.getName();
                    break;
                }
            }
        }
        if (prefix == null) {
            prefix = sources.isEmpty() ? "Popular pick" : "Related to your purchases";
        }
        return String.format(Locale.US, "%s | Popularity %d | Rating %.1f", prefix,
                Math.round(popularity * Config.POPULARITY_DISPLAY_SCALE), candidate.getRating());
    }

    // ------------------------------------------------------------ helpers

    /** Distinct products the user bought (still in the catalog), most recent first. */
    private List<Product> purchasedProducts(String username) {
        List<Product> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Order order : orders.getOrdersForUser(username)) {
            for (OrderItem item : order.getItems()) {
                Product product = products.getById(item.getProductId());
                if (product != null && seen.add(product.getId())) {
                    result.add(product);
                }
            }
        }
        return result;
    }

    /** Average price per unit the user has paid so far (0 if no purchases). Basis of priceFit. */
    private double averagePurchasePrice(String username) {
        double total = 0;
        int units = 0;
        for (Order order : orders.getOrdersForUser(username)) {
            for (OrderItem item : order.getItems()) {
                total += item.getPriceAtPurchase() * item.getQuantity();
                units += item.getQuantity();
            }
        }
        return units == 0 ? 0 : total / units;
    }

    private static Set<String> idsOf(List<Product> list) {
        Set<String> ids = new HashSet<>();
        for (Product product : list) {
            ids.add(product.getId());
        }
        return ids;
    }
}