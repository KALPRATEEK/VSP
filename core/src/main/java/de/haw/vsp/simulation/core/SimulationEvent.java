package de.haw.vsp.simulation.core;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Unified domain model for simulation events.
 *
 * Represents events that occur during simulation execution, such as:
 * - Messages sent between nodes
 * - State changes in nodes
 * - Algorithm-specific events (e.g., leader election)
 * - System events (start, stop, errors)
 *
 * Events are emitted by the simulation core and consumed by:
 * - Visualization (UI via WebSocket)
 * - Logging (for debugging and analysis)
 * - Metrics (for aggregation)
 *
 * This record is immutable and fully JSON-serializable.
 */
public record SimulationEvent(
        @JsonProperty("timestamp") long timestamp,
        @JsonProperty("type") String type,
        @JsonProperty("nodeId") String nodeId,
        @JsonProperty("peerId") String peerId,
        @JsonProperty("payloadSummary") String payloadSummary
) {

    /**
     * Canonical constructor with validation.
     *
     * @param timestamp      simulation time or wall-clock time in milliseconds
     * @param type           event type identifier (must not be null or blank)
     * @param nodeId         ID of the node that generated the event (must not be null or blank)
     * @param peerId         ID of the peer node involved (may be null for non-message events)
     * @param payloadSummary short, UI-friendly summary of the event payload (must not be null)
     * @throws IllegalArgumentException if validation fails
     */
    @JsonCreator
    public SimulationEvent {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException(
                    "type must not be null or blank, but was: " +
                            (type == null ? "null" : "'" + type + "'")
            );
        }
        if (nodeId == null || nodeId.isBlank()) {
            throw new IllegalArgumentException(
                    "nodeId must not be null or blank, but was: " +
                            (nodeId == null ? "null" : "'" + nodeId + "'")
            );
        }
        if (payloadSummary == null) {
            throw new IllegalArgumentException("payloadSummary must not be null");
        }
        // peerId may be null (e.g., for system events, state changes without peer involvement)
    }

    /**
     * Creates a SimulationEvent with no peer involvement.
     *
     * @param timestamp      simulation time or wall-clock time in milliseconds
     * @param type           event type identifier
     * @param nodeId         ID of the node that generated the event
     * @param payloadSummary short, UI-friendly summary of the event payload
     * @return new SimulationEvent with peerId set to null
     */
    public static SimulationEvent withoutPeer(
            long timestamp,
            String type,
            String nodeId,
            String payloadSummary
    ) {
        return new SimulationEvent(timestamp, type, nodeId, null, payloadSummary);
    }
}

