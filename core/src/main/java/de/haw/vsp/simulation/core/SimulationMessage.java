package de.haw.vsp.simulation.core;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Immutable message exchanged between simulation nodes.
 *
 * Represents a message sent from one node to another during simulation execution.
 * Messages contain sender/receiver information, a message type, and an optional payload.
 *
 * This record is immutable and fully JSON-serializable for transport over the network.
 * Wire schema (JSON):
 * {
 *   "sender":   "nodeId",
 *   "receiver": "nodeId",
 *   "msgType":  "MESSAGE_TYPE",
 *   "payload":  "...",
 *   "seq":      42        // optional
 * }
 */
public record SimulationMessage(
        @JsonProperty("sender") NodeId sender,
        @JsonProperty("receiver") NodeId receiver,
        @JsonProperty("messageType") String messageType,
        @JsonProperty("payload") Object payload,
        @JsonProperty("seq") Long seq
) {

    /**
     * Canonical constructor with validation.
     *
     * @param sender      the node that sent the message (must not be null)
     * @param receiver    the node that should receive the message (must not be null)
     * @param messageType the type/category of the message (must not be null or blank)
     * @param payload     optional message payload (may be null)
     * @param seq         optional sequence number (must be >= 0 if present)
     * @throws IllegalArgumentException if validation fails
     */
    @JsonCreator
    public SimulationMessage {
        if (sender == null) {
            throw new IllegalArgumentException("sender must not be null");
        }
        if (receiver == null) {
            throw new IllegalArgumentException("receiver must not be null");
        }
        if (messageType == null || messageType.isBlank()) {
            throw new IllegalArgumentException(
                    "messageType must not be null or blank, but was: " +
                            (messageType == null ? "null" : "'" + messageType + "'")
            );
        }
        if (seq != null && seq < 0) {
            throw new IllegalArgumentException("seq must be >= 0, but was: " + seq);
        }
    }
}

