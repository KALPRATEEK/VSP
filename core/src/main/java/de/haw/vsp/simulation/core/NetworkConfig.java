package de.haw.vsp.simulation.core;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Immutable configuration for network topology.
 *
 * Defines the number of nodes and the topology type for a simulation network.
 * All instances must satisfy validation constraints:
 * - nodeCount must be greater than 0
 * - topologyType must not be null
 */
public record NetworkConfig(
        int nodeCount,
        TopologyType topologyType
) {
    /**
     * Compact constructor with validation.
     *
     * @throws IllegalArgumentException if nodeCount <= 0 or topologyType is null
     */
    @JsonCreator
    public NetworkConfig(
            @JsonProperty("nodeCount") int nodeCount,
            @JsonProperty("topologyType") TopologyType topologyType
    ) {
        if (nodeCount <= 0) {
            throw new IllegalArgumentException("nodeCount must be greater than 0, but was: " + nodeCount);
        }
        if (topologyType == null) {
            throw new IllegalArgumentException("topologyType must not be null");
        }
        this.nodeCount = nodeCount;
        this.topologyType = topologyType;
    }
}

