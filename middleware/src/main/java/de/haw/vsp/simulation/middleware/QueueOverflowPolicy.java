package de.haw.vsp.simulation.middleware;

/**
 * Defines what to do when a queue is full.
 *
 * These policies are used for inbound/outbound messaging queues to model realistic
 * distributed-systems behavior under load (backpressure or explicit drops).
 */
public enum QueueOverflowPolicy {

    /**
     * Block the producer up to a configured timeout while waiting for space.
     * If the timeout expires, the enqueue attempt should be treated as failed.
     */
    BLOCK,

    /**
     * Reject the newly offered element immediately when the queue is full.
     */
    DROP_NEWEST,

    /**
     * Remove (drop) the oldest element from the queue to make room for the new element.
     * If the queue is empty or cannot be modified, the enqueue attempt may still fail.
     */
    DROP_OLDEST
}
