package de.haw.vsp.simulation.middleware.adapter;

import de.haw.vsp.simulation.core.NodeId;
import de.haw.vsp.simulation.core.SimulationMessage;

import java.io.IOException;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.*;

public final class FaultyTransportAdapter implements TransportAdapter {

    private final TransportAdapter delegate;
    private final double dropProbability;
    private final long artificialDelayMillis;
    private final Random random = new Random();

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "transport-fault-scheduler");
                t.setDaemon(true);
                return t;
            });

    public FaultyTransportAdapter(
            TransportAdapter delegate,
            double dropProbability,
            long artificialDelayMillis
    ) {
        this.delegate = Objects.requireNonNull(delegate);
        if (dropProbability < 0.0 || dropProbability > 1.0) {
            throw new IllegalArgumentException("dropProbability must be in [0,1]");
        }
        if (artificialDelayMillis < 0) {
            throw new IllegalArgumentException("delay must be >= 0");
        }
        this.dropProbability = dropProbability;
        this.artificialDelayMillis = artificialDelayMillis;
    }

    @Override
    public void send(SimulationMessage message) {
        if (random.nextDouble() < dropProbability) {
            return; // silently drop
        }

        if (artificialDelayMillis == 0) {
            delegate.send(message);
        } else {
            scheduler.schedule(
                    () -> delegate.send(message),
                    artificialDelayMillis,
                    TimeUnit.MILLISECONDS
            );
        }
    }

    @Override
    public void onReceive(ReceiveCallback callback) {
        delegate.onReceive(callback);
    }

    @Override
    public NodeId localNode() {
        return delegate.localNode();
    }

    @Override
    public void close() throws IOException {
        delegate.close();
        scheduler.shutdownNow();
    }
}