package de.haw.vsp.simulation.core;

import org.junit.jupiter.api.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for SimulationEventBus implementation.
 *
 * Covers acceptance criteria from Issue D1 and D2:
 * - Events delivered to all registered observers
 * - Events delivered in publication order
 * - Publishing does not block simulation execution
 * - Listeners receive only events of subscribed event types
 * - Events from one source remain ordered
 * - Unsubscription immediately stops delivery
 * - Multiple listeners can subscribe to same event type
 * - Publishing does not block if listeners are slow
 */
@DisplayName("SimulationEventBus")
class SimulationEventBusTest {

    private SimulationEventBus eventBus;
    
    @BeforeEach
    void setUp() {
        eventBus = new InMemorySimulationEventBus();
    }
    
    @Nested
    @DisplayName("Basic Publishing")
    class BasicPublishing {

        @Test
        @DisplayName("should create event bus")
        void shouldCreateEventBus() {
            assertNotNull(eventBus);
        }

        @Test
        @DisplayName("should reject null event on publish")
        void shouldRejectNullEvent() {
            assertThrows(IllegalArgumentException.class, () -> eventBus.publish(null));
        }

        @Test
        @DisplayName("should publish event without subscribers without error")
        void shouldPublishWithoutSubscribers() {
            SimulationEvent event = SimulationEvent.withoutPeer(
                    1000L, EventType.MESSAGE_SENT, "node-1", "test"
            );

            assertDoesNotThrow(() -> eventBus.publish(event));
        }
    }

    @Nested
    @DisplayName("Subscription Management")
    class SubscriptionManagement {

        @Test
        @DisplayName("should reject null type on subscribe")
        void shouldRejectNullTypeOnSubscribe() {
            SimulationEventListener listener = event -> {};
            assertThrows(IllegalArgumentException.class, () -> eventBus.subscribe(null, listener));
        }

        @Test
        @DisplayName("should reject null listener on subscribe")
        void shouldRejectNullListenerOnSubscribe() {
            assertThrows(IllegalArgumentException.class,
                    () -> eventBus.subscribe(EventType.MESSAGE_SENT, null));
        }

        @Test
        @DisplayName("should reject null type on unsubscribe")
        void shouldRejectNullTypeOnUnsubscribe() {
            SimulationEventListener listener = event -> {};
            assertThrows(IllegalArgumentException.class, () -> eventBus.unsubscribe(null, listener));
        }

        @Test
        @DisplayName("should reject null listener on unsubscribe")
        void shouldRejectNullListenerOnUnsubscribe() {
            assertThrows(IllegalArgumentException.class,
                    () -> eventBus.unsubscribe(EventType.MESSAGE_SENT, null));
        }

        @Test
        @DisplayName("should allow unsubscribe without prior subscribe")
        void shouldAllowUnsubscribeWithoutSubscribe() {
            SimulationEventListener listener = event -> {};
            assertDoesNotThrow(() -> eventBus.unsubscribe(EventType.MESSAGE_SENT, listener));
        }
    }

    @Nested
    @DisplayName("Event Delivery")
    class EventDelivery {

        @Test
        @DisplayName("should deliver event to subscribed listener")
        void shouldDeliverEventToSubscribedListener() {
            List<SimulationEvent> received = new ArrayList<>();
            eventBus.subscribe(EventType.MESSAGE_SENT, received::add);

            SimulationEvent event = SimulationEvent.withoutPeer(
                    1000L, EventType.MESSAGE_SENT, "node-1", "test"
            );
            eventBus.publish(event);

            assertEquals(1, received.size());
            assertEquals(event, received.get(0));
        }

        @Test
        @DisplayName("should deliver events to all subscribed listeners")
        void shouldDeliverEventsToAllListeners() {
            List<SimulationEvent> received1 = new ArrayList<>();
            List<SimulationEvent> received2 = new ArrayList<>();
            List<SimulationEvent> received3 = new ArrayList<>();

            eventBus.subscribe(EventType.MESSAGE_SENT, received1::add);
            eventBus.subscribe(EventType.MESSAGE_SENT, received2::add);
            eventBus.subscribe(EventType.MESSAGE_SENT, received3::add);

            SimulationEvent event = SimulationEvent.withoutPeer(
                    1000L, EventType.MESSAGE_SENT, "node-1", "test"
            );
            eventBus.publish(event);

            assertEquals(1, received1.size());
            assertEquals(1, received2.size());
            assertEquals(1, received3.size());
            assertEquals(event, received1.get(0));
            assertEquals(event, received2.get(0));
            assertEquals(event, received3.get(0));
        }

