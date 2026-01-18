package de.haw.vsp.simulation.middleware;

import java.util.Objects;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.TimeUnit;

public final class QueueOps {
    private QueueOps() {}

    public static <T> boolean enqueue(BlockingDeque<T> queue, T item, QueueConfig cfg) {
        Objects.requireNonNull(queue, "queue");
        Objects.requireNonNull(cfg, "cfg");

        try {
            return switch (cfg.overflowPolicy()) {
                case BLOCK -> queue.offerLast(item, cfg.offerTimeoutMillis(), TimeUnit.MILLISECONDS);
                case DROP_NEWEST -> queue.offerLast(item);
                case DROP_OLDEST -> {
                    if (queue.offerLast(item)) yield true;
                    queue.pollFirst();
                    yield queue.offerLast(item);
                }
            };
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
