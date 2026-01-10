package de.haw.vsp.simulation.core;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Simulation runtime parameters.
 *
 * Defines the configuration for a single simulation run:
 * - randomSeed: ensures deterministic behavior across runs
 * - maxSteps: maximum number of simulation steps (must be > 0)
 * - messageDelayMillis: artificial message delay in milliseconds (must be >= 0)
 *
 * This record is immutable and JSON-serializable.
 */
public record SimulationParameters(
        @JsonProperty("randomSeed") long randomSeed,
        @JsonProperty("maxSteps") int maxSteps,
        @JsonProperty("messageDelayMillis") int messageDelayMillis
) {

    /**
     * Canonical constructor with validation.
     *
     * @param randomSeed         seed for deterministic randomness
     * @param maxSteps           maximum number of simulation steps (must be > 0)
     * @param messageDelayMillis artificial delay per message in milliseconds (must be >= 0)
     * @throws IllegalArgumentException if validation fails
     */
    @JsonCreator
    public SimulationParameters {
        if (maxSteps <= 0) {
            throw new IllegalArgumentException(
                    "maxSteps must be greater than 0, but was: " + maxSteps
            );
        }
        if (messageDelayMillis < 0) {
            throw new IllegalArgumentException(
                    "messageDelayMillis must be non-negative, but was: " + messageDelayMillis
            );
        }
    }
}

