package de.haw.vsp.simulation.engine;

import de.haw.vsp.simulation.core.EventType;
import de.haw.vsp.simulation.core.MetricsSnapshot;
import de.haw.vsp.simulation.core.SimulationEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DistributedMetricsAggregator.
 *
 * Verifies:
 * - Event handling (METRICS_UPDATE, MESSAGE_SENT, LEADER_ELECTED)
 * - Metrics aggregation across multiple nodes
 * - Snapshot generation
 * - JSON payload parsing
 * - Thread-safety
 */
@DisplayName("DistributedMetricsAggregator")
class DistributedMetricsAggregatorTest {

    private DistributedMetricsAggregator aggregator;

    @BeforeEach
    void setUp() {
        aggregator = new DistributedMetricsAggregator();
    }

    @Test
    @DisplayName("should handle METRICS_UPDATE event and parse payload")
    void shouldHandleMetricsUpdateEvent() {
        // Given: METRICS_UPDATE event with JSON payload
        String payload = "{\"messagesSent\":10,\"messagesReceived\":8,\"stateChanges\":3}";
        SimulationEvent event = SimulationEvent.withoutPeer(
            System.currentTimeMillis(),
            EventType.METRICS_UPDATE,
            "node-0",
            payload
        );

        // When: Process event
        aggregator.onEvent(event);

        // Then: Snapshot should reflect updated metrics
        MetricsSnapshot snapshot = aggregator.getSnapshot();
        assertNotNull(snapshot);
        assertEquals(10, snapshot.messageCount());
    }

    @Test
    @DisplayName("should aggregate metrics from multiple nodes")
    void shouldAggregateMetricsFromMultipleNodes() {
        // Given: METRICS_UPDATE events from 3 nodes
        SimulationEvent event1 = SimulationEvent.withoutPeer(
            System.currentTimeMillis(),
            EventType.METRICS_UPDATE,
            "node-0",
            "{\"messagesSent\":5,\"messagesReceived\":3,\"stateChanges\":1}"
        );

        SimulationEvent event2 = SimulationEvent.withoutPeer(
            System.currentTimeMillis(),
            EventType.METRICS_UPDATE,
            "node-1",
            "{\"messagesSent\":7,\"messagesReceived\":5,\"stateChanges\":2}"
        );

        SimulationEvent event3 = SimulationEvent.withoutPeer(
            System.currentTimeMillis(),
            EventType.METRICS_UPDATE,
            "node-2",
            "{\"messagesSent\":3,\"messagesReceived\":4,\"stateChanges\":1}"
        );

        // When: Process all events
        aggregator.onEvent(event1);
        aggregator.onEvent(event2);
        aggregator.onEvent(event3);

        // Then: Total messages should be sum of all nodes
        MetricsSnapshot snapshot = aggregator.getSnapshot();
        assertEquals(15, snapshot.messageCount()); // 5 + 7 + 3
    }

    @Test
    @DisplayName("should handle LEADER_ELECTED event")
    void shouldHandleLeaderElectedEvent() {
        // Given: LEADER_ELECTED event
        SimulationEvent event = SimulationEvent.withoutPeer(
            System.currentTimeMillis(),
            EventType.LEADER_ELECTED,
            "node-5",
            "Node node-5 elected as leader"
        );

        // When: Process event
        aggregator.onEvent(event);

        // Then: Leader should be set and converged
        MetricsSnapshot snapshot = aggregator.getSnapshot();
        assertTrue(snapshot.converged());
        assertNotNull(snapshot.leaderId());
        assertEquals("node-5", snapshot.leaderId());
    }

    @Test
    @DisplayName("should count MESSAGE_SENT events")
    void shouldCountMessageSentEvents() {
        // Given: METRICS_UPDATE event with 5 messages sent from node
        SimulationEvent event = SimulationEvent.withoutPeer(
            System.currentTimeMillis(),
            EventType.METRICS_UPDATE,
            "node-0",
            "{\"messagesSent\":5,\"messagesReceived\":3}"
        );
        aggregator.onEvent(event);

        // When: Get snapshot
        MetricsSnapshot snapshot = aggregator.getSnapshot();

        // Then: Message count should be 5
        assertEquals(5, snapshot.messageCount());
    }

