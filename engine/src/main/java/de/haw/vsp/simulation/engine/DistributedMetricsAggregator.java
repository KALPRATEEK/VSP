package de.haw.vsp.simulation.engine;

import de.haw.vsp.simulation.core.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Aggregates metrics from distributed nodes via event stream.
 *
 * <p>Conforms to § 8.8 "Metrics & Performance Measurement Concept":
 * "MetricsCollector subscribes to the EventBus and updates counters."
 * "Metric extraction does not block nodes and does not influence run timing."
 *
 * <p>This aggregator:
 * <ul>
 *   <li>Subscribes to METRICS_UPDATE, MESSAGE_SENT, LEADER_ELECTED events</li>
 *   <li>Maintains per-node metrics state</li>
 *   <li>Aggregates into global MetricsSnapshot on demand</li>
 *   <li>Thread-safe for concurrent event processing</li>
 *   <li>Non-blocking (conforms to § 8.8)</li>
 * </ul>
 */
public class DistributedMetricsAggregator implements SimulationEventListener {

    private static final Logger LOG = LoggerFactory.getLogger(DistributedMetricsAggregator.class);

    private final Map<NodeId, NodeMetrics> nodeMetricsMap;
    private final AtomicLong totalMessages;
    private final AtomicLong rounds;
    private volatile String leaderId;
    private volatile boolean converged;
    private volatile long startTimeMillis;
    private volatile long endTimeMillis;

    /**
     * Creates a new distributed metrics aggregator.
     */
    public DistributedMetricsAggregator() {
        this.nodeMetricsMap = new ConcurrentHashMap<>();
        this.totalMessages = new AtomicLong(0);
        this.rounds = new AtomicLong(0);
        this.leaderId = null;
        this.converged = false;
        this.startTimeMillis = 0;
        this.endTimeMillis = 0;
    }

    @Override
    public void onEvent(SimulationEvent event) {
        if (event == null) {
            return;
        }

        // Handle different event types
        switch (event.type()) {
            case METRICS_UPDATE -> handleMetricsUpdate(event);
            case LEADER_ELECTED -> handleLeaderElected(event);
            case MESSAGE_SENT -> totalMessages.incrementAndGet();
            case STATE_CHANGED -> { /* Count if needed */ }
            default -> { /* ignore other event types */ }
        }
    }

    /**
     * Handles METRICS_UPDATE event from a node.
     *
     * <p>Parses payload summary (JSON-like string) and updates per-node metrics.
     *
     * @param event metrics update event
     */
    private void handleMetricsUpdate(SimulationEvent event) {
        try {
            NodeId nodeId = new NodeId(event.nodeId());

            // Parse payload summary: {"messagesSent":42,"messagesReceived":38,"stateChanges":5}
            String payload = event.payloadSummary();

            long messagesSent = extractLongValue(payload, "messagesSent");
            long messagesReceived = extractLongValue(payload, "messagesReceived");
            long stateChanges = extractLongValue(payload, "stateChanges");

            NodeMetrics metrics = new NodeMetrics(messagesSent, messagesReceived, stateChanges);
            nodeMetricsMap.put(nodeId, metrics);

            LOG.debug("Updated metrics for node {}: sent={}, received={}, stateChanges={}",
                      nodeId, messagesSent, messagesReceived, stateChanges);
        } catch (Exception e) {
            LOG.error("Failed to parse metrics update event: {}", event.payloadSummary(), e);
        }
    }

    /**
     * Handles LEADER_ELECTED event.
     *
     * @param event leader elected event
     */
    private void handleLeaderElected(SimulationEvent event) {
        this.leaderId = event.nodeId();
        this.converged = true;
        LOG.info("Leader elected: {}", leaderId);
    }

    /**
     * Returns a snapshot of current aggregated metrics.
     *
     * <p>This method is non-blocking and can be called at any time.
     *
     * @return current metrics snapshot
     */
    public MetricsSnapshot getSnapshot() {
        // Aggregate messages from all nodes
        long totalMessagesSent = nodeMetricsMap.values().stream()
            .mapToLong(NodeMetrics::messagesSent)
            .sum();

        long totalMessagesReceived = nodeMetricsMap.values().stream()
            .mapToLong(NodeMetrics::messagesReceived)
            .sum();

        // Calculate duration
        long durationMs = 0;
        if (startTimeMillis > 0) {
            long end = endTimeMillis > 0 ? endTimeMillis : System.currentTimeMillis();
            durationMs = end - startTimeMillis;
        }

        // Build snapshot using constructor
        // MetricsSnapshot(simulatedTime, realTimeMillis, messageCount, rounds, converged, leaderId)
        return new MetricsSnapshot(
            rounds.get(),           // simulatedTime = rounds
            durationMs,             // realTimeMillis
            totalMessagesSent,      // messageCount
            rounds.get(),           // rounds
            converged,              // converged
            leaderId != null ? leaderId.toString() : null  // leaderId as String
        );
    }

    /**
     * Marks the start of simulation timing.
     */
    public void markSimulationStart() {
        this.startTimeMillis = System.currentTimeMillis();
        LOG.info("Simulation start time marked: {}", startTimeMillis);
    }

    /**
     * Marks the end of simulation timing.
     */
    public void markSimulationEnd() {
        this.endTimeMillis = System.currentTimeMillis();
        LOG.info("Simulation end time marked: {}", endTimeMillis);
    }

    /**
     * Resets all metrics to initial state.
     */
    public void reset() {
        nodeMetricsMap.clear();
        totalMessages.set(0);
        rounds.set(0);
        leaderId = null;
        converged = false;
        startTimeMillis = 0;
        endTimeMillis = 0;
        LOG.info("Metrics aggregator reset");
    }

    /**
     * Extracts a long value from JSON-like payload string.
     *
     * <p>Simple parser for format: {"key":value,...}
     *
     * @param payload JSON-like string
     * @param key key to extract
     * @return extracted long value, or 0 if not found
     */
    private long extractLongValue(String payload, String key) {
        try {
            String keyPattern = "\"" + key + "\":";
            int startIdx = payload.indexOf(keyPattern);
            if (startIdx == -1) {
                return 0;
            }

            startIdx += keyPattern.length();
            int endIdx = payload.indexOf(",", startIdx);
            if (endIdx == -1) {
                endIdx = payload.indexOf("}", startIdx);
            }

            String valueStr = payload.substring(startIdx, endIdx).trim();
            return Long.parseLong(valueStr);
        } catch (Exception e) {
            LOG.warn("Failed to extract '{}' from payload: {}", key, payload);
            return 0;
        }
    }

    /**
     * Per-node metrics record.
     */
    record NodeMetrics(
        long messagesSent,
        long messagesReceived,
        long stateChanges
    ) {}
}
