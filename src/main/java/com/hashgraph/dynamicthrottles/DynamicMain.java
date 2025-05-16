package com.hashgraph.dynamicthrottles;

import static com.hashgraph.dynamicthrottles.Node.NANOS_PER_SECOND;

import com.hashgraph.dynamicthrottles.model.Event;
import com.hashgraph.dynamicthrottles.model.PostConsensusEvent;
import com.hashgraph.dynamicthrottles.model.Round;
import com.hashgraph.dynamicthrottles.model.Transaction;
import com.hashgraph.dynamicthrottles.simulated.Consensus;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@SuppressWarnings({"StringConcatenationInsideStringBufferAppend", "DuplicatedCode"})
public class DynamicMain {
    public static final int NUM_OF_NODES = 5;
    public static final List<Node> nodes = IntStream.range(1, NUM_OF_NODES+1)
            .mapToObj(Node::new)
            .collect(Collectors.toList());
    public static final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);
    public static final Consensus consensus = new Consensus(DynamicMain::handleNewRound, NUM_OF_NODES);
    public static final LoadGenerator loadGenerator = new LoadGenerator();
    // gather metrics
    public static final AtomicLong roundsInLastSecond = new AtomicLong(0);
    public static final AtomicLong eventsInLastSecond = new AtomicLong(0);
    public static final AtomicLong transactionsInLastSecond = new AtomicLong(0);
    public static final AtomicLong transactionWorkNsInLastSecond = new AtomicLong(0);

    /** Main for command line testing */
    public static void main(String[] args) {
        // start executor to print state once per second
        executorService.scheduleAtFixedRate(() -> {
            // return to start of line
            StringBuilder sb = new StringBuilder();
            for (Node node : nodes) {
                // print the same line over and over with all nodes toStrings with "," delimiter
                sb.append(node.toString() + ", ");
            }
            // print metrics
            sb.append(" con queue: " + consensus.consensusQueueSize() + ", ");
            sb.append(" accepted: %.1f%%,".formatted(loadGenerator.getAcceptedTransactionPercentageSinceLastCall()*100));
            sb.append(" rounds: " + roundsInLastSecond.getAndSet(0) + ", ");
            sb.append(" events: " + eventsInLastSecond.getAndSet(0) + ", ");
            sb.append(" transactions: " + transactionsInLastSecond.getAndSet(0) + ", ");
            sb.append(" transaction work: " + ((double)transactionWorkNsInLastSecond.getAndSet(0)/NANOS_PER_SECOND)+" seconds");
            System.out.println(sb);
        }, 0, 1, java.util.concurrent.TimeUnit.SECONDS);
        startSimulation();
    }

    public static void startSimulation() {
        // Start load generator
        loadGenerator.start();
    }

    /**
     * Called by consensus when it has a created a new round to process.
     *
     * @param round the round to process
     */
    private static void handleNewRound(Round round) {
        // compute a bunch of metrics for debugging
        roundsInLastSecond.incrementAndGet();
        for (PostConsensusEvent event : round.events()) {
            eventsInLastSecond.incrementAndGet();
            // add the work of all transactions in the event
            for (Transaction transaction : event.event().transactions()) {
                transactionsInLastSecond.incrementAndGet();
                transactionWorkNsInLastSecond.addAndGet(transaction.amountOfWorkInNanos());
            }
        }
        // send the round to all nodes
        for (Node node : nodes) {
            node.roundReachedConsensus(round);
        }
    }

    /**
     * Called by a node when it has a event to gossip to the other nodes.
     *
     * @param event the event to gossip
     */
    public static void gossip(Event event) {
        // send event to consensus
        consensus.addEvent(event);
    }
}