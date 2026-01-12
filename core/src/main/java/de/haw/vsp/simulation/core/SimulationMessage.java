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
 */
public record SimulationMessage(
        @JsonProperty("sender") NodeId sender,
        @JsonProperty("receiver") NodeId receiver,
        @JsonProperty("messageType") String messageType,
        @JsonProperty("payload") Object payload
) {

    /**
     * Canonical constructor with validation.
     *
     * @param sender      the node that sent the message (must not be null)
     * @param receiver    the node that should receive the message (must not be null)
     * @param messageType the type/category of the message (must not be null or blank)
     * @param payload     optional message payload (may be null)
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
    }
}

