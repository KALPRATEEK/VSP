package de.haw.vsp.simulation.core;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Immutable read-only snapshot of the current simulation state for visualization.
 *
 * This snapshot is derived from the event stream and internal state of the simulation.
 * It provides a consistent view of:
 * - Network topology (nodes and their connections)
 * - Current state of each node
 * - Recent events that affect visualization
 *
 * This record is immutable and fully JSON-serializable.
 * The snapshot does not affect the simulation state and can be safely polled frequently.
 */
public record VisualizationSnapshot(
        @JsonProperty("nodes") List<NodeState> nodes,
        @JsonProperty("topology") Map<String, Set<String>> topology,
        @JsonProperty("timestamp") long timestamp
) {
    /**
     * Canonical constructor with validation.
     *
     * @param nodes    list of node states (must not be null)
     * @param topology map from node ID to set of neighbor node IDs (must not be null)
     * @param timestamp timestamp when snapshot was created (must be non-negative)
     * @throws IllegalArgumentException if validation fails
     */
    @JsonCreator
    public VisualizationSnapshot {
        if (nodes == null) {
            throw new IllegalArgumentException("nodes must not be null");
        }
        if (topology == null) {
            throw new IllegalArgumentException("topology must not be null");
        }
        if (timestamp < 0) {
            throw new IllegalArgumentException("timestamp must be non-negative, but was: " + timestamp);
        }
    }

    /**
     * Represents the current state of a single node in the visualization.
     */
    public record NodeState(
            @JsonProperty("nodeId") String nodeId,
            @JsonProperty("state") String state,
            @JsonProperty("isLeader") boolean isLeader
    ) {
        /**
         * Canonical constructor with validation.
         *
         * @param nodeId   identifier of the node (must not be null or blank)
         * @param state    current state of the node (must not be null)
         * @param isLeader whether this node is the current leader
         * @throws IllegalArgumentException if validation fails
         */
        @JsonCreator
        public NodeState {
            if (nodeId == null || nodeId.isBlank()) {
                throw new IllegalArgumentException(
                        "nodeId must not be null or blank, but was: " +
                                (nodeId == null ? "null" : "'" + nodeId + "'")
                );
            }
            if (state == null) {
                throw new IllegalArgumentException("state must not be null");
            }
        }
    }
}
