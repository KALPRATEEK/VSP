package de.haw.vsp.simulation.core;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.UUID;

/**
 * Immutable identifier for a simulation instance.
 *
 * SimulationId uniquely identifies a simulation within the system.
 * It is used to reference simulations in control operations and to track
 * multiple concurrent simulations.
 *
 * This record is immutable and fully JSON-serializable.
 */
public record SimulationId(@JsonValue String value) {

    /**
     * Canonical constructor with validation.
     *
     * @param value the string representation of the simulation ID (must not be null or blank)
     * @throws IllegalArgumentException if validation fails
     */
    @JsonCreator
    public SimulationId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "SimulationId value must not be null or blank, but was: " +
                            (value == null ? "null" : "'" + value + "'")
            );
        }
    }

    /**
     * Creates a new SimulationId with a randomly generated UUID.
     *
     * @return a new SimulationId with a unique UUID value
     */
    public static SimulationId generate() {
        return new SimulationId(UUID.randomUUID().toString());
    }

    @Override
    public String toString() {
        return value;
    }
}
