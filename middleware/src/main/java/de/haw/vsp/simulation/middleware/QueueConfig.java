package de.haw.vsp.simulation.middleware;

/**
 * Configuration for a bounded queue used in messaging (inbound/outbound).
 *
 * @param capacity           maximum number of elements the queue can hold (> 0)
 * @param overflowPolicy     behavior when the queue is full
 * @param offerTimeoutMillis timeout used only for {@link QueueOverflowPolicy#BLOCK}.
 *                           For non-blocking policies this value is ignored but must be >= 0.
 */
public record QueueConfig(
        int capacity,
        QueueOverflowPolicy overflowPolicy,
        long offerTimeoutMillis
) {

    /**
     * Canonical constructor with validation.
     */
    public QueueConfig {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be > 0, but was: " + capacity);
        }
        if (overflowPolicy == null) {
            throw new IllegalArgumentException("overflowPolicy must not be null");
        }
        if (offerTimeoutMillis < 0) {
            throw new IllegalArgumentException("offerTimeoutMillis must be >= 0, but was: " + offerTimeoutMillis);
        }
    }

    /**
     * Default configuration intended for deterministic/robust simulations:
     * bounded queue + non blocking
     */
    public static QueueConfig defaultConfig() {
        // Contract default recommendation: non-blocking overflow handling
        return new QueueConfig(1024, QueueOverflowPolicy.DROP_NEWEST, 0);
        // return new QueueConfig(1024, QueueOverflowPolicy.BLOCK, 1000);
    }

    /**
     * Convenience factory for a blocking configuration.
     */
    public static QueueConfig blocking(int capacity, long offerTimeoutMillis) {
        return new QueueConfig(capacity, QueueOverflowPolicy.BLOCK, offerTimeoutMillis);
    }

    /**
     * Convenience factory for a drop-newest configuration.
     */
    public static QueueConfig dropNewest(int capacity) {
        return new QueueConfig(capacity, QueueOverflowPolicy.DROP_NEWEST, 0);
    }

    /**
     * Convenience factory for a drop-oldest configuration.
     */
    public static QueueConfig dropOldest(int capacity) {
        return new QueueConfig(capacity, QueueOverflowPolicy.DROP_OLDEST, 0);
    }
}
