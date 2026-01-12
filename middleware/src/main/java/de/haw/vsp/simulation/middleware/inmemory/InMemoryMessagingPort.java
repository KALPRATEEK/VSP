package de.haw.vsp.simulation.middleware.inmemory;

import de.haw.vsp.simulation.middleware.MessageHandler;
import de.haw.vsp.simulation.middleware.MessagingPort;
import de.haw.vsp.simulation.middleware.NodeId;
import de.haw.vsp.simulation.middleware.SimulationMessage;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * In-memory implementation of {@link MessagingPort}.
 *
 * <p>Semantics are best-effort: if a receiver has no registered handler,
 * the message is dropped.</p>
 */
public final class InMemoryMessagingPort implements MessagingPort {

    private static final Logger LOG = Logger.getLogger(InMemoryMessagingPort.class.getName());

    private final ConcurrentMap<NodeId, MessageHandler> handlers = new ConcurrentHashMap<>();

    private final AtomicLong sentCount = new AtomicLong();
    private final AtomicLong deliveredCount = new AtomicLong();
    private final AtomicLong droppedCount = new AtomicLong();

    @Override
    public void send(NodeId receiver, SimulationMessage message) {
        Objects.requireNonNull(receiver, "receiver");
        Objects.requireNonNull(message, "message");

        sentCount.incrementAndGet();

        MessageHandler handler = handlers.get(receiver);
        if (handler == null) {
            droppedCount.incrementAndGet();
            LOG.log(Level.FINE, () -> "InMemory drop: no handler for receiver=" + receiver
                    + " msgId=" + message.messageId() + " type=" + message.type());
            return;
        }

        deliveredCount.incrementAndGet();
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
        LOG.log(Level.FINE, () -> "InMemory registered handler for " + nodeId);
    }

    @Override
    public void unregisterHandler(NodeId nodeId) {
        Objects.requireNonNull(nodeId, "nodeId");
        handlers.remove(nodeId);
        LOG.log(Level.FINE, () -> "InMemory unregistered handler for " + nodeId);
    }

    // ---- Observability getters ----

    public long sentCount() {
        return sentCount.get();
    }

    public long deliveredCount() {
        return deliveredCount.get();
    }

    public long droppedCount() {
        return droppedCount.get();
    }

    public int registeredHandlerCount() {
        return handlers.size();
    }

    public boolean isRegistered(NodeId nodeId) {
        return handlers.containsKey(nodeId);
    }
}
