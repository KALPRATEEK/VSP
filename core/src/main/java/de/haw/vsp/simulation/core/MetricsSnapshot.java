package de.haw.vsp.simulation.core;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Immutable metrics aggregation snapshot.
 *
 * Captures the current state and progress of a simulation run, including:
 * - Temporal metrics (simulated time, real time)
 * - Resource metrics (message count, rounds)
 * - Algorithm outcomes (convergence, leader election)
 *
 * This record is designed for periodic polling by monitoring UIs and analysis tools.
 * It provides a compact, aggregated view of simulation behavior suitable for:
 * - Real-time monitoring dashboards
 * - Post-simulation analysis
 * - Teaching and demonstration purposes
 *
 * This record is immutable and fully JSON-serializable.
 */
public record MetricsSnapshot(
        @JsonProperty("simulatedTime") long simulatedTime,
        @JsonProperty("realTimeMillis") long realTimeMillis,
        @JsonProperty("messageCount") long messageCount,
        @JsonProperty("rounds") long rounds,
        @JsonProperty("converged") boolean converged,
        @JsonProperty("leaderId") String leaderId
) {

    /**
     * Canonical constructor with validation.
     *
     * @param simulatedTime  simulation time in logical units (must be non-negative)
     * @param realTimeMillis wall-clock time in milliseconds (must be non-negative)
     * @param messageCount   total number of messages sent (must be non-negative)
     * @param rounds         number of simulation rounds completed (must be non-negative)
     * @param converged      whether the algorithm has converged
     * @param leaderId       ID of the elected leader (may be null if no leader elected or not applicable)
     * @throws IllegalArgumentException if validation fails
     */
    @JsonCreator
    public MetricsSnapshot {
        if (simulatedTime < 0) {
            throw new IllegalArgumentException(
                    "simulatedTime must be non-negative, but was: " + simulatedTime
            );
        }
        if (realTimeMillis < 0) {
            throw new IllegalArgumentException(
                    "realTimeMillis must be non-negative, but was: " + realTimeMillis
            );
        }
        if (messageCount < 0) {
            throw new IllegalArgumentException(
                    "messageCount must be non-negative, but was: " + messageCount
            );
        }
        if (rounds < 0) {
            throw new IllegalArgumentException(
                    "rounds must be non-negative, but was: " + rounds
            );
        }
        // leaderId may be null (e.g., no leader elected yet, or algorithm doesn't elect leaders)
    }

    /**
     * Creates an initial/empty MetricsSnapshot with all values at zero.
     *
     * @return new MetricsSnapshot representing the start of a simulation
     */
    public static MetricsSnapshot initial() {
        return new MetricsSnapshot(0L, 0L, 0L, 0L, false, null);
    }

    /**
     * Creates a MetricsSnapshot with specified values and no leader.
     *
     * @param simulatedTime  simulation time in logical units
     * @param realTimeMillis wall-clock time in milliseconds
     * @param messageCount   total number of messages sent
     * @param rounds         number of simulation rounds completed
     * @param converged      whether the algorithm has converged
     * @return new MetricsSnapshot without a leader
     */
    public static MetricsSnapshot withoutLeader(
            long simulatedTime,
            long realTimeMillis,
            long messageCount,
            long rounds,
            boolean converged
    ) {
        return new MetricsSnapshot(simulatedTime, realTimeMillis, messageCount, rounds, converged, null);
    }
}

