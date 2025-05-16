package com.hashgraph.dynamicthrottles.model;

import java.util.List;

/**
 * Batch of transactions to be executed from single node, the minimum unit that is gossiped and we reach consensus on.
 *
 * @param nodeId The node that created this event
 * @param healthPercentage The health percentage of the node that created this event, between 0 and 100. We can debate
 *                         if this needs to be on every event or just state signing events are they may be enough.
 * @param transactions The list of transactions that are part of this event.
 */
public record Event(int nodeId, int healthPercentage, List<Transaction> transactions) {

    public Event {
        if (transactions == null) {
            throw new IllegalArgumentException("transactions must be null");
        }
        if (nodeId < 0) {
            throw new IllegalArgumentException("nodeId must be non-negative");
        }
        if (healthPercentage < 0 || healthPercentage > 100) {
            throw new IllegalArgumentException("healthPercentage must be between 0 and 100");
        }
    }

    public void execute() {
        for (Transaction transaction : transactions) {
            transaction.execute();
        }
    }

    public long getTotalWorkInNanos() {
        return transactions.stream().mapToLong(Transaction::amountOfWorkInNanos).sum();
    }
}
