package com.hashgraph.dynamicthrottles.simulated;

import com.hashgraph.dynamicthrottles.model.Event;
import com.hashgraph.dynamicthrottles.model.PostConsensusEvent;
import com.hashgraph.dynamicthrottles.model.Round;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * This simulates the hashgraph consensus process. It just queues all incoming events and then takes a random number of
 * events and forms a round. The round size is between 10 and 400 events.
 */
public class Consensus {
    private static final int MIN_EVENT_PER_ROUND = 50;
    private static final int MAX_EVENT_PER_ROUND = 400;
    private final Random random = new Random(3626415163845131L);
    private final ConcurrentLinkedDeque<Event> consensusQueue = new ConcurrentLinkedDeque<>();
    private final Consumer<Round> roundHandler;
    public final AtomicLong currentRound = new AtomicLong(0);
    private final int twoThirdsOfNodes;

    public Consensus(Consumer<Round> roundHandler, final int numberOfNodes) {
        this.roundHandler = roundHandler;
        this.twoThirdsOfNodes = (numberOfNodes * 2) / 3;
    }

    public synchronized void addEvent(Event event) {
        consensusQueue.add(event);
        // try and create a round
        final int numOfEvents = random.nextInt(MIN_EVENT_PER_ROUND, MAX_EVENT_PER_ROUND);
        // see if we have enough events
        if (consensusQueue.size() >= numOfEvents) {
            // collect events
            final Event[] events = new Event[numOfEvents];
            Set<Integer> nodesFromEvents = new HashSet<>();
            for (int i = 0; i < numOfEvents; i++) {
                events[i] = consensusQueue.poll();
                nodesFromEvents.add(events[i].nodeId());
            }
            // check we have at least 2/3 of
            if (nodesFromEvents.size() < twoThirdsOfNodes) {
                // not enough nodes, put them back on front of queue in same order we took them off
                for(int i = numOfEvents-1; i >=0; i--) {
                    consensusQueue.addFirst(events[i]);
                }
                return;
            }
            // create a round
            final long roundConsensusTimestamp = System.currentTimeMillis();
            final AtomicLong eventConsensusTimestampAtomic = new AtomicLong(roundConsensusTimestamp);
            final Round round = new Round(
                    currentRound.incrementAndGet(),
                    Arrays.stream(events)
                            .map(e -> new PostConsensusEvent(e,eventConsensusTimestampAtomic.getAndIncrement()))
                            .toList(),
                    roundConsensusTimestamp);
            // call the round handler
            roundHandler.accept(round);
        }
    }

    public int consensusQueueSize() {
        return consensusQueue.size();
    }
}
