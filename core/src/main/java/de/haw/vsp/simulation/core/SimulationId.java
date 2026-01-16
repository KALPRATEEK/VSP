package de.haw.vsp.simulation.core;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Immutable identifier for a simulation instance.
 *
 * SimulationId uniquely identifies a simulation within the system.
 * It is used to address simulations in the SimulationControl API.
 *
 * This record is immutable and fully JSON-serializable.
 */
public record SimulationId(@JsonValue String value) implements Comparable<SimulationId> {

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

    @Override
    public int compareTo(SimulationId other) {
        return this.value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