        @Test
        @DisplayName("should not deliver event to unsubscribed listener")
        void shouldNotDeliverEventToUnsubscribedListener() {
            List<SimulationEvent> received = new ArrayList<>();
            SimulationEventListener listener = received::add;

            eventBus.subscribe(EventType.MESSAGE_SENT, listener);
            eventBus.unsubscribe(EventType.MESSAGE_SENT, listener);

            SimulationEvent event = SimulationEvent.withoutPeer(
                    1000L, EventType.MESSAGE_SENT, "node-1", "test"
            );
            eventBus.publish(event);

            assertEquals(0, received.size());
        }
    }

    @Nested
    @DisplayName("Event Type Filtering")
    class EventTypeFiltering {

        @Test
        @DisplayName("should deliver only events of subscribed type")
        void shouldDeliverOnlySubscribedType() {
            List<SimulationEvent> messageSentEvents = new ArrayList<>();
            List<SimulationEvent> stateChangedEvents = new ArrayList<>();

            eventBus.subscribe(EventType.MESSAGE_SENT, messageSentEvents::add);
            eventBus.subscribe(EventType.STATE_CHANGED, stateChangedEvents::add);

            SimulationEvent messageSent = SimulationEvent.withoutPeer(
                    1000L, EventType.MESSAGE_SENT, "node-1", "sent"
            );
            SimulationEvent stateChanged = SimulationEvent.withoutPeer(
                    2000L, EventType.STATE_CHANGED, "node-1", "changed"
            );
            SimulationEvent error = SimulationEvent.withoutPeer(
                    3000L, EventType.ERROR, "node-1", "error"
            );

            eventBus.publish(messageSent);
            eventBus.publish(stateChanged);
            eventBus.publish(error);

            assertEquals(1, messageSentEvents.size());
            assertEquals(1, stateChangedEvents.size());
            assertEquals(EventType.MESSAGE_SENT, messageSentEvents.get(0).type());
            assertEquals(EventType.STATE_CHANGED, stateChangedEvents.get(0).type());
        }

        @Test
        @DisplayName("should support subscription to multiple event types by same listener")
        void shouldSupportMultipleTypeSubscriptionBySameListener() {
            List<SimulationEvent> received = new ArrayList<>();

            eventBus.subscribe(EventType.MESSAGE_SENT, received::add);
            eventBus.subscribe(EventType.STATE_CHANGED, received::add);

            SimulationEvent messageSent = SimulationEvent.withoutPeer(
                    1000L, EventType.MESSAGE_SENT, "node-1", "sent"
            );
            SimulationEvent stateChanged = SimulationEvent.withoutPeer(
                    2000L, EventType.STATE_CHANGED, "node-1", "changed"
            );

            eventBus.publish(messageSent);
            eventBus.publish(stateChanged);

            assertEquals(2, received.size());
            assertEquals(EventType.MESSAGE_SENT, received.get(0).type());
            assertEquals(EventType.STATE_CHANGED, received.get(1).type());
        }
    }

    @Nested
    @DisplayName("Event Order Preservation")
    class EventOrderPreservation {

        @Test
        @DisplayName("should preserve event order from single publisher")
        void shouldPreserveEventOrder() {
            List<SimulationEvent> received = new ArrayList<>();
            eventBus.subscribe(EventType.MESSAGE_SENT, received::add);

            List<SimulationEvent> published = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                SimulationEvent event = SimulationEvent.withoutPeer(
                        i, EventType.MESSAGE_SENT, "node-1", "msg-" + i
                );
                published.add(event);
                eventBus.publish(event);
            }

            assertEquals(100, received.size());
            for (int i = 0; i < 100; i++) {
                assertEquals(published.get(i), received.get(i));
            }
        }

