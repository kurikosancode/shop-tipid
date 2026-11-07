package com.shop.structures;

/**
 * One directed half of an undirected similarity link: "the neighbor product is targetId,
 * with this similarity weight". Immutable record. All accessors are O(1).
 */
public record Edge(String targetId, double weight) { }