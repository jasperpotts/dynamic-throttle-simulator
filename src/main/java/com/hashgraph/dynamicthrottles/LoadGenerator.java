package com.hashgraph.dynamicthrottles;

import static com.hashgraph.dynamicthrottles.Node.NANOS_PER_SECOND;

import com.hashgraph.dynamicthrottles.model.Transaction;
import java.lang.System.Logger;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

/**
 * This class is responsible for generating load on the nodes by creating transactions and sending them to the nodes. It
 * runs in a separate thread and continuously generates transactions until the program is terminated. Sending them as
 * fast as the nodes can accept them. The transaction work amount is random, but it is capped at 250 ms.
 */
public class LoadGenerator {
    private static final Random RANDOM = new Random(3518465419866731650L);
    private final Logger logger = System.getLogger(LoadGenerator.class.getName());
    private final AtomicLong acceptedTransactions = new AtomicLong(0);
    private final AtomicLong rejectedTransactions = new AtomicLong(0);
    private final AtomicLong transactions = new AtomicLong(0);
    /** The percentage of large transactions (under 15K TPS) in 0 to 100 */
    public final AtomicInteger percentageLargeTransactions = new AtomicInteger(0);

    public void start() {
        Thread loadGeneratorThread = new Thread(() -> {
            // wait 5 seconds before starting to generate load
            LockSupport.parkNanos(NANOS_PER_SECOND * 5L);
            while (true) {
//                LockSupport.parkNanos(1_000_000 / 100); // TODO temp slow down
                // pick a random node
                final int randomExtraWork = RANDOM.nextInt(0,
                        Transaction.MAX_AMOUNT_OF_WORK_IN_NANOS-Transaction.MIN_AMOUNT_OF_WORK_15K_TPS_IN_NANOS);
                final int transactionWork = Transaction.MIN_AMOUNT_OF_WORK_15K_TPS_IN_NANOS +
                        (int)(randomExtraWork * percentageLargeTransactions.get() / 100d);
                final Transaction transaction = new Transaction(transactionWork);
                transactions.incrementAndGet();
                // pick a random node and try to send the transaction
                Node node = DynamicMain.nodes.get(RANDOM.nextInt(0, DynamicMain.NUM_OF_NODES));
                boolean accepted = node.acceptTransaction(transaction);
                if (!accepted) {
                    rejectedTransactions.incrementAndGet();
                }
                // if the node is busy, wait a bit and picking another random node
                while (!accepted) {
                    // if the node is busy, wait a bit and try again
                    LockSupport.parkNanos(NANOS_PER_SECOND);
                    // pick another random node
                    node = DynamicMain.nodes.get(RANDOM.nextInt(0, DynamicMain.NUM_OF_NODES));
                    // try again
                    accepted = node.acceptTransaction(transaction);
                    if (!accepted) {
                        rejectedTransactions.incrementAndGet();
                    }
                }
                // track accepted and rejected transactions metrics
                acceptedTransactions.incrementAndGet();
            }
        });
        loadGeneratorThread.setUncaughtExceptionHandler((t, e) ->
                logger.log(Logger.Level.ERROR, "Load generator thread interrupted: " + e.getMessage(), e));
        loadGeneratorThread.setDaemon(true);
        loadGeneratorThread.start();
    }

    public double getAcceptedTransactionPercentageSinceLastCall() {
        long accepted = acceptedTransactions.getAndSet(0);
        long rejected = rejectedTransactions.getAndSet(0);
        return (double) accepted / (accepted + rejected);
    }

    public long[] getAcceptedRejectedSinceLastCall() {
        return new long[]{
                acceptedTransactions.getAndSet(0),
                rejectedTransactions.getAndSet(0)
        };
    }

    public long getTransactionsSinceLastCall() {
        return transactions.getAndSet(0);
    }
}