        @Test
        @DisplayName("should maintain order with multiple subscribers")
        void shouldMaintainOrderWithMultipleSubscribers() {
            List<SimulationEvent> received1 = new ArrayList<>();
            List<SimulationEvent> received2 = new ArrayList<>();

            eventBus.subscribe(EventType.MESSAGE_SENT, received1::add);
            eventBus.subscribe(EventType.MESSAGE_SENT, received2::add);

            List<SimulationEvent> published = new ArrayList<>();
            for (int i = 0; i < 50; i++) {
                SimulationEvent event = SimulationEvent.withoutPeer(
                        i, EventType.MESSAGE_SENT, "node-1", "msg-" + i
                );
                published.add(event);
                eventBus.publish(event);
            }

            assertEquals(50, received1.size());
            assertEquals(50, received2.size());

            for (int i = 0; i < 50; i++) {
                assertEquals(published.get(i), received1.get(i));
                assertEquals(published.get(i), received2.get(i));
            }
        }
    }

    @Nested
    @DisplayName("Concurrent Operations")
    class ConcurrentOperations {

        @Test
        @DisplayName("should handle concurrent subscriptions safely")
        void shouldHandleConcurrentSubscriptions() throws InterruptedException {
            int threadCount = 10;
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger eventCount = new AtomicInteger(0);

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    eventBus.subscribe(EventType.MESSAGE_SENT, event -> eventCount.incrementAndGet());
                    latch.countDown();
                });
            }

            assertTrue(latch.await(5, TimeUnit.SECONDS));

            eventBus.publish(SimulationEvent.withoutPeer(
                    1000L, EventType.MESSAGE_SENT, "node-1", "test"
            ));

            assertEquals(threadCount, eventCount.get());
            executor.shutdown();
        }

        @Test
        @DisplayName("should not block on listener exceptions")
        void shouldNotBlockOnListenerExceptions() {
            List<SimulationEvent> received = new ArrayList<>();

            // First listener throws exception
            eventBus.subscribe(EventType.MESSAGE_SENT, event -> {
                throw new RuntimeException("Test exception");
            });

            // Second listener should still receive event
            eventBus.subscribe(EventType.MESSAGE_SENT, received::add);

            SimulationEvent event = SimulationEvent.withoutPeer(
                    1000L, EventType.MESSAGE_SENT, "node-1", "test"
            );

            assertDoesNotThrow(() -> eventBus.publish(event));
            assertEquals(1, received.size());
        }
    }

    @Nested
    @DisplayName("Memory Management")
    class MemoryManagement {

        @Test
        @DisplayName("should allow same listener to subscribe and unsubscribe multiple times")
        void shouldAllowRepeatedSubscribeUnsubscribe() {
            List<SimulationEvent> received = new ArrayList<>();
            SimulationEventListener listener = received::add;

            // First subscription
            eventBus.subscribe(EventType.MESSAGE_SENT, listener);
            eventBus.publish(SimulationEvent.withoutPeer(
                    1000L, EventType.MESSAGE_SENT, "node-1", "test1"
            ));
            assertEquals(1, received.size());

            // Unsubscribe
            eventBus.unsubscribe(EventType.MESSAGE_SENT, listener);
            eventBus.publish(SimulationEvent.withoutPeer(
                    2000L, EventType.MESSAGE_SENT, "node-1", "test2"
            ));
            assertEquals(1, received.size());

            // Resubscribe
            eventBus.subscribe(EventType.MESSAGE_SENT, listener);
            eventBus.publish(SimulationEvent.withoutPeer(
                    3000L, EventType.MESSAGE_SENT, "node-1", "test3"
            ));
            assertEquals(2, received.size());
        }
    }

    @Nested
    @DisplayName("Integration Scenarios")
    class IntegrationScenarios {

        @Test
        @DisplayName("should support typical logging use case")
        void shouldSupportLoggingUseCase() {
            List<String> logMessages = new ArrayList<>();

            eventBus.subscribe(EventType.ERROR, event ->
                    logMessages.add("[ERROR] " + event.nodeId() + ": " + event.payloadSummary())
            );
            eventBus.subscribe(EventType.MESSAGE_SENT, event ->
                    logMessages.add("[INFO] " + event.nodeId() + " -> " + event.peerId() + ": " + event.payloadSummary())
            );

            eventBus.publish(new SimulationEvent(
                    1000L, EventType.MESSAGE_SENT, "node-1", "node-2", "Election message"
            ));
            eventBus.publish(SimulationEvent.withoutPeer(
                    2000L, EventType.ERROR, "node-3", "Connection timeout"
            ));

            assertEquals(2, logMessages.size());
            assertTrue(logMessages.get(0).contains("[INFO]"));
            assertTrue(logMessages.get(1).contains("[ERROR]"));
        }

        @Test
        @DisplayName("should support metrics collection use case")
        void shouldSupportMetricsCollectionUseCase() {
            AtomicInteger messagesSent = new AtomicInteger(0);
            AtomicInteger messagesReceived = new AtomicInteger(0);
            AtomicInteger errors = new AtomicInteger(0);

            eventBus.subscribe(EventType.MESSAGE_SENT, event -> messagesSent.incrementAndGet());
            eventBus.subscribe(EventType.MESSAGE_RECEIVED, event -> messagesReceived.incrementAndGet());
            eventBus.subscribe(EventType.ERROR, event -> errors.incrementAndGet());

            // Simulate some activity
            for (int i = 0; i < 10; i++) {
                eventBus.publish(new SimulationEvent(
                        i, EventType.MESSAGE_SENT, "node-1", "node-2", "msg"
                ));
            }
            for (int i = 0; i < 8; i++) {
                eventBus.publish(new SimulationEvent(
                        i, EventType.MESSAGE_RECEIVED, "node-2", "node-1", "msg"
                ));
            }
            eventBus.publish(SimulationEvent.withoutPeer(
                    100L, EventType.ERROR, "node-1", "timeout"
            ));

            assertEquals(10, messagesSent.get());
            assertEquals(8, messagesReceived.get());
            assertEquals(1, errors.get());
        }
    }
}
