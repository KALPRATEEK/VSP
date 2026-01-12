package de.haw.vsp.simulation.middleware.codec;

import de.haw.vsp.simulation.middleware.SimulationMessage;

/**
 * Deserializes a wire representation (e.g., JSON bytes) into {@link SimulationMessage}.
 */
@FunctionalInterface
public interface SimulationMessageDeserializer {

    SimulationMessage deserialize(byte[] bytes) throws MessageCodecException;
}
