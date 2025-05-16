package com.hashgraph.dynamicthrottles.impl;

import com.hashgraph.dynamicthrottles.model.Event;
import com.hashgraph.dynamicthrottles.model.PostConsensusEvent;
import com.hashgraph.dynamicthrottles.model.Round;
import java.lang.System.Logger;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;

/**
 * QuorumHealthAggregator is a class that aggregates the health of a quorum of nodes in a distributed system. It computes
 * the health of the quorum based on the health of the nodes in the system, and smooths out fluctuations in the health
 * signal. It is designed to produce a Byzantine Fault Tolerance health signal, which is a measure of the health of the
 * quorum of nodes in the system.
 * <p>
 * This class is designed to be thread-safe and can be used in a multithreaded environment.
 */
public final class QuorumHealthAggregator {
    /**
     * The number of rounds to average over when computing the health of the quorum. This is used to smooth out
     * fluctuations in the health of the nodes. It should be small to minimize the delay in reacting to changes in
     * health, but large enough to smooth spikes and prevent the PID from overreacting. This can be increased if the
     * health signal is still noisy after consensus, or the PID output is oscillating or unstable.
     */
    private final int AVERAGING_WINDOW_SIZE = 2;
    /** System logger */
    private final Logger logger = System.getLogger(QuorumHealthAggregator.class.getName());
    /** Queue of last AVERAGING_WINDOW_SIZE rounds to compute the health of the quorum */
    private final double[] roundHealths = new double[AVERAGING_WINDOW_SIZE];
    /** The index of the next roundHealths slot to use */
    private int nextRoundIndex = 0;
    /** Keep track of if we have logged a waning about quorum size */
    private boolean loggedWarningOnce = false;
    private final int nodeId;
    /**
     * Constructor for QuorumHealthAggregator. It initializes the round health values to 1.0, which means that the
     * quorum is healthy at the start.
     */
    public QuorumHealthAggregator(int nodeId) {
        this.nodeId = nodeId;
        // always start with a healthy quorum
        Arrays.fill(roundHealths, 1.0);
    }

    /**
     * This is called per round to compute the health of the quorum. It takes the average health of the nodes within the
     * round, and across the last AVERAGING_WINDOW_SIZE rounds. It ignores the bottom 1/3 of the nodes in the round.
     *
     * @param round the new round to process
     * @return the health of the quorum, between 0.0 and 1.0
     */
    public synchronized double computeQuorumHealth(Round round) {
        final double roundHealth = aggregateQuorumHealth(round);
        roundHealths[nextRoundIndex] = roundHealth;
        // update nextRoundIndex
        nextRoundIndex++;
        nextRoundIndex = (nextRoundIndex == AVERAGING_WINDOW_SIZE) ? 0 : nextRoundIndex;
//        if (nodeId==1) System.out.println("roundHealth="+roundHealth+" roundHealths = " + Arrays.toString(roundHealths));
        // compute the average of the last AVERAGING_WINDOW_SIZE rounds
        return DoubleStream.of(roundHealths).average().orElse(0.0);
    }

    /**
     * Aggregates the health of the nodes in a single round. It takes the median health of the nodes, ignoring the
     * bottom 1/3. Averaging all health reports from a single node within a round.
     *
     * @param round the round to process
     * @return the health of the quorum, between 0.0 and 1.0
     */
    private double aggregateQuorumHealth(Round round) {
        final List<Double> sortedNodeHealths = round.events().stream()
                .map(PostConsensusEvent::event)
                .collect(Collectors.groupingBy(
                        Event::nodeId,
                        Collectors.averagingDouble(e -> e.healthPercentage() / 100d)))
                .values().stream()
                .sorted()
                .toList();
//        if (nodeId==1) System.out.println("sortedNodeHealths = " + Arrays.toString(sortedNodeHealths.toArray()));
        if (!loggedWarningOnce && sortedNodeHealths.size() < 4) {// Not enough for BFT safety
            logger.log(System.Logger.Level.WARNING, "Not enough nodes to be BFT safe in health aggregation, " +
                    "only {0} nodes. This is not a problem in small networks, but in larger networks, this could " +
                    "lead to unhealthy nodes being trusted.", sortedNodeHealths.size());
            loggedWarningOnce = true;
        }
        // We need 2/3 of the nodes to be healthy, so we take the bottom 1/3 and ignore them
        final int limit = (2 * sortedNodeHealths.size()) / 3;
        final List<Double> trusted = sortedNodeHealths.subList(0, limit);
        // now take the minimum of the trusted nodes, it is the minimum of the top 2/3 because we do not want to allow
        // any of the top 2/3 to become unhealthy.
        return trusted.getLast();
    }
}
