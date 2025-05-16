package com.hashgraph.dynamicthrottles.impl;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * AdaptivePIDRateController is a PID-based rate controller with adaptive gain tuning.
 * It adjusts the global intake rate based on a health signal (e.g., quorum health in a distributed system).
 * The controller adapts its PID coefficients based on recent control performance and error trends.
 *
 * Usage:
 * - Call update(targetHealth, currentHealth) once per control round (e.g., per consensus round).
 * - The controller outputs a new intake rate (tokens per second).
 * - Gains are auto-tuned based on recent error and output stability.
 */
public class AdaptivePIDRateController {
//    private static final double INITIAL_KP = 3000.0;    // Proportional gain — how strongly we react to error
//    private static final double INITIAL_KI = 500.0;     // Integral gain — handles steady-state error over time
//    private static final double INITIAL_KD = 0.0;       // Derivative gain — optional, start with zero
    private static final double INITIAL_KP = 100.0;    // Proportional gain — how strongly we react to error
    private static final double INITIAL_KI = 10.0;     // Integral gain — handles steady-state error over time
    private static final double INITIAL_KD = 0.0;       // Derivative gain — optional, start with zero
    private static final double INITIAL_MIN_RATE = 2.0;      // Minimum allowed global intake rate (txn/sec)
    private static final double INITIAL_MAX_RATE = 10000.0;     // Maximum allowed global intake rate
    private static final double INITIAL_RATE = 5000.0;  // Starting rate (mid-range)

    private double kp;
    private double ki;
    private double kd;

    private final double minRate;
    private final double maxRate;

    private double integral = 0.0;
    private double previousError = 0.0;
    private double currentRate;
    private long lastUpdateTime;

    private final Deque<Double> recentErrors = new ArrayDeque<>();
    private final Deque<Double> recentRates = new ArrayDeque<>();
    private final int windowSize = 5; // Last 5 rounds

    // Adaptive gain tuning parameters
    private final double gainAdjustRate = 0.05; // How aggressively to adapt gains
    private final double oscillationThreshold = 0.1; // Threshold for detecting rate instability

    public AdaptivePIDRateController() {
        this.kp = INITIAL_KP;
        this.ki = INITIAL_KI;
        this.kd = INITIAL_KD;
        this.minRate = INITIAL_MIN_RATE;
        this.maxRate = INITIAL_MAX_RATE;
        this.currentRate = INITIAL_RATE;
        this.lastUpdateTime = System.currentTimeMillis();
    }

    /**
     * Update the PID controller based on current health feedback.
     *
     * @param targetHealth desired health value (e.g. 0.8 for 80%)
     * @param currentHealth observed quorum health in current round
     * @return updated intake rate (tokens/sec)
     */
    public synchronized double update(double targetHealth, double currentHealth) {
        long now = System.currentTimeMillis();
        double deltaTime = (now - lastUpdateTime) / 1000.0;
        lastUpdateTime = now;

        // Reverse the error calculation
        double error =  currentHealth - targetHealth;
        integral += error * deltaTime;
        double derivative = (error - previousError) / deltaTime;
        previousError = error;

        double adjustment = kp * error + ki * integral + kd * derivative;
        currentRate = Math.max(minRate, Math.min(maxRate, currentRate + adjustment));

        recordRecentHistory(error, currentRate);
        autoTuneGains();

        return currentRate;
    }

    private void recordRecentHistory(double error, double rate) {
        if (recentErrors.size() >= windowSize) recentErrors.removeFirst();
        if (recentRates.size() >= windowSize) recentRates.removeFirst();
        recentErrors.addLast(error);
        recentRates.addLast(rate);
    }

    /**
     * Explanation:
     * Oscillation Handling: Reduces all gains, including kd, when the system oscillates.
     * Undercorrection Handling: Increases kp and ki when the system undercorrects.
     * Derivative Gain Adjustment: Dynamically increases kd when rapid error changes are detected, ensuring it contributes to damping oscillations.
     */
    private void autoTuneGains() {
        if (recentErrors.size() < windowSize) return; // Wait for history

        double errorVariance = computeVariance(recentErrors);
        double rateVariance = computeVariance(recentRates);

        if (rateVariance > oscillationThreshold) {
            // System is oscillating — reduce gains
            kp *= (1.0 - gainAdjustRate);
            ki *= (1.0 - gainAdjustRate);
            kd *= (1.0 - gainAdjustRate);
        } else if (errorVariance > 0.01 && Math.abs(recentErrors.getLast()) > 0.05) {
            // System is undercorrecting — increase proportional/integral gains
            kp *= (1.0 + gainAdjustRate);
            ki *= (1.0 + gainAdjustRate);
        }

        // Adjust kd based on rapid error changes
        double recentDerivative = Math.abs(recentErrors.getLast() - recentErrors.getFirst()) / windowSize;
        if (recentDerivative > 0.1) { // Threshold for significant error change
            kd *= (1.0 + gainAdjustRate);
        }
    }

    private double computeVariance(Deque<Double> values) {
        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        return values.stream().mapToDouble(v -> (v - mean) * (v - mean)).average().orElse(0.0);
    }

    public synchronized double getCurrentRate() {
        return currentRate;
    }

    public synchronized double getKp() { return kp; }
    public synchronized double getKi() { return ki; }
    public synchronized double getKd() { return kd; }

    public synchronized String toString() {
        return String.format("[kp=%.2f, ki=%.2f, kd=%.2f, rate=%.2f]",
                kp, ki, kd, currentRate);
    }
}
