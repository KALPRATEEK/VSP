package de.haw.vsp.simulation.middleware;

/**
 * Callback for inbound middleware messages.
 */
@FunctionalInterface
public interface MessageHandler {

    void onMessage(SimulationMessage msg);
}
