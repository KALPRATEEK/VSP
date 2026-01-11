package de.haw.vsp.simulation.middleware;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Objects;
import java.util.UUID;

/**
 * Wire-level message envelope used by the middleware.
 * <p>
 * Domain-level events/commands should live in the core module; this type is
 * intentionally generic so different algorithms can exchange structured payloads.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SimulationMessage(
        String messageId,
        NodeId sender,
        NodeId receiver,
        String type,
        JsonNode payload,
        long timestampEpochMillis
) {

    @JsonCreator
    public SimulationMessage(
            @JsonProperty("messageId") String messageId,
            @JsonProperty("sender") NodeId sender,
            @JsonProperty("receiver") NodeId receiver,
            @JsonProperty("type") String type,
            @JsonProperty("payload") JsonNode payload,
            @JsonProperty("timestampEpochMillis") long timestampEpochMillis
    ) {
        if (messageId == null || messageId.isBlank()) {
            throw new IllegalArgumentException("messageId must not be null/blank");
        }
        if (sender == null) {
            throw new IllegalArgumentException("sender must not be null");
        }
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("type must not be null/blank");
        }
        this.messageId = messageId;
        this.sender = sender;
        this.receiver = receiver;
        this.type = type;
        this.payload = payload;
        this.timestampEpochMillis = timestampEpochMillis;
    }

    /**
     * Convenience factory for a message with a generated messageId and current timestamp.
     */
    public static SimulationMessage of(NodeId sender, NodeId receiver, String type, JsonNode payload) {
        Objects.requireNonNull(sender, "sender");
        return new SimulationMessage(
                UUID.randomUUID().toString(),
                sender,
                receiver,
                type,
                payload,
                System.currentTimeMillis()
        );
    }
}
