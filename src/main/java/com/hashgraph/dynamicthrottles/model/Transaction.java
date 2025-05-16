package com.hashgraph.dynamicthrottles.model;

import static com.hashgraph.dynamicthrottles.Node.NANOS_PER_SECOND;

import java.util.concurrent.locks.LockSupport;

/**
 * A simple pretend transaction class that simulates work by sleeping for a given amount of time. The amount of work is
 * set in constructor and is in nanoseconds. The amount of work is capped at 250 ms. This class is used to simulate
 * transactions in the system.
 *
 * @param amountOfWorkInNanos the amount of work to be done in nanoseconds
 */
public record Transaction(int amountOfWorkInNanos) {
//    public static final int MAX_AMOUNT_OF_WORK_IN_NANOS = 250_000_000; // 1/4 second
    public static final int MAX_AMOUNT_OF_WORK_IN_NANOS = 1_000_000; // 1 ms
    public static final int MIN_AMOUNT_OF_WORK_15K_TPS_IN_NANOS = NANOS_PER_SECOND / 15_000; // 15K TPS

    public Transaction {
        if (amountOfWorkInNanos <= 0) {
            throw new IllegalArgumentException("amountOfWorkInNanos must be non-negative and non-zero");
        }
        if (amountOfWorkInNanos > MAX_AMOUNT_OF_WORK_IN_NANOS) {
            throw new IllegalArgumentException("amountOfWorkInNanos must be less than " + MAX_AMOUNT_OF_WORK_IN_NANOS);
        }
    }

    public void execute() {
        LockSupport.parkNanos(amountOfWorkInNanos);
    }
}
