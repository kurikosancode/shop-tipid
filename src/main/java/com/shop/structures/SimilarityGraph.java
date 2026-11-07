package com.shop.structures;

import com.shop.config.Config;
import com.shop.model.Order;
import com.shop.model.OrderItem;
import com.shop.model.Product;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * SIMILARITY GRAPH: products are nodes, edges link similar products.
 *
 * Edge weight = (sameLabel ? 3 : 0) + (sameCategory ? 1 : 0) + coPurchaseCount * 0.5
 * (constants live in Config). Only pairs with weight > 0 get an edge.
 *
 * Data structures (all prebuilt Java collections, wrapped in this class):
 *   adjacency  : HashMap<String, List<Edge>>  adjacency list, productId -> its edges
 *   nodes      : HashMap<String, Product>     productId -> product (hash table)
 *   coPurchase : HashMap<String, Integer>     "idA|idB" -> times bought in the same order
 *   BFS uses an ArrayDeque as its queue.
 *
 * Complexity (n products, m edges, k items in one order):
 *   build            O(n^2 + total pairs in orders)   run once at startup
 *   recordOrder      O(k^2 * degree)                  incremental update after each checkout
 *   getNeighbors     O(1)
 *   getWeight        O(degree)
 *   collectCandidates (BFS, depth <= 2)  O(n + m) worst case
 */
public class SimilarityGraph {

    private final Map<String, Product> nodes = new HashMap<>();
    private final Map<String, List<Edge>> adjacency = new HashMap<>();
    private final Map<String, Integer> coPurchase = new HashMap<>();

    // ------------------------------------------------------------ building

    /** Rebuilds the whole graph from the current products and all past orders. */
    public void build(Collection<Product> products, Collection<Order> orders) {
        nodes.clear();
        adjacency.clear();
        coPurchase.clear();

        for (Product product : products) {
            nodes.put(product.getId(), product);
            adjacency.put(product.getId(), new ArrayList<>());
        }
        for (Order order : orders) {
            List<String> ids = distinctProductIds(order);
            for (int i = 0; i < ids.size(); i++) {
                for (int j = i + 1; j < ids.size(); j++) {
                    coPurchase.merge(pairKey(ids.get(i), ids.get(j)), 1, Integer::sum);
                }
            }
        }

        List<Product> list = new ArrayList<>(nodes.values());
        for (int i = 0; i < list.size(); i++) {
            for (int j = i + 1; j < list.size(); j++) {
                double weight = weight(list.get(i), list.get(j));
                if (weight > 0) {
                    link(list.get(i).getId(), list.get(j).getId(), weight);
                }
            }
        }
    }

    /** Called after each checkout: raises co-purchase counts and updates only the affected edges. */
    public void recordOrder(Order order) {
        List<String> ids = distinctProductIds(order);
        for (int i = 0; i < ids.size(); i++) {
            for (int j = i + 1; j < ids.size(); j++) {
                String a = ids.get(i);
                String b = ids.get(j);
                coPurchase.merge(pairKey(a, b), 1, Integer::sum);
                Product pa = nodes.get(a);
                Product pb = nodes.get(b);
                if (pa != null && pb != null) {
                    link(a, b, weight(pa, pb));
                }
            }
        }
    }

    // ------------------------------------------------------------ queries

    public boolean contains(String productId) {
        return nodes.containsKey(productId);
    }

    public int nodeCount() {
        return nodes.size();
    }

    public int edgeCount() {
        int total = 0;
        for (List<Edge> edges : adjacency.values()) {
            total += edges.size();
        }
        return total / 2;
    }

    public List<Edge> getNeighbors(String productId) {
        List<Edge> edges = adjacency.get(productId);
        return edges == null ? new ArrayList<>() : Collections.unmodifiableList(edges);
    }

    /** Weight of the edge between two products, or 0 if they are not connected. */
    public double getWeight(String a, String b) {
        for (Edge edge : getNeighbors(a)) {
            if (edge.targetId().equals(b)) {
                return edge.weight();
            }
        }
        return 0;
    }

    public int getCoPurchaseCount(String a, String b) {
        return coPurchase.getOrDefault(pairKey(a, b), 0);
    }

    // ------------------------------------------------------------ BFS

    /**
     * BREADTH-FIRST SEARCH from the source products (the ones the user bought or viewed),
     * up to maxDepth hops (1-2). Returns candidateId -> raw similarity.
     *
     * A depth-1 neighbor scores its edge weight. A deeper candidate scores
     * min(edge weights along the path) * depthDecay^(depth-1), so closer products count more.
     * When several paths reach the same product, the best score is kept.
     * The source products themselves are never returned.
     */
    public Map<String, Double> collectCandidates(Collection<String> sourceIds, int maxDepth, double depthDecay) {
        Map<String, Integer> depth = new HashMap<>();
        Map<String, Double> pathScore = new HashMap<>();
        Map<String, Double> best = new HashMap<>();
        Deque<String> queue = new ArrayDeque<>();

        for (String id : sourceIds) {
            if (adjacency.containsKey(id) && !depth.containsKey(id)) {
                depth.put(id, 0);
                pathScore.put(id, Double.MAX_VALUE);
                queue.add(id);
            }
        }
        Set<String> sources = new HashSet<>(depth.keySet());

        while (!queue.isEmpty()) {
            String current = queue.poll();
            int currentDepth = depth.get(current);
            if (currentDepth >= maxDepth) {
                continue;
            }
            double decay = Math.pow(depthDecay, currentDepth);
            for (Edge edge : adjacency.get(current)) {
                String next = edge.targetId();
                if (sources.contains(next)) {
                    continue;
                }
                double score = Math.min(pathScore.getOrDefault(current, 0.0), edge.weight()) * decay;
                if (!depth.containsKey(next)) {
                    depth.put(next, currentDepth + 1);
                    queue.add(next);
                }
                if (score > best.getOrDefault(next, 0.0)) {
                    best.put(next, score);
                    pathScore.put(next, score);
                }
            }
        }
        return best;
    }

    // ------------------------------------------------------------ internals

    private double weight(Product a, Product b) {
        double weight = 0;
        if (a.getLabel().equalsIgnoreCase(b.getLabel())) {
            weight += Config.SAME_LABEL_WEIGHT;
        }
        if (a.getCategory().equalsIgnoreCase(b.getCategory())) {
            weight += Config.SAME_CATEGORY_WEIGHT;
        }
        weight += getCoPurchaseCount(a.getId(), b.getId()) * Config.CO_PURCHASE_WEIGHT;
        return weight;
    }

    private void link(String a, String b, double weight) {
        setEdge(a, b, weight);
        setEdge(b, a, weight);
    }

    private void setEdge(String from, String to, double weight) {
        List<Edge> edges = adjacency.get(from);
        for (int i = 0; i < edges.size(); i++) {
            if (edges.get(i).targetId().equals(to)) {
                edges.set(i, new Edge(to, weight));
                return;
            }
        }
        edges.add(new Edge(to, weight));
    }

    private static List<String> distinctProductIds(Order order) {
        Set<String> ids = new LinkedHashSet<>();
        for (OrderItem item : order.getItems()) {
            ids.add(item.getProductId());
        }
        return new ArrayList<>(ids);
    }

    private static String pairKey(String a, String b) {
        return a.compareTo(b) < 0 ? a + "|" + b : b + "|" + a;
    }
}