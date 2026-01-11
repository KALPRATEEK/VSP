package de.haw.vsp.simulation.middleware.inmemory;

import de.haw.vsp.simulation.middleware.MessageHandler;
import de.haw.vsp.simulation.middleware.MessagingPort;
import de.haw.vsp.simulation.middleware.NodeId;
import de.haw.vsp.simulation.middleware.SimulationMessage;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * In-memory implementation of {@link MessagingPort}.
 *
 * <p>Intended for tests and local runs without a real network transport.</p>
 * <p>Semantics are "best effort": if a receiver has no registered handler,
 * the message is dropped.</p>
 */
public final class InMemoryMessagingPort implements MessagingPort {

    private final ConcurrentMap<NodeId, MessageHandler> handlers = new ConcurrentHashMap<>();

    @Override
    public void send(NodeId receiver, SimulationMessage message) {
        Objects.requireNonNull(receiver, "receiver");
        Objects.requireNonNull(message, "message");

        MessageHandler handler = handlers.get(receiver);
        if (handler == null) {
            // Best-effort semantics: drop if receiver not registered.
            return;
        }

        handler.onMessage(message);
    }

    @Override
    public void broadcast(Set<NodeId> receivers, SimulationMessage message) {
        Objects.requireNonNull(receivers, "receivers");
        Objects.requireNonNull(message, "message");

        for (NodeId receiver : receivers) {
            if (receiver == null) continue;
            send(receiver, message);
        }
    }

    @Override
    public void registerHandler(NodeId nodeId, MessageHandler handler) {
        Objects.requireNonNull(nodeId, "nodeId");
        Objects.requireNonNull(handler, "handler");

        handlers.put(nodeId, handler);
    }

    @Override
    public void unregisterHandler(NodeId nodeId) {
        Objects.requireNonNull(nodeId, "nodeId");
        handlers.remove(nodeId);
    }

    /**
     * Visible for tests / diagnostics.
     */
    public int registeredHandlerCount() {
        return handlers.size();
    }

    /**
     * Visible for tests / diagnostics.
     */
    public boolean isRegistered(NodeId nodeId) {
        return handlers.containsKey(nodeId);
    }
}
