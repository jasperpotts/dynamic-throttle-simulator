package com.hashgraph.dynamicthrottles.model;

/**
 * A record that represents an event received by the system. It contains the event itself and the timestamp when it was
 * received.
 *
 * @param event the received event
 * @param receivedTimestamp the timestamp when the event was received, in milliseconds since the epoch
 */
public record PostConsensusEvent(Event event, long receivedTimestamp) {
    public PostConsensusEvent {
        if (event == null) {
            throw new IllegalArgumentException("event must be non-null");
        }
        if (receivedTimestamp < 0) {
            throw new IllegalArgumentException("receivedTimestamp must be non-negative");
        }
    }

    public void execute() {
        event.execute();
    }

    public long getTotalWorkInNanos() {
        return event.getTotalWorkInNanos();
    }
}
