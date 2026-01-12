package de.haw.vsp.simulation.core;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Immutable identifier for a simulation node.
 *
 * NodeId uniquely identifies a node within a simulation.
 * It is used for routing messages, identifying neighbors, and determining leader election outcomes.
 *
 * This record is immutable and fully JSON-serializable.
 */
public record NodeId(@JsonValue String value) implements Comparable<NodeId> {

    /**
     * Canonical constructor with validation.
     *
     * @param value the string representation of the node ID (must not be null or blank)
     * @throws IllegalArgumentException if validation fails
     */
    @JsonCreator
    public NodeId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "NodeId value must not be null or blank, but was: " +
                            (value == null ? "null" : "'" + value + "'")
            );
        }
    }

    @Override
    public int compareTo(NodeId other) {
        return this.value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}

