package de.haw.vsp.simulation.middleware.adapter;

import de.haw.vsp.simulation.core.NodeId;
import de.haw.vsp.simulation.core.SimulationMessage;
import de.haw.vsp.simulation.middleware.QueueConfig;
import de.haw.vsp.simulation.middleware.QueueOps;
import de.haw.vsp.simulation.middleware.codec.MessageCodecException;
import de.haw.vsp.simulation.middleware.codec.SimulationMessageDeserializer;
import de.haw.vsp.simulation.middleware.codec.SimulationMessageSerializer;
import de.haw.vsp.simulation.middleware.virtual.VirtualFaultConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Virtual (in-JVM) transport adapter that simulates network semantics:
 * - async delivery
 * - bounded queues
 * - JSON serialize->deserialize boundary
 * - optional fault injection (drop + delay)
 *
 * This adapter is per-simulation instance (NO static/global registry).
 *
 * NOTE: This adapter is designed to be used with MessagingPortImpl(enforceLocalSender=false),
 * because a single shared port routes messages for many node senders.
 */
public final class VirtualAdapter implements TransportAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(VirtualAdapter.class);

    private final NodeId virtualNode = new NodeId("virtual");
    private final SimulationMessageSerializer serializer;
    private final SimulationMessageDeserializer deserializer;

    private final QueueConfig outboundConfig;
    private final QueueConfig inboundPerReceiverConfig;

    private final VirtualFaultConfig faultConfig;
    private final Random rng;

    private final LinkedBlockingDeque<SimulationMessage> outboundQueue;
    private final ConcurrentHashMap<NodeId, Inbox> inboxes = new ConcurrentHashMap<>();

    private final ExecutorService workerPool;
    private final ScheduledExecutorService scheduler; // only if delay enabled
    private final AtomicBoolean running = new AtomicBoolean(true);

    private volatile ReceiveCallback receiveCallback;
    private volatile ErrorCallback errorCallback;

    private final Thread routerThread;

    public VirtualAdapter(
            SimulationMessageSerializer serializer,
            SimulationMessageDeserializer deserializer
    ) {
        this(serializer, deserializer, QueueConfig.defaultConfig(), QueueConfig.defaultConfig(),
                Math.max(2, Runtime.getRuntime().availableProcessors()),
                VirtualFaultConfig.DISABLED);
    }

    public VirtualAdapter(
            SimulationMessageSerializer serializer,
            SimulationMessageDeserializer deserializer,
            QueueConfig outboundConfig,
            QueueConfig inboundPerReceiverConfig,
            int workerThreads,
            VirtualFaultConfig faultConfig
    ) {
        this.serializer = Objects.requireNonNull(serializer, "serializer");
        this.deserializer = Objects.requireNonNull(deserializer, "deserializer");
        this.outboundConfig = Objects.requireNonNull(outboundConfig, "outboundConfig");
        this.inboundPerReceiverConfig = Objects.requireNonNull(inboundPerReceiverConfig, "inboundPerReceiverConfig");

        if (workerThreads <= 0) throw new IllegalArgumentException("workerThreads must be > 0");

        this.faultConfig = Objects.requireNonNull(faultConfig, "faultConfig");
        this.rng = new Random(this.faultConfig.seed());

        this.outboundQueue = new LinkedBlockingDeque<>(this.outboundConfig.capacity());

        this.workerPool = Executors.newFixedThreadPool(workerThreads, r -> {
            Thread t = new Thread(r, "virtual-adapter-worker");
            t.setDaemon(true);
            return t;
        });

        this.scheduler = (this.faultConfig.maxDelayMs() > 0L)
                ? Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "virtual-adapter-delay");
            t.setDaemon(true);
            return t;
        })
                : null;

        this.routerThread = new Thread(this::routeLoop, "virtual-adapter-router");
        this.routerThread.setDaemon(true);
        this.routerThread.start();
    }

    @Override
    public boolean send(SimulationMessage message) {
        if (!running.get()) return false;

        return QueueOps.enqueue(outboundQueue, message, outboundConfig);
    }


    @Override
    public void onReceive(ReceiveCallback callback) {
        this.receiveCallback = callback;
    }

    @Override
    public void onError(ErrorCallback callback) {
        this.errorCallback = callback;
    }

    @Override
    public NodeId localNode() {
        return virtualNode;
    }

    @Override
    public void close() {
        running.set(false);
        routerThread.interrupt();

        outboundQueue.clear();
        inboxes.clear();

        workerPool.shutdownNow();
        if (scheduler != null) scheduler.shutdownNow();
    }

    private void routeLoop() {
        while (running.get()) {
            try {
                SimulationMessage original = outboundQueue.takeFirst();

                SimulationMessage decoded = roundTrip(original);
                if (decoded == null) {
                    reportError(original.sender(), original.receiver(), "virtual codec error (dropped)");
                    continue;
                }

                if (faultConfig.enabled()) {
                    if (faultConfig.shouldDrop(rng)) {
                        reportError(decoded.sender(), decoded.receiver(), "virtual fault: dropped");
                        continue;
                    }

                    long delayMs = faultConfig.sampleDelayMs(rng);
                    if (delayMs > 0 && scheduler != null) {
                        scheduler.schedule(() -> deliver(decoded), delayMs, TimeUnit.MILLISECONDS);
                        continue;
                    }
                }

                deliver(decoded);

            } catch (InterruptedException ie) {
                if (!running.get()) break;
                Thread.currentThread().interrupt();
            } catch (RuntimeException e) {
                LOG.debug("Virtual route loop error: {}", e.getMessage(), e);
            }
        }
    }

    private void deliver(SimulationMessage msg) {
        if (!running.get()) return;
        Inbox inbox = inboxes.computeIfAbsent(msg.receiver(), r -> new Inbox());
        inbox.enqueue(msg);
    }

    private SimulationMessage roundTrip(SimulationMessage msg) {
        try {
            byte[] bytes = serializer.serialize(msg);
            return deserializer.deserialize(bytes);
        } catch (MessageCodecException e) {
            LOG.debug("Virtual codec error: {}", e.getMessage());
            return null;
        } catch (RuntimeException e) {
            LOG.debug("Virtual unexpected codec error: {}", e.getMessage(), e);
            return null;
        }
    }

    private void reportError(NodeId nodeId, NodeId peer, String msg) {
        ErrorCallback cb = this.errorCallback;
        if (cb != null) cb.onError(nodeId, peer, msg);
    }

    /**
     * Per-receiver bounded inbox + serial draining on a shared worker pool.
     */
    private final class Inbox {
        private final LinkedBlockingDeque<SimulationMessage> q =
                new LinkedBlockingDeque<>(inboundPerReceiverConfig.capacity());
        private final AtomicBoolean draining = new AtomicBoolean(false);

        void enqueue(SimulationMessage msg) {
            boolean ok = QueueOps.enqueue(q, msg, inboundPerReceiverConfig);
            if (!ok) {
                reportError(msg.receiver(), msg.sender(), "virtual inbound queue full (dropped)");
                return;
            }
            scheduleDrain();
        }

        private void scheduleDrain() {
            if (draining.compareAndSet(false, true)) {
                workerPool.execute(this::drain);
            }
        }

        private void drain() {
            try {
                while (running.get()) {
                    SimulationMessage m = q.pollFirst();
                    if (m == null) break;

                    ReceiveCallback cb = receiveCallback;
                    if (cb != null) {
                        cb.onMessage(m);
                    } else {
                        // transient drop if port not wired yet
                        reportError(m.receiver(), m.sender(), "virtual drop (no receive callback)");
                    }
                }
            } finally {
                draining.set(false);
                // race: new items might have arrived after we decided to stop
                if (!q.isEmpty()) scheduleDrain();
            }
        }
    }
}
