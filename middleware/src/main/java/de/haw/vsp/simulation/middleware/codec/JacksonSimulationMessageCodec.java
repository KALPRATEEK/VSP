package de.haw.vsp.simulation.middleware.codec;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import de.haw.vsp.simulation.core.SimulationMessage;

import java.util.Objects;

/**
 * JSON (de)serialization for {@link SimulationMessage} using Jackson.
 */
public final class JacksonSimulationMessageCodec
        implements SimulationMessageSerializer, SimulationMessageDeserializer {

    private final ObjectMapper mapper;

    /**
     * Creates a codec with a reasonable default ObjectMapper configuration.
     */
    public JacksonSimulationMessageCodec() {
        this(defaultMapper());
    }

    public JacksonSimulationMessageCodec(ObjectMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    @Override
    public byte[] serialize(SimulationMessage message) throws MessageCodecException {
        Objects.requireNonNull(message, "message");
        try {
            return mapper.writeValueAsBytes(message);
        } catch (JsonProcessingException e) {
            throw new MessageCodecException("Failed to serialize SimulationMessage to JSON", e);
        }
    }

    @Override
    public SimulationMessage deserialize(byte[] bytes) throws MessageCodecException {
        if (bytes == null || bytes.length == 0) {
            throw new MessageCodecException("Cannot deserialize: payload is null/empty");
        }
        try {
            return mapper.readValue(bytes, SimulationMessage.class);
        } catch (Exception e) {
            throw new MessageCodecException("Failed to deserialize SimulationMessage from JSON", e);
        }
    }

    private static ObjectMapper defaultMapper() {
        // JsonMapper is the recommended builder for Jackson 2.10+.
        return JsonMapper.builder()
                // In distributed settings, it's helpful if older/newer nodes don't crash on extra fields.
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true)
                .build();
    }
}
