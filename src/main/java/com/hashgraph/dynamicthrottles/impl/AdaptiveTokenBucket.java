package com.hashgraph.dynamicthrottles.impl;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Adaptive Token Bucket Rate Limiter with EMA smoothing for production use.
 * Adjusts token refill rate smoothly based on queue health.
 */
public class AdaptiveTokenBucket {

    private final long capacity;
    private final double minRefillRate;
    private final double maxRefillRate;

    private final AtomicLong tokens;
    private final AtomicReference<Double> refillRateEMA;

    private volatile long lastRefillTimestamp;

    private final double smoothingFactor;

    /**
     * Initializes the Adaptive Token Bucket.
     *
     * @param capacity        Maximum bucket capacity.
     * @param minRefillRate   Minimum refill rate (tokens/sec).
     * @param maxRefillRate   Maximum refill rate (tokens/sec).
     * @param initialRefillRate Initial refill rate.
     * @param smoothingFactor EMA smoothing factor (0 < alpha <= 1, lower is smoother).
     */
    public AdaptiveTokenBucket(long capacity, double minRefillRate, double maxRefillRate,
            double initialRefillRate, double smoothingFactor) {
        this.capacity = capacity;
        this.minRefillRate = minRefillRate;
        this.maxRefillRate = maxRefillRate;
        this.tokens = new AtomicLong(capacity);
        this.refillRateEMA = new AtomicReference<>(initialRefillRate);
        this.lastRefillTimestamp = System.nanoTime();
        this.smoothingFactor = smoothingFactor;
    }

    /**
     * Attempts to acquire a token immediately.
     *
     * @return true if a token was acquired, false otherwise.
     */
    public synchronized boolean tryAcquire() {
        refill();
        if (tokens.get() > 0) {
            tokens.decrementAndGet();
            return true;
        }
        return false;
    }

    /**
     * Updates the refill rate based on current queue health.
     *
     * @param health Current health of the queue (0.0 to 1.0).
     */
    public synchronized void updateHealth(double health) {
        double targetRefillRate = minRefillRate + health * (maxRefillRate - minRefillRate);
        refillRateEMA.updateAndGet(prevRate ->
                prevRate + smoothingFactor * (targetRefillRate - prevRate)
        );
    }

    /**
     * Refill tokens based on elapsed time and smoothed refill rate.
     */
    private void refill() {
        long now = System.nanoTime();
        double secondsElapsed = (now - lastRefillTimestamp) / 1_000_000_000.0;
        lastRefillTimestamp = now;

        double rate = refillRateEMA.get();
        long newTokens = (long) (secondsElapsed * rate);
        if (newTokens > 0) {
            tokens.updateAndGet(current -> Math.min(capacity, current + newTokens));
        }
    }

    /**
     * Get current token count (for monitoring/debugging).
     */
    public long getTokens() {
        refill();
        return tokens.get();
    }

    /**
     * Get current refill rate (for monitoring/debugging).
     */
    public double getRefillRatePerSecond() {
        return refillRateEMA.get();
    }

    public double getCurrentTokens() {
        return tokens.get();
    }

}

