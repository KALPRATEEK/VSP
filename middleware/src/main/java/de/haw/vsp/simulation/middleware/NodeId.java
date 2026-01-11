package de.haw.vsp.simulation.middleware;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Identifier of a simulation node within the distributed system.
 * <p>
 * Intentionally a small wrapper around a {@link String} to avoid mixing node ids
 * with arbitrary strings.
 */
public record NodeId(String value) {

    @JsonCreator
    public NodeId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("NodeId must not be null/blank");
        }
    }

    @JsonValue
    @Override
    public String toString() {
        return value;
    }
}
