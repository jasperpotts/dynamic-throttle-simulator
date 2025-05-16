package com.hashgraph.dynamicthrottles.impl;


// Part 3: Shared Elastic Token Bucket
public class ElasticTokenBucket {
    private final int capacity;
    private double tokens;
    private double refillRatePerSecond;
    private long lastRefillTime;

    public ElasticTokenBucket(int capacity, double initialRate) {
        this.capacity = capacity;
        this.tokens = capacity;
        this.refillRatePerSecond = initialRate;
        this.lastRefillTime = System.nanoTime();
    }

    public synchronized boolean tryConsume(int amount) {
        refill();
        if (tokens >= amount) {
            tokens -= amount;
            return true;
        }
        return false;
    }

    public synchronized void refill() {
        long now = System.nanoTime();
        double secondsElapsed = (now - lastRefillTime) / 1e9;
        double refillAmount = secondsElapsed * refillRatePerSecond;
        tokens = Math.min(capacity, tokens + refillAmount); // Enforce cap
        lastRefillTime = now;
    }

    public synchronized void setRefillRate(double newRate) {
        refillRatePerSecond = newRate;
    }

    public synchronized double getTokens() {
        refill();
        return tokens;
    }

    public synchronized double getCurrentTokens() {
        return tokens;
    }

    public synchronized double getRefillRatePerSecond() {
        return refillRatePerSecond;
    }
}
