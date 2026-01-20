package de.haw.vsp.simulation.engine;

import de.haw.vsp.simulation.core.EventType;
import de.haw.vsp.simulation.core.SimulationEvent;
import de.haw.vsp.simulation.core.SimulationId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for EventPublisherService.
 *
 * Verifies:
 * - Async event queuing
 * - Background publishing
 * - Best-effort delivery
 * - Queue overflow handling
 * - Graceful shutdown
 *
 * Note: These tests focus on the service logic, not HTTP communication.
 * HTTP communication is tested separately with integration tests.
 */
@DisplayName("EventPublisherService")
class EventPublisherServiceTest {

    private static final String TEST_BACKEND_URL = "http://localhost:8080";
    private EventPublisherService eventPublisher;
    private SimulationId simulationId;

    @BeforeEach
    void setUp() {
        simulationId = SimulationId.generate();
        eventPublisher = new EventPublisherService(TEST_BACKEND_URL, simulationId);
    }

    @AfterEach
    void tearDown() {
        if (eventPublisher != null) {
            eventPublisher.stop();
        }
    }

    @Test
    @DisplayName("should create event publisher with valid parameters")
    void shouldCreateEventPublisherWithValidParameters() {
        // When: Create event publisher
        EventPublisherService publisher = new EventPublisherService(TEST_BACKEND_URL, simulationId);

        // Then: Should not be null
        assertNotNull(publisher);
    }

    @Test
    @DisplayName("should throw exception when backendUrl is null")
    void shouldThrowExceptionWhenBackendUrlIsNull() {
        // When/Then: Should throw IllegalArgumentException
        assertThrows(
            IllegalArgumentException.class,
            () -> new EventPublisherService(null, simulationId)
        );
    }

    @Test
    @DisplayName("should throw exception when backendUrl is blank")
    void shouldThrowExceptionWhenBackendUrlIsBlank() {
        // When/Then: Should throw IllegalArgumentException
        assertThrows(
            IllegalArgumentException.class,
            () -> new EventPublisherService("   ", simulationId)
        );
    }

    @Test
    @DisplayName("should throw exception when simulationId is null")
    void shouldThrowExceptionWhenSimulationIdIsNull() {
        // When/Then: Should throw IllegalArgumentException
        assertThrows(
            IllegalArgumentException.class,
            () -> new EventPublisherService(TEST_BACKEND_URL, null)
        );
    }

    @Test
    @DisplayName("should start background publishing thread")
    void shouldStartBackgroundPublishingThread() {
        // When: Start event publisher
        eventPublisher.start();

        // Then: Should not throw exception
        assertDoesNotThrow(() -> eventPublisher.start());
    }

    @Test
    @DisplayName("should accept events when started")
    void shouldAcceptEventsWhenStarted() {
        // Given: Started publisher
        eventPublisher.start();

        // When: Publish event
        SimulationEvent event = SimulationEvent.withoutPeer(
            System.currentTimeMillis(),
            EventType.MESSAGE_SENT,
            "node-0",
            "Test event"
        );

        // Then: Should not throw exception
        assertDoesNotThrow(() -> eventPublisher.publish(event));
    }

    @Test
    @DisplayName("should throw exception when publishing null event")
    void shouldThrowExceptionWhenPublishingNullEvent() {
        // Given: Started publisher
        eventPublisher.start();

        // When/Then: Should throw IllegalArgumentException
        assertThrows(
            IllegalArgumentException.class,
            () -> eventPublisher.publish(null)
        );
    }

    @Test
    @DisplayName("should drop events when not started")
    void shouldDropEventsWhenNotStarted() {
        // Given: Publisher not started
        // When: Publish event
        SimulationEvent event = SimulationEvent.withoutPeer(
            System.currentTimeMillis(),
            EventType.MESSAGE_SENT,
            "node-0",
            "Test event"
        );

        // Then: Should not throw exception (event is dropped)
        assertDoesNotThrow(() -> eventPublisher.publish(event));
    }

    @Test
    @DisplayName("should stop gracefully")
    void shouldStopGracefully() {
        // Given: Started publisher
        eventPublisher.start();

        // When: Stop publisher
        assertDoesNotThrow(() -> eventPublisher.stop());

        // Then: Can be stopped again without error
        assertDoesNotThrow(() -> eventPublisher.stop());
    }

    @Test
    @DisplayName("should handle multiple start calls gracefully")
    void shouldHandleMultipleStartCallsGracefully() {
        // When: Start multiple times
        eventPublisher.start();
        eventPublisher.start();
        eventPublisher.start();

        // Then: Should not throw exception
        assertDoesNotThrow(() -> eventPublisher.stop());
    }

    @Test
    @DisplayName("should queue events when backend is unreachable")
    void shouldQueueEventsWhenBackendIsUnreachable() {
        // Given: Publisher with unreachable backend
        EventPublisherService publisher = new EventPublisherService(
            "http://non-existent-backend:9999",
            simulationId
        );
        publisher.start();

        // When: Publish multiple events
        for (int i = 0; i < 10; i++) {
            SimulationEvent event = SimulationEvent.withoutPeer(
                System.currentTimeMillis(),
                EventType.MESSAGE_SENT,
                "node-0",
                "Test event " + i
            );
            publisher.publish(event);
        }

        // Then: Should not throw exception (best-effort)
        assertDoesNotThrow(() -> publisher.stop());
    }

    @Test
    @DisplayName("should handle rapid event publishing")
    void shouldHandleRapidEventPublishing() throws InterruptedException {
        // Given: Started publisher
        eventPublisher.start();

        // When: Publish many events rapidly
        for (int i = 0; i < 1000; i++) {
            SimulationEvent event = SimulationEvent.withoutPeer(
                System.currentTimeMillis(),
                EventType.MESSAGE_SENT,
                "node-0",
                "Test event " + i
            );
            eventPublisher.publish(event);
        }

        // Then: Should handle without crashing
        Thread.sleep(100); // Give some time for processing
        assertDoesNotThrow(() -> eventPublisher.stop());
    }

    @Test
    @DisplayName("should create EventBatch DTO correctly")
    void shouldCreateEventBatchDtoCorrectly() {
        // Given: SimulationId and events
        SimulationEvent event1 = SimulationEvent.withoutPeer(
            System.currentTimeMillis(),
            EventType.MESSAGE_SENT,
            "node-0",
            "Event 1"
        );

        SimulationEvent event2 = SimulationEvent.withoutPeer(
            System.currentTimeMillis(),
            EventType.MESSAGE_RECEIVED,
            "node-1",
            "Event 2"
        );

        // When: Create EventBatch
        EventPublisherService.EventBatch batch = new EventPublisherService.EventBatch(
            simulationId,
            java.util.List.of(event1, event2)
        );

        // Then: Batch should contain correct data
        assertNotNull(batch);
        assertEquals(simulationId, batch.simulationId());
        assertEquals(2, batch.events().size());
    }

    @Test
    @DisplayName("EventBatch should throw exception when simulationId is null")
    void eventBatchShouldThrowExceptionWhenSimulationIdIsNull() {
        // When/Then: Should throw IllegalArgumentException
        assertThrows(
            IllegalArgumentException.class,
            () -> new EventPublisherService.EventBatch(null, java.util.List.of())
        );
    }

    @Test
    @DisplayName("EventBatch should throw exception when events is null")
    void eventBatchShouldThrowExceptionWhenEventsIsNull() {
        // When/Then: Should throw IllegalArgumentException
        assertThrows(
            IllegalArgumentException.class,
            () -> new EventPublisherService.EventBatch(simulationId, null)
        );
    }
}
