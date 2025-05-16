package com.hashgraph.dynamicthrottles.model;

import java.util.List;
import java.util.concurrent.locks.LockSupport;

/**
 * Round is a batch of events that reach consensus together and are executed together.
 *
 * @param events the list of events that reached consensus
 * @param consensusTimestamp the timestamp of the first event in the round, in milliseconds since the epoch
 */
public record Round(long roundNum, List<PostConsensusEvent> events, long consensusTimestamp) {
    public Round {
        if (events == null || events.isEmpty()) {
            throw new IllegalArgumentException("events must be non-empty");
        }
        if (consensusTimestamp < 0) {
            throw new IllegalArgumentException("consensusTimestamp must be non-negative");
        }
    }

    public void execute() {
        // just do one total sleep as more accurate than doing each event or each transaction sleep
        long totalWorkNanos = events.stream()
                .mapToLong(PostConsensusEvent::getTotalWorkInNanos)
                .sum();
        LockSupport.parkNanos(totalWorkNanos);
    }
}
