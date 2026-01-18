package de.haw.vsp.simulation.middleware.adapter;

import de.haw.vsp.simulation.core.NodeId;
import de.haw.vsp.simulation.core.SimulationMessage;

import java.io.Closeable;

/**
 * Low-level transport abstraction used by MessagingPortImpl.
 *
 * Note: For udp-docker, adapters are bound to a single local node.
 * For virtual mode, the adapter may represent a shared router.
 */
public interface TransportAdapter extends Closeable {

    /**
     * @return true if the message was accepted/enqueued for asynchronous transmission.
     *         false if it was dropped immediately (e.g., queue full, unknown address, oversize).
     *
     * Note: immediate drops should be communicated via the boolean return.
     *       Use onError(...) for asynchronous errors (decode failures, inbound queue full, socket errors).
     */
    boolean send(SimulationMessage message);

    /** Register callback for incoming messages delivered by the transport. */
    void onReceive(ReceiveCallback callback);

    /**
     * Optional callback for transport-level errors/drops (queue full, decode errors, etc).
     * Default: no-op.
     */
    default void onError(ErrorCallback callback) { /* no-op */ }

    /** @return local node id this adapter is bound to (or a sentinel in virtual mode). */
    NodeId localNode();

    @FunctionalInterface
    interface ReceiveCallback {
        void onMessage(SimulationMessage message);
    }

    @FunctionalInterface
    interface ErrorCallback {
        void onError(NodeId nodeId, NodeId peer, String message);
    }
}
