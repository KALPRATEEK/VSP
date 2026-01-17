package de.haw.vsp.simulation.middleware;

/**
 * Bundles inbound and outbound queue configurations for a messaging runtime.
 *
 * Inbound queues model per-node mailboxes (consumer = handler execution).
 * Outbound queues model send buffering (consumer = transport sender/router).
 */
public record MessagingQueueConfig(
        QueueConfig inbound,
        QueueConfig outbound
) {

    public MessagingQueueConfig {
        if (inbound == null) {
            throw new IllegalArgumentException("inbound must not be null");
        }
        if (outbound == null) {
            throw new IllegalArgumentException("outbound must not be null");
        }
    }

    public static MessagingQueueConfig defaultQueues() {
        return new MessagingQueueConfig(QueueConfig.defaultConfig(), QueueConfig.defaultConfig());
    }
}
