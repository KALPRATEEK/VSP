package de.haw.vsp.simulation.core;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Configuration object bundling network, algorithm, and parameters.
 *
 * Represents a complete simulation scenario that can be saved and loaded.
 * Bundling these three aspects ensures that:
 * - Network topology (nodeCount, topologyType)
 * - Algorithm selection (algorithmId)
 * - Simulation parameters (randomSeed, maxSteps, messageDelayMillis)
 * are preserved together, enabling reproducible simulations.
 *
 * This record is immutable and fully JSON-serializable.
 */
public record SimulationConfig(
        @JsonProperty("networkConfig") NetworkConfig networkConfig,
        @JsonProperty("algorithmId") String algorithmId,
        @JsonProperty("defaultParameters") SimulationParameters defaultParameters
) {

    /**
     * Canonical constructor with validation.
     *
     * @param networkConfig     network topology configuration (must not be null)
     * @param algorithmId       algorithm identifier (must not be null or blank)
     * @param defaultParameters default simulation runtime parameters (must not be null)
     * @throws IllegalArgumentException if validation fails
     */
    @JsonCreator
    public SimulationConfig {
        if (networkConfig == null) {
            throw new IllegalArgumentException("networkConfig must not be null");
        }
        if (algorithmId == null || algorithmId.isBlank()) {
            throw new IllegalArgumentException(
                    "algorithmId must not be null or blank, but was: " +
                            (algorithmId == null ? "null" : "'" + algorithmId + "'")
            );
        }
        if (defaultParameters == null) {
            throw new IllegalArgumentException("defaultParameters must not be null");
        }
    }
}

