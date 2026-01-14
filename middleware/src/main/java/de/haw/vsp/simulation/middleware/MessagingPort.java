package de.haw.vsp.simulation.middleware;
import de.haw.vsp.simulation.core.NodeId;
import de.haw.vsp.simulation.core.SimulationMessage;

import java.util.Set;

/**
 * Middleware messaging API (port).
 *
 * <p>This is the only interface the simulation core should depend on for
 * inter-node communication. Implementations may be in-memory (tests) or
 * network-based (e.g., UDP).</p>
 */
public interface MessagingPort {

    /**
     * Send a message to a single receiver.
     *
     * @param receiver receiver node id (must not be null)
     * @param message  message envelope (must not be null)
     */
    void send(NodeId receiver, SimulationMessage message);

    /**
     * Broadcast a message to multiple receivers.
     *
     * @param receivers receiver node ids (must not be null; may be empty)
     * @param message   message envelope (must not be null)
     */
    void broadcast(Set<NodeId> receivers, SimulationMessage message);

    /**
     * Register a handler for inbound messages for the given node id.
     *
     * <p>In in-memory implementations, this usually registers where messages should be delivered.
     * In network implementations, this typically registers the local node's inbound handler.</p>
     *
     * @param nodeId   node id to register for (must not be null)
     * @param handler  handler callback (must not be null)
     */
    void registerHandler(NodeId nodeId, MessageHandler handler);

    /**
     * Unregister the handler (if any) for the given node id.
     *
     * @param nodeId node id to unregister for (must not be null)
     */
    void unregisterHandler(NodeId nodeId);
}
