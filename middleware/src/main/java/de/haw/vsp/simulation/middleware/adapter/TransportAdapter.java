package de.haw.vsp.simulation.middleware.adapter;

import de.haw.vsp.simulation.core.NodeId;
import de.haw.vsp.simulation.core.SimulationMessage;

import java.io.Closeable;

/**
 * Low-level transport abstraction used by {@link de.haw.vsp.simulation.middleware.MessagingPortImpl}.
 *
 * Implementations encapsulate concrete communication mechanisms (e.g. UDP, in-memory).
 */
public interface TransportAdapter extends Closeable {

    /**
     * Send message asynchronously (best-effort).
     */
    void send(SimulationMessage message);

    /**
     * Register callback for incoming messages to the local node.
     */
    void onReceive(ReceiveCallback callback);

    /**
     * @return the local node id this adapter is bound to
     */
    NodeId localNode();

    @FunctionalInterface
    interface ReceiveCallback {
        void onMessage(SimulationMessage message);
    }
}
