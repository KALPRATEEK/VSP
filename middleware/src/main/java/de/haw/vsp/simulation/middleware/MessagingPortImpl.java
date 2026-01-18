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
 * MessagingPort implementation delegating transport to a TransportAdapter.
 *
 * - udp-docker: enforceLocalSender = true (sender must equal adapter.localNode)
 * - virtual:    enforceLocalSender = false (single shared port for many nodes)
 */
public final class MessagingPortImpl implements MessagingPort, Closeable, EventPublisherAware {

    private static final Logger LOG = LoggerFactory.getLogger(MessagingPortImpl.class);

    private final TransportAdapter adapter;
    private final boolean enforceLocalSender;

    private volatile SimulationEventPublisher eventPublisher; // may be null
    private final ConcurrentMap<NodeId, MessageHandler> handlers = new ConcurrentHashMap<>();

    public MessagingPortImpl(TransportAdapter adapter, SimulationEventPublisher eventPublisher) {
        this(adapter, eventPublisher, true);
    }

    public MessagingPortImpl(TransportAdapter adapter, SimulationEventPublisher eventPublisher, boolean enforceLocalSender) {
        this.adapter = Objects.requireNonNull(adapter, "adapter");
        this.eventPublisher = eventPublisher;
        this.enforceLocalSender = enforceLocalSender;

        adapter.onReceive(this::handleIncoming);

        // forward transport-level errors to SimulationEvents.ERROR
        adapter.onError((nodeId, peer, msg) -> publish(EventType.ERROR, nodeId, peer, msg));
    }

    @Override
    public void setEventPublisher(SimulationEventPublisher publisher) {
        this.eventPublisher = publisher;
    }

    @Override
    public void send(NodeId receiver, SimulationMessage message) {
        Objects.requireNonNull(receiver, "receiver");
        Objects.requireNonNull(message, "message");

        // optional: keep your existing validation if you already have it
        if (!receiver.equals(message.receiver())) {
            publish(EventType.ERROR, message.sender(), receiver, "receiver mismatch");
            return;
        }

        // optional: if udp-docker enforces sender == local node
        if (enforceLocalSender && !adapter.localNode().equals(message.sender())) {
            publish(EventType.ERROR, adapter.localNode(), receiver, "sender mismatch");
            return;
        }

        boolean accepted = adapter.send(message);

        if (accepted) {
            publish(EventType.MESSAGE_SENT, message.sender(), message.receiver(), summary(message));
        } else {
            publish(EventType.ERROR, message.sender(), message.receiver(), "dropped by transport (send not accepted)");
        }
    }


    @Override
    public void broadcast(Set<NodeId> receivers, SimulationMessage baseMessage) {
        Objects.requireNonNull(receivers, "receivers");
        Objects.requireNonNull(baseMessage, "baseMessage");

        for (NodeId r : receivers) {
            if (r == null) continue;

            SimulationMessage msg = baseMessage;
            if (!Objects.equals(r, baseMessage.receiver())) {
                msg = new SimulationMessage(
                        baseMessage.sender(), r,
                        baseMessage.messageType(), baseMessage.payload(), baseMessage.seq()
                );
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
        try { adapter.close(); } catch (Exception ignored) {}
        handlers.clear();
    }

    private void handleIncoming(SimulationMessage msg) {
        if (msg == null) return;

        NodeId receiver = msg.receiver();
        MessageHandler handler = handlers.get(receiver);

        if (handler == null) {
            publish(EventType.ERROR, receiver, msg.sender(), "no handler registered");
            return;
        }

        publish(EventType.MESSAGE_RECEIVED, receiver, msg.sender(), summary(msg));
        try {
            handler.onMessage(msg);
        } catch (RuntimeException e) {
            publish(EventType.ERROR, receiver, msg.sender(), "handler error: " + e.getMessage());
            LOG.debug("Handler error at {} for message {}", receiver, msg, e);
        }
    }

    private void publish(EventType type, NodeId nodeId, NodeId peer, String summary) {
        SimulationEventPublisher pub = this.eventPublisher;
        if (pub == null || nodeId == null) return;

        pub.publish(new SimulationEvent(
                System.currentTimeMillis(),
                type,
                nodeId.toString(),
                peer == null ? null : peer.toString(),
                summary
        ));
    }

    private static String summary(SimulationMessage m) {
        String seq = m.seq() == null ? "" : (" seq=" + m.seq());
        return "msgType=" + m.messageType() + seq;
    }
}