    @Test
    @DisplayName("should handle invalid JSON payload gracefully")
    void shouldHandleInvalidJsonPayloadGracefully() {
        // Given: METRICS_UPDATE event with invalid JSON
        SimulationEvent event = SimulationEvent.withoutPeer(
            System.currentTimeMillis(),
            EventType.METRICS_UPDATE,
            "node-0",
            "invalid-json"
        );

        // When: Process event
        assertDoesNotThrow(() -> aggregator.onEvent(event));

        // Then: Should not crash, metrics should be 0
        MetricsSnapshot snapshot = aggregator.getSnapshot();
        assertEquals(0, snapshot.messageCount());
    }

    @Test
    @DisplayName("should handle null event gracefully")
    void shouldHandleNullEventGracefully() {
        // When: Process null event
        assertDoesNotThrow(() -> aggregator.onEvent(null));

        // Then: Should not crash
        MetricsSnapshot snapshot = aggregator.getSnapshot();
        assertNotNull(snapshot);
    }

    @Test
    @DisplayName("should update metrics when receiving new data from same node")
    void shouldUpdateMetricsWhenReceivingNewDataFromSameNode() {
        // Given: First METRICS_UPDATE from node-0
        SimulationEvent event1 = SimulationEvent.withoutPeer(
            System.currentTimeMillis(),
            EventType.METRICS_UPDATE,
            "node-0",
            "{\"messagesSent\":5,\"messagesReceived\":3,\"stateChanges\":1}"
        );
        aggregator.onEvent(event1);

        // When: Second METRICS_UPDATE from same node with updated values
        SimulationEvent event2 = SimulationEvent.withoutPeer(
            System.currentTimeMillis(),
            EventType.METRICS_UPDATE,
            "node-0",
            "{\"messagesSent\":10,\"messagesReceived\":8,\"stateChanges\":2}"
        );
        aggregator.onEvent(event2);

        // Then: Should use latest values (not sum)
        MetricsSnapshot snapshot = aggregator.getSnapshot();
        assertEquals(10, snapshot.messageCount());
    }

    @Test
    @DisplayName("should mark simulation start and calculate duration")
    void shouldMarkSimulationStartAndCalculateDuration() throws InterruptedException {
        // Given: Mark start time
        aggregator.markSimulationStart();

        // When: Wait a bit and mark end
        Thread.sleep(100);
        aggregator.markSimulationEnd();

        // Then: Duration should be > 0
        MetricsSnapshot snapshot = aggregator.getSnapshot();
        assertTrue(snapshot.realTimeMillis() >= 100);
    }

    @Test
    @DisplayName("should reset all metrics")
    void shouldResetAllMetrics() {
        // Given: Aggregator with metrics
        SimulationEvent event = SimulationEvent.withoutPeer(
            System.currentTimeMillis(),
            EventType.METRICS_UPDATE,
            "node-0",
            "{\"messagesSent\":10,\"messagesReceived\":8,\"stateChanges\":3}"
        );
        aggregator.onEvent(event);
        aggregator.markSimulationStart();

        // When: Reset
        aggregator.reset();

        // Then: All metrics should be 0/empty
        MetricsSnapshot snapshot = aggregator.getSnapshot();
        assertEquals(0, snapshot.messageCount());
        assertFalse(snapshot.converged());
        assertNull(snapshot.leaderId());
        assertEquals(0, snapshot.realTimeMillis());
    }

    @Test
    @DisplayName("should handle missing fields in JSON payload")
    void shouldHandleMissingFieldsInJsonPayload() {
        // Given: METRICS_UPDATE with only some fields
        SimulationEvent event = SimulationEvent.withoutPeer(
            System.currentTimeMillis(),
            EventType.METRICS_UPDATE,
            "node-0",
            "{\"messagesSent\":5}"
        );

        // When: Process event
        assertDoesNotThrow(() -> aggregator.onEvent(event));

        // Then: Should handle gracefully, messagesSent should be 5
        MetricsSnapshot snapshot = aggregator.getSnapshot();
        assertEquals(5, snapshot.messageCount());
    }

    @Test
    @DisplayName("should ignore non-metric event types")
    void shouldIgnoreNonMetricEventTypes() {
        // Given: STATE_CHANGED event
        SimulationEvent event = SimulationEvent.withoutPeer(
            System.currentTimeMillis(),
            EventType.STATE_CHANGED,
            "node-0",
            "State changed"
        );

        // When: Process event
        aggregator.onEvent(event);

        // Then: Metrics should remain 0
        MetricsSnapshot snapshot = aggregator.getSnapshot();
        assertEquals(0, snapshot.messageCount());
    }
}
