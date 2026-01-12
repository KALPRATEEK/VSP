package de.haw.vsp.simulation.middleware.codec;

import de.haw.vsp.simulation.middleware.SimulationMessage;

/**
 * Serializes {@link SimulationMessage} into a wire format (e.g., JSON bytes).
 */
@FunctionalInterface
public interface SimulationMessageSerializer {

    byte[] serialize(SimulationMessage message) throws MessageCodecException;
}
