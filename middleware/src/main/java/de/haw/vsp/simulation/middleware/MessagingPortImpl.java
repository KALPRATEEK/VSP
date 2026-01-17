package de.haw.vsp.simulation.middleware;

import de.haw.vsp.simulation.core.EventType;
import de.haw.vsp.simulation.core.NodeId;
import de.haw.vsp.simulation.core.SimulationEvent;
import de.haw.vsp.simulation.core.SimulationEventPublisher;
import de.haw.vsp.simulation.core.SimulationMessage;
import de.haw.vsp.simulation.middleware.adapter.TransportAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Implementation of {@link MessagingPort} that delegates actual message
 * transport to a pluggable {@link TransportAdapter}.
 *
 * This class adds validation, event publishing and handler management.
 */
public final class MessagingPortImpl implements MessagingPort, Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(MessagingPortImpl.class);

    private final TransportAdapter adapter;
    private final SimulationEventPublisher eventPublisher; // may be null
    private final ConcurrentMap<NodeId, MessageHandler> handlers = new ConcurrentHashMap<>();

    public MessagingPortImpl(TransportAdapter adapter, SimulationEventPublisher eventPublisher) {
        this.adapter = Objects.requireNonNull(adapter, "adapter");
        this.eventPublisher = eventPublisher;

        // wire incoming messages
        adapter.onReceive(this::handleIncoming);
    }

    @Override
    public void send(NodeId receiver, SimulationMessage message) {
        Objects.requireNonNull(receiver, "receiver");
        Objects.requireNonNull(message, "message");

        if (!receiver.equals(message.receiver())) {
            publishError(message.sender(), receiver, "receiver mismatch");
            return;
        }
        if (!adapter.localNode().equals(message.sender())) {
            publishError(adapter.localNode(), receiver, "sender mismatch");
            return;
        }

        publish(EventType.MESSAGE_SENT, message.sender(), message.receiver(), summary(message));
        adapter.send(message);
    }

    @Override
    public void broadcast(Set<NodeId> receivers, SimulationMessage baseMessage) {
        Objects.requireNonNull(receivers, "receivers");
        for (NodeId r : receivers) {
            if (r == null) continue;
            SimulationMessage msg = baseMessage;
            if (!Objects.equals(r, baseMessage.receiver())) {
                msg = new SimulationMessage(baseMessage.sender(), r,
                        baseMessage.messageType(), baseMessage.payload(), baseMessage.seq());
            }
            send(r, msg);
        }
    }

    @Override
    public void registerHandler(NodeId nodeId, MessageHandler handler) {
        Objects.requireNonNull(nodeId, "nodeId");
        Objects.requireNonNull(handler, "handler");
        handlers.put(nodeId, handler);
        LOG.debug("Handler registered for {}", nodeId);
    }

    @Override
    public void unregisterHandler(NodeId nodeId) {
        handlers.remove(nodeId);
    }

    @Override
    public void close() {
        try {
            adapter.close();
        } catch (Exception ignored) {}
        handlers.clear();
    }

    private void handleIncoming(SimulationMessage msg) {
        if (msg == null) return;
        NodeId receiver = msg.receiver();
        MessageHandler handler = handlers.get(receiver);

        if (handler == null) {
            publishError(receiver, msg.sender(), "no handler registered");
            return;
        }

        publish(EventType.MESSAGE_RECEIVED, receiver, msg.sender(), summary(msg));
        try {
            handler.onMessage(msg);
        } catch (RuntimeException e) {
            publishError(receiver, msg.sender(), "handler error: " + e.getMessage());
            LOG.debug("Handler error at {} for message {}", receiver, msg, e);
        }
    }

    private void publish(EventType type, NodeId nodeId, NodeId peer, String summary) {
        if (eventPublisher == null) return;
        eventPublisher.publish(new SimulationEvent(
                System.currentTimeMillis(), type,
                nodeId.toString(),
                peer == null ? null : peer.toString(),
                summary
        ));
    }

    private void publishError(NodeId nodeId, NodeId peer, String msg) {
        publish(EventType.ERROR, nodeId, peer, msg);
    }

    private static String summary(SimulationMessage m) {
        String seq = m.seq() == null ? "" : (" seq=" + m.seq());
        return "msgType=" + m.messageType() + seq;
    }
}
