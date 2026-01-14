package de.haw.vsp.simulation.middleware.adapter;

import de.haw.vsp.simulation.core.NodeId;
import de.haw.vsp.simulation.core.SimulationMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Simple in-memory adapter for simulation runs inside a single JVM.
 */
public final class InMemoryAdapter implements TransportAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(InMemoryAdapter.class);

    // shared map of all active adapters (local-node-id → adapter)
    private static final ConcurrentMap<NodeId, InMemoryAdapter> REGISTRY = new ConcurrentHashMap<>();

    private final NodeId localNode;
    private volatile ReceiveCallback receiveCallback;

    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS,
            new SynchronousQueue<>(),
            r -> {
                Thread t = new Thread(r, "inmem-adapter-" + System.identityHashCode(this));
                t.setDaemon(true);
                return t;
            },
            new ThreadPoolExecutor.DiscardPolicy()
    );

    public InMemoryAdapter(NodeId localNode) {
        this.localNode = Objects.requireNonNull(localNode, "localNode");
        if (REGISTRY.putIfAbsent(localNode, this) != null) {
            throw new IllegalStateException("InMemoryAdapter already registered for " + localNode);
        }
    }

    @Override
    public void send(SimulationMessage message) {
        InMemoryAdapter target = REGISTRY.get(message.receiver());
        if (target == null || target.receiveCallback == null) {
            LOG.debug("Drop message to {} (no target registered)", message.receiver());
            return;
        }

        try {
            target.executor.execute(() -> target.receiveCallback.onMessage(message));
        } catch (RejectedExecutionException e) {
            LOG.debug("Drop message to {} (executor busy)", message.receiver());
        }
    }

    @Override
    public void onReceive(ReceiveCallback callback) {
        this.receiveCallback = callback;
    }

    @Override
    public NodeId localNode() {
        return localNode;
    }

    @Override
    public void close() {
        REGISTRY.remove(localNode);
        executor.shutdownNow();
    }
}
