package de.haw.vsp.simulation.middleware.inmemory;

import de.haw.vsp.simulation.core.EventType;
import de.haw.vsp.simulation.core.NodeId;
import de.haw.vsp.simulation.core.SimulationEvent;
import de.haw.vsp.simulation.core.SimulationEventPublisher;
import de.haw.vsp.simulation.core.SimulationMessage;
import de.haw.vsp.simulation.middleware.MessageHandler;
import de.haw.vsp.simulation.middleware.MessagingPort;
import de.haw.vsp.simulation.middleware.QueueConfig;
import de.haw.vsp.simulation.middleware.QueueOverflowPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Shared in-memory middleware implementation.
 *
 * <p>Intended for local runs/tests where all nodes live in the same JVM, but messaging should still be:
 * <ul>
 *   <li>Event-driven (handlers)</li>
 *   <li>Asynchronous (send() enqueues, delivery happens on worker threads)</li>
 *   <li>Transient (no durability, no delivery guarantees)</li>
 * </ul>
 *
 * <p>This class is designed as a single middleware instance per simulation (shared resources),
 * routing messages between registered node handlers.</p>
 *
 * <p>Queueing model (producer/consumer):
 * <ul>
 *   <li>Outbound queue: producers are node threads calling send(); consumer is a router thread.</li>
 *   <li>Inbound queues (per node): producer is router thread; consumer is a per-node worker thread.</li>
 * </ul>
 */
