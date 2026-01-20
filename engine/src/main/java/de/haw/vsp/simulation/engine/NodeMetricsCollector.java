package de.haw.vsp.simulation.engine;

import de.haw.vsp.simulation.core.EventType;
import de.haw.vsp.simulation.core.NodeId;
import de.haw.vsp.simulation.core.SimulationEvent;
import de.haw.vsp.simulation.core.SimulationEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Collects metrics from a node and publishes them as events.
 *
 * <p>Conforms to § 8.8 "Metrics & Performance Measurement Concept":
 * "MetricsCollector subscribes to the EventBus and updates counters."
 *
 * <p>This collector:
 * <ul>
 *   <li>Subscribes to MESSAGE_SENT and MESSAGE_RECEIVED events</li>
 *   <li>Maintains counters for local metrics</li>
 *   <li>Periodically publishes METRICS_UPDATE events to backend (async)</li>
 *   <li>Non-blocking: does not influence node execution timing</li>
 * </ul>
 *
 * <p>Thread-safety: This class is thread-safe for concurrent event processing.
 */
public class NodeMetricsCollector implements SimulationEventListener {

    private static final Logger LOG = LoggerFactory.getLogger(NodeMetricsCollector.class);

    private final NodeId nodeId;
    private final EventPublisherService eventPublisher;

    private final AtomicLong messagesSent;
    private final AtomicLong messagesReceived;
    private final AtomicLong stateChanges;

    /**
     * Creates a new node metrics collector.
     *
     * @param nodeId the ID of the node being monitored
     * @param eventPublisher the event publisher to send metrics updates
     * @throws IllegalArgumentException if nodeId or eventPublisher is null
     */
    public NodeMetricsCollector(NodeId nodeId, EventPublisherService eventPublisher) {
        if (nodeId == null) {
            throw new IllegalArgumentException("nodeId must not be null");
        }
        if (eventPublisher == null) {
            throw new IllegalArgumentException("eventPublisher must not be null");
        }

        this.nodeId = nodeId;
        this.eventPublisher = eventPublisher;
        this.messagesSent = new AtomicLong(0);
        this.messagesReceived = new AtomicLong(0);
        this.stateChanges = new AtomicLong(0);
    }

    @Override
    public void onEvent(SimulationEvent event) {
        if (event == null) {
            return;
        }

        // Update counters based on event type
        switch (event.type()) {
            case MESSAGE_SENT -> messagesSent.incrementAndGet();
            case MESSAGE_RECEIVED -> messagesReceived.incrementAndGet();
            case STATE_CHANGED -> stateChanges.incrementAndGet();
            default -> { /* ignore other event types */ }
        }
    }

    /**
     * Publishes current metrics as METRICS_UPDATE event.
     *
     * <p>This method is scheduled to run periodically (every 1 second).
     * It sends metrics asynchronously to the backend via event publisher.
     *
     * <p>Conforms to § 8.8: "Metric extraction does not block nodes
     * and does not influence run timing."
     */
    @Scheduled(fixedRate = 1000) // Every 1 second
    public void publishMetrics() {
        long sent = messagesSent.get();
        long received = messagesReceived.get();
        long changes = stateChanges.get();

        // Create payload summary as JSON-like string
        String payloadSummary = String.format(
            "{\"messagesSent\":%d,\"messagesReceived\":%d,\"stateChanges\":%d}",
            sent, received, changes
        );

        // Create METRICS_UPDATE event
        SimulationEvent metricsEvent = SimulationEvent.withoutPeer(
            System.currentTimeMillis(),
            EventType.METRICS_UPDATE,
            nodeId.value(),
            payloadSummary
        );

        // Publish asynchronously (non-blocking)
        eventPublisher.publish(metricsEvent);

        LOG.debug("Published metrics for node {}: sent={}, received={}, stateChanges={}",
                  nodeId, sent, received, changes);
    }

    /**
     * Returns the current message sent count.
     *
     * @return number of messages sent by this node
     */
    public long getMessagesSent() {
        return messagesSent.get();
    }

    /**
     * Returns the current message received count.
     *
     * @return number of messages received by this node
     */
    public long getMessagesReceived() {
        return messagesReceived.get();
    }

    /**
     * Returns the current state change count.
     *
     * @return number of state changes in this node
     */
    public long getStateChanges() {
        return stateChanges.get();
    }

    /**
     * Resets all metric counters to zero.
     *
     * <p>Used when simulation is restarted.
     */
    public void reset() {
        messagesSent.set(0);
        messagesReceived.set(0);
        stateChanges.set(0);
        LOG.info("Metrics reset for node {}", nodeId);
    }
}
