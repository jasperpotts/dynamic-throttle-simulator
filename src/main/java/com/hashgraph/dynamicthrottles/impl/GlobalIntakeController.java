package com.hashgraph.dynamicthrottles.impl;

import com.hashgraph.dynamicthrottles.model.Round;
import java.util.concurrent.atomic.AtomicInteger;

public class GlobalIntakeController {
    private static final int TOKEN_BUCKET_CAPACITY = 50_000;         // Allow up to 10,000 txns in burst
    private static final double TOKEN_BUCKET_INITIAL_RATE = 5_000.0;  // 5,000 transactions/sec refill rate
    private static final double TOKEN_BUCKET_MIN_RATE = 2.0;  // 5,000 transactions/sec refill rate
    private static final double TOKEN_BUCKET_MAX_RATE = 15_000.0;  // 5,000 transactions/sec refill rate
    private static final double TARGET_HEALTH = 0.8;              // Target health percentage for PID controller
    private final QuorumHealthAggregator quorumHealthAggregator;
    public final AdaptivePIDRateController pid;
    //private final ElasticTokenBucket tokenBucket = new ElasticTokenBucket(TOKEN_BUCKET_CAPACITY, TOKEN_BUCKET_INITIAL_RATE);
    private final int nodeId;
    public final AtomicInteger quorumHealth = new AtomicInteger(0);
    private final AdaptiveTokenBucket tokenBucket = new AdaptiveTokenBucket(
            15000,    // capacity
            2.0,      // min refill rate
            15000.0,  // max refill rate
            5000.0,   // initial refill rate
            0.05      // EMA smoothing factor
    );


    public GlobalIntakeController(int nodeId) {
        this.nodeId = nodeId;
        this.quorumHealthAggregator = new QuorumHealthAggregator(nodeId);
        this.pid = new AdaptivePIDRateController();
    }

    // Call this periodically once per round
    public void updateGlobalRate(Round round) {
        // Compute the global quorum health for this round, it will be deterministic unless a node reconnects and is
        // lacking history for across round averaging. If we think it has to be deterministic, we can either use a
        // single round or store the averaging data in state.
        final double quorumHealth = quorumHealthAggregator.computeQuorumHealth(round);
        this.quorumHealth.set((int) (quorumHealth * 100)); // Convert to percentage
//        double newRate = pid.update(TARGET_HEALTH, quorumHealth);
//        if (nodeId==1) System.out.println("     health = " + quorumHealth+" -> " + newRate+" tps - tokens="+tokenBucket.getTokens());
//        double newRate = Math.max(TOKEN_BUCKET_MIN_RATE,TOKEN_BUCKET_MAX_RATE * quorumHealth);

//        tokenBucket.setRefillRate(newRate);
        tokenBucket.updateHealth(quorumHealth);
    }

    public boolean shouldAcceptTransaction() {
//        return tokenBucket.tryConsume(1);
        return tokenBucket.tryAcquire();
    }

    public double getCurrentTokenRate() {
        return tokenBucket.getRefillRatePerSecond();
    }

    public double getCurrentTokenCount() {
        return tokenBucket.getCurrentTokens();
    }

}