public final class InMemoryMessagingPort implements MessagingPort, Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(InMemoryMessagingPort.class);

    private final QueueConfig inboundConfig;
    private final QueueConfig outboundConfig;

    private final LinkedBlockingDeque<OutboundEnvelope> outboundQueue;
    private final ConcurrentMap<NodeId, NodeMailbox> mailboxes;

    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final Thread routerThread;

    // Optional, can be set later
    private volatile SimulationEventPublisher eventPublisher;

    /**
     * Uses {@link QueueConfig#defaultConfig()} for both inbound and outbound queues, no event publisher.
     */
    public InMemoryMessagingPort() {
        this(null, QueueConfig.defaultConfig(), QueueConfig.defaultConfig());
    }

    /**
     * Uses {@link QueueConfig#defaultConfig()} for both inbound and outbound queues.
     */
    public InMemoryMessagingPort(SimulationEventPublisher publisher) {
        this(publisher, QueueConfig.defaultConfig(), QueueConfig.defaultConfig());
    }

    /**
     * Create an in-memory messaging port with explicit queue configs.
     *
     * @param publisher optional event publisher (may be null)
     * @param inboundConfig per-node inbound queue config (must not be null)
     * @param outboundConfig outbound queue config (must not be null)
     */
    public InMemoryMessagingPort(
            SimulationEventPublisher publisher,
            QueueConfig inboundConfig,
            QueueConfig outboundConfig
    ) {
        this.eventPublisher = publisher;
        this.inboundConfig = Objects.requireNonNull(inboundConfig, "inboundConfig");
        this.outboundConfig = Objects.requireNonNull(outboundConfig, "outboundConfig");

        this.outboundQueue = new LinkedBlockingDeque<>(outboundConfig.capacity());
        this.mailboxes = new ConcurrentHashMap<>();

        this.routerThread = new Thread(this::routeLoop, "inmem-mw-router");
        this.routerThread.setDaemon(true);
        this.routerThread.start();
    }

    /**
     * Optional setter if the engine wants to attach an event publisher after construction.
     */
    public void setEventPublisher(SimulationEventPublisher publisher) {
        this.eventPublisher = publisher;
    }

    @Override
    public void send(NodeId receiver, SimulationMessage message) {
        Objects.requireNonNull(receiver, "receiver");
        Objects.requireNonNull(message, "message");

        if (!receiver.equals(message.receiver())) {
            publishError(message.sender(), receiver, "receiver mismatch");
            return;
        }
        if (closed.get()) {
            publishError(message.sender(), receiver, "middleware closed");
            return;
        }

        OutboundEnvelope env = new OutboundEnvelope(receiver, message);

        boolean accepted = enqueue(outboundQueue, env, outboundConfig);
        if (!accepted) {
            publishError(message.sender(), receiver, "outbound queue full (dropped)");
            return;
        }

        // "Sent" = accepted into middleware (no delivery guarantee)
        publish(EventType.MESSAGE_SENT, message.sender(), receiver, summary(message));
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
                        baseMessage.sender(),
                        r,
                        baseMessage.messageType(),
                        baseMessage.payload(),
                        baseMessage.seq()
                );
            }
            send(r, msg);
        }
    }

    @Override
    public void registerHandler(NodeId nodeId, MessageHandler handler) {
        Objects.requireNonNull(nodeId, "nodeId");
        Objects.requireNonNull(handler, "handler");

        if (closed.get()) {
            publishError(nodeId, null, "middleware closed");
            return;
        }

        mailboxes.compute(nodeId, (id, existing) -> {
            if (existing == null) {
                NodeMailbox mb = new NodeMailbox(id, handler, inboundConfig, this::publish, this::publishError);
                mb.start();
                LOG.debug("Handler registered for {} (new mailbox)", nodeId);
                return mb;
            } else {
                existing.setHandler(handler);
                LOG.debug("Handler registered for {} (updated handler)", nodeId);
                return existing;
            }
        });
    }

    @Override
    public void unregisterHandler(NodeId nodeId) {
        Objects.requireNonNull(nodeId, "nodeId");
        NodeMailbox mb = mailboxes.remove(nodeId);
        if (mb != null) {
            mb.close();
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return; // idempotent
        }

        // stop router
        routerThread.interrupt();

        // stop mailboxes
        for (NodeMailbox mb : mailboxes.values()) {
            try {
                mb.close();
            } catch (Exception ignored) {}
        }
        mailboxes.clear();

        // best-effort drain
        outboundQueue.clear();
    }

    // -------------------- Router loop --------------------

    private void routeLoop() {
        while (!closed.get()) {
            try {
                OutboundEnvelope env = outboundQueue.takeFirst();
                deliver(env);
            } catch (InterruptedException ie) {
                // allow shutdown
                if (closed.get()) break;
                Thread.currentThread().interrupt();
            } catch (RuntimeException e) {
                LOG.warn("Router loop error: {}", e.getMessage(), e);
            }
        }
    }

    private void deliver(OutboundEnvelope env) {
        SimulationMessage msg = env.message;
        NodeId receiver = env.receiver;

        NodeMailbox mb = mailboxes.get(receiver);
        if (mb == null) {
            // Message is transient -> drop if receiver not registered
            publishError(receiver, msg.sender(), "no handler registered");
            return;
        }

        boolean accepted = mb.enqueue(msg);
        if (!accepted) {
            publishError(receiver, msg.sender(), "inbound queue full (dropped)");
        }
    }

    // -------------------- Mailbox --------------------

    private static final class NodeMailbox implements Closeable {
        private final NodeId nodeId;
        private final QueueConfig inboundConfig;
        private final LinkedBlockingDeque<SimulationMessage> inboundQueue;

        private final AtomicBoolean running = new AtomicBoolean(false);
        private volatile MessageHandler handler;

        private final Thread worker;

        private final EventPublisher publish;
        private final ErrorPublisher publishError;

        NodeMailbox(
                NodeId nodeId,
                MessageHandler handler,
                QueueConfig inboundConfig,
                EventPublisher publish,
                ErrorPublisher publishError
        ) {
            this.nodeId = nodeId;
            this.handler = handler;
            this.inboundConfig = inboundConfig;
            this.inboundQueue = new LinkedBlockingDeque<>(inboundConfig.capacity());
            this.publish = publish;
            this.publishError = publishError;

            this.worker = new Thread(this::workLoop, "inmem-mw-inbound-" + nodeId);
            this.worker.setDaemon(true);
        }

        void start() {
            if (running.compareAndSet(false, true)) {
                worker.start();
            }
        }

        void setHandler(MessageHandler handler) {
            this.handler = handler;
        }

        boolean enqueue(SimulationMessage msg) {
            return InMemoryMessagingPort.enqueue(inboundQueue, msg, inboundConfig);
        }

        private void workLoop() {
            while (running.get()) {
                try {
                    SimulationMessage msg = inboundQueue.takeFirst();
                    MessageHandler h = handler;
                    if (h == null) {
                        publishError.publish(nodeId, msg.sender(), "no handler registered");
                        continue;
                    }

                    publish.publish(EventType.MESSAGE_RECEIVED, nodeId, msg.sender(), summary(msg));
                    try {
                        h.onMessage(msg);
                    } catch (RuntimeException e) {
                        publishError.publish(nodeId, msg.sender(), "handler error: " + e.getMessage());
                        LOG.debug("Handler error at {} for message {}", nodeId, msg, e);
                    }
                } catch (InterruptedException ie) {
                    if (!running.get()) break;
                    Thread.currentThread().interrupt();
                } catch (RuntimeException e) {
                    LOG.warn("Inbound worker error at {}: {}", nodeId, e.getMessage(), e);
                }
            }
        }

        @Override
        public void close() {
            if (!running.compareAndSet(true, false)) {
                // if not running yet or already stopped
                running.set(false);
            }
            worker.interrupt();
            inboundQueue.clear();
        }
    }

    // -------------------- Queue policy helper --------------------

    private static <T> boolean enqueue(LinkedBlockingDeque<T> queue, T item, QueueConfig cfg) {
        QueueOverflowPolicy policy = cfg.overflowPolicy();

        try {
            return switch (policy) {
                case BLOCK -> queue.offerLast(item, cfg.offerTimeoutMillis(), TimeUnit.MILLISECONDS);
                case DROP_NEWEST -> queue.offerLast(item);
                case DROP_OLDEST -> {
                    if (queue.offerLast(item)) {
                        yield true;
                    }
                    // try to make room
                    queue.pollFirst();
                    yield queue.offerLast(item);
                }
            };
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    // -------------------- Events --------------------

    private void publish(EventType type, NodeId nodeId, NodeId peer, String summary) {
        SimulationEventPublisher pub = this.eventPublisher;
        if (pub == null) return;

        pub.publish(new SimulationEvent(
                System.currentTimeMillis(),
                type,
                nodeId == null ? null : nodeId.toString(),
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

    // -------------------- Small helper types --------------------

    private record OutboundEnvelope(NodeId receiver, SimulationMessage message) {}

    @FunctionalInterface
    private interface EventPublisher {
        void publish(EventType type, NodeId nodeId, NodeId peer, String summary);
    }

    @FunctionalInterface
    private interface ErrorPublisher {
        void publish(NodeId nodeId, NodeId peer, String msg);
    }
}
