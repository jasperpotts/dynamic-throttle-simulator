package com.hashgraph.dynamicthrottles;

import com.hashgraph.dynamicthrottles.impl.GlobalIntakeController;
import com.hashgraph.dynamicthrottles.model.Event;
import com.hashgraph.dynamicthrottles.model.Round;
import com.hashgraph.dynamicthrottles.model.Transaction;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

@SuppressWarnings("FieldCanBeLocal")
public class Node {
    public static final int MAX_TRANSACTIONS_QUEUE_SIZE = 500;
    public static final int MAX_EVENTS_QUEUE_SIZE_FOR_UNHEALTHY = 20;
    public static final int NANOS_PER_SECOND = 1_000_000_000;
    public static final int MAX_TRANSACTIONS_PER_EVENT = 100;
    public static final int EVENTS_PER_SECOND = 500/ DynamicMain.NUM_OF_NODES;
    private final Logger logger = System.getLogger(Node.class.getName());
    private final int nodeId;
    public final ConcurrentLinkedDeque<Transaction> incomingTransactionQueue = new ConcurrentLinkedDeque<>();
    public final ConcurrentLinkedDeque<Round> roundsToExecuteQueue = new ConcurrentLinkedDeque<>();
    private final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1, r -> {
        Thread thread = new Thread(r);
        thread.setDaemon(true);
        return thread;
    });
    public final GlobalIntakeController globalIntakeController;
    public final AtomicInteger healthPercentage = new AtomicInteger(100);
    public final AtomicLong currentRound = new AtomicLong(0);
    public final AtomicLong ingestedTransactions = new AtomicLong(0);

    public Node(int nodeId) {
        this.nodeId = nodeId;
        this.globalIntakeController = new GlobalIntakeController(nodeId);
        // schedule event creation
        executorService.scheduleAtFixedRate(this::createAndGossipEvent, 0, NANOS_PER_SECOND / EVENTS_PER_SECOND, TimeUnit.NANOSECONDS);
        // create execution thread
        Thread executionThread = new Thread(this::executeTransactions);
        executionThread.setDaemon(true);
        executionThread.setUncaughtExceptionHandler((t, e) ->
                logger.log(Level.ERROR, "Execution thread interrupted: " + e.getMessage(), e));
        executionThread.start();
    }

    public int nodeId() {
        return nodeId;
    }

    /**
     * This method is called by the load generator to add a transaction to the incoming queue
     *
     * @return true if the transaction was accepted, false otherwise
     */
    public boolean acceptTransaction(Transaction transaction) {
        // On each transaction intake attempt
        if (globalIntakeController.shouldAcceptTransaction()) {
            // Accept transaction
            incomingTransactionQueue.add(transaction);
            ingestedTransactions.incrementAndGet();
            return true;
        } else {
            // Reject or queue
            return false;
        }
    }

    /**
     * This method is called by the gossip thread to execute the event, it places it in the queue to be executed
     */
    public void roundReachedConsensus(Round round) {
        // queue rounds for execution
        roundsToExecuteQueue.add(round);
        // update global intake controller with the round
        globalIntakeController.updateGlobalRate(round);
    }

    private int computeHealthPercentage() {
        // if either queue is full, return 100 else return 0
        double incomingQueuePercentFull = Math.min((incomingTransactionQueue.size()/(double)MAX_TRANSACTIONS_QUEUE_SIZE) * 100d, 100d);
        double roundsQueuePercentFull = Math.min((roundsToExecuteQueue.size()/(double)MAX_EVENTS_QUEUE_SIZE_FOR_UNHEALTHY) * 100d, 100d);
        // 0 means unhealthy, 100 means healthy
//        final int health = 100 - (int)Math.min(incomingQueuePercentFull + roundsQueuePercentFull, 100d);
        final int health = 100 - (int)Math.min(roundsQueuePercentFull, 100d);
        if (health > 100 || health < 0) {
            throw new IllegalStateException("health is out of bounds: " + health);
        }
//        System.out.println("incomingQueueHealth = " + incomingQueuePercentFull+" "+incomingTransactionQueue.size()+" eventsToExecuteQueueSize = " + roundsQueuePercentFull+" "+ eventsToExecuteQueue.size()+" health = " + health);
        // keep health percentage for debug printing
        healthPercentage.set(health);
        return health;
    }

    /**
     * Called at regular intervals to create an event and gossip it to all nodes, collects up to
     * MAX_TRANSACTIONS_PER_EVENT from queue and creates an event with them. Then sends it to the gossip queue.
     */
    private void createAndGossipEvent() {
        try {
            // collect up to MAX_TRANSACTIONS_PER_EVENT transactions from the incoming queue
            final List<Transaction> transactions = new ArrayList<>();
            while (!incomingTransactionQueue.isEmpty() && transactions.size() < MAX_TRANSACTIONS_PER_EVENT) {
                Transaction transaction = incomingTransactionQueue.poll();
                if (transaction == null) {
                    break;
                } else {
                    transactions.add(transaction);
                }
            }
            // create the event, we send events even if we have no transactions, this is to keep the health information flowing
            Event event = new Event(nodeId, computeHealthPercentage(), transactions);
            // gossip the event to all nodes
            DynamicMain.gossip(event);
        } catch (Throwable e) {
            logger.log(Level.ERROR, "Error creating event: " + e.getMessage(), e);
        }
    }

    /**
     * This method is called by the executor thread to execute the transactions in the queue
     */
    private void executeTransactions() {
        while(true) {
            Round round = roundsToExecuteQueue.poll();
            if (round == null) {
                // we have no rounds to execute, so we will sleep for a bit, should never happen when under load
                LockSupport.parkNanos(1000); // 1ms
            } else {
                currentRound.set(round.roundNum());
                // will sleep for the amount of work in each transaction in the round
                round.execute();
            }
        }
    }

    @Override
    public String toString() {
        return switch(nodeId) {
            case 1 -> "1️⃣";
            case 2 -> "2️⃣";
            case 3 -> "3️⃣";
            case 4 -> "4️⃣";
            case 5 -> "5️⃣";
            case 6 -> "6️⃣";
            case 7 -> "7️⃣";
            case 8 -> "8️⃣";
            case 9 -> "9️⃣";
            case 10 -> "🔟";
            default -> Integer.toString(nodeId);
        } + "[in=%d, exe=%d, H=%d%%]".formatted(incomingTransactionQueue.size(), roundsToExecuteQueue.size(),
                healthPercentage.get());
    }
}
