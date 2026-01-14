package de.haw.vsp.simulation.middleware;

import de.haw.vsp.simulation.core.SimulationMessage;

/**
 * Callback for inbound middleware messages.
 */
@FunctionalInterface
public interface MessageHandler {

    void onMessage(SimulationMessage msg);
}
