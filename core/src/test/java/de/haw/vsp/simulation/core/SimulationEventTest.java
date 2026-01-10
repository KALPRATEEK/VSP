package de.haw.vsp.simulation.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SimulationEvent.
 *
 * Covers:
 * - Valid event construction (with and without peer)
 * - Validation of required fields (type, nodeId, payloadSummary)
 * - Optional peerId (may be null)
 * - JSON serialization/deserialization
 * - Usability for UI and logging
 * - Consistent event creation across subsystems
 */
class SimulationEventTest {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Nested
    @DisplayName("Valid Construction")
    class ValidConstruction {

        @Test
        @DisplayName("should create valid SimulationEvent with all fields")
        void shouldCreateValidEventWithAllFields() {
            SimulationEvent event = new SimulationEvent(
                    1000L,
                    "MESSAGE_SENT",
                    "node-1",
                    "node-2",
                    "Election message: candidate=node-1"
            );

            assertNotNull(event);
            assertEquals(1000L, event.timestamp());
            assertEquals("MESSAGE_SENT", event.type());
            assertEquals("node-1", event.nodeId());
            assertEquals("node-2", event.peerId());
            assertEquals("Election message: candidate=node-1", event.payloadSummary());
        }

        @Test
        @DisplayName("should create valid SimulationEvent with null peerId")
        void shouldCreateValidEventWithNullPeerId() {
            SimulationEvent event = new SimulationEvent(
                    2000L,
                    "STATE_CHANGED",
                    "node-3",
                    null,
                    "Became leader"
            );

            assertNotNull(event);
            assertEquals(2000L, event.timestamp());
            assertEquals("STATE_CHANGED", event.type());
            assertEquals("node-3", event.nodeId());
            assertNull(event.peerId());
            assertEquals("Became leader", event.payloadSummary());
        }

        @Test
        @DisplayName("should create event using withoutPeer factory method")
        void shouldCreateEventUsingWithoutPeerFactory() {
            SimulationEvent event = SimulationEvent.withoutPeer(
                    3000L,
                    "SIMULATION_STARTED",
                    "node-0",
                    "Simulation initialized"
            );

            assertNotNull(event);
            assertEquals(3000L, event.timestamp());
            assertEquals("SIMULATION_STARTED", event.type());
            assertEquals("node-0", event.nodeId());
            assertNull(event.peerId());
            assertEquals("Simulation initialized", event.payloadSummary());
        }

        @Test
        @DisplayName("should allow empty payloadSummary string")
        void shouldAllowEmptyPayloadSummary() {
            SimulationEvent event = new SimulationEvent(
                    1000L,
                    "HEARTBEAT",
                    "node-1",
                    "node-2",
                    ""
            );

            assertEquals("", event.payloadSummary());
        }

        @Test
        @DisplayName("should allow negative timestamp")
        void shouldAllowNegativeTimestamp() {
            SimulationEvent event = new SimulationEvent(
                    -100L,
                    "TEST_EVENT",
                    "node-1",
                    null,
                    "Test"
            );

            assertEquals(-100L, event.timestamp());
        }

        @Test
        @DisplayName("should allow zero timestamp")
        void shouldAllowZeroTimestamp() {
            SimulationEvent event = new SimulationEvent(
                    0L,
                    "INIT",
                    "node-0",
                    null,
                    "Initial event"
            );

            assertEquals(0L, event.timestamp());
        }
    }

    @Nested
    @DisplayName("Type Validation")
    class TypeValidation {

        @Test
        @DisplayName("should reject null type")
        void shouldRejectNullType() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new SimulationEvent(1000L, null, "node-1", "node-2", "payload")
            );

            assertTrue(exception.getMessage().contains("type"));
            assertTrue(exception.getMessage().contains("null or blank"));
            assertTrue(exception.getMessage().contains("null"));
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"", "   ", "\t", "\n"})
        @DisplayName("should reject blank type")
        void shouldRejectBlankType(String blankType) {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new SimulationEvent(1000L, blankType, "node-1", "node-2", "payload")
            );

            assertTrue(exception.getMessage().contains("type"));
            assertTrue(exception.getMessage().contains("null or blank"));
        }
    }

    @Nested
    @DisplayName("NodeId Validation")
    class NodeIdValidation {

        @Test
        @DisplayName("should reject null nodeId")
        void shouldRejectNullNodeId() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new SimulationEvent(1000L, "MESSAGE_SENT", null, "node-2", "payload")
            );

            assertTrue(exception.getMessage().contains("nodeId"));
            assertTrue(exception.getMessage().contains("null or blank"));
            assertTrue(exception.getMessage().contains("null"));
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"", "   ", "\t", "\n"})
        @DisplayName("should reject blank nodeId")
        void shouldRejectBlankNodeId(String blankNodeId) {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new SimulationEvent(1000L, "MESSAGE_SENT", blankNodeId, "node-2", "payload")
            );

            assertTrue(exception.getMessage().contains("nodeId"));
            assertTrue(exception.getMessage().contains("null or blank"));
        }
    }

    @Nested
    @DisplayName("PayloadSummary Validation")
    class PayloadSummaryValidation {

        @Test
        @DisplayName("should reject null payloadSummary")
        void shouldRejectNullPayloadSummary() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new SimulationEvent(1000L, "MESSAGE_SENT", "node-1", "node-2", null)
            );

            assertTrue(exception.getMessage().contains("payloadSummary"));
            assertTrue(exception.getMessage().contains("must not be null"));
        }
    }

    @Nested
    @DisplayName("PeerId Optionality")
    class PeerIdOptionality {

        @Test
        @DisplayName("should allow null peerId for system events")
        void shouldAllowNullPeerIdForSystemEvents() {
            SimulationEvent event = new SimulationEvent(
                    1000L,
                    "SYSTEM_ERROR",
                    "node-1",
                    null,
                    "Network timeout"
            );

            assertNull(event.peerId());
        }

        @Test
        @DisplayName("should allow null peerId for state change events")
        void shouldAllowNullPeerIdForStateChanges() {
            SimulationEvent event = new SimulationEvent(
                    2000L,
                    "STATE_CHANGED",
                    "node-5",
                    null,
                    "Transitioned to FOLLOWER"
            );

            assertNull(event.peerId());
        }

        @Test
        @DisplayName("should allow blank peerId")
        void shouldAllowBlankPeerId() {
            SimulationEvent event = new SimulationEvent(
                    1000L,
                    "BROADCAST",
                    "node-1",
                    "",
                    "Broadcast message"
            );

            assertEquals("", event.peerId());
        }
    }

    @Nested
    @DisplayName("Event Equality and Consistency")
    class EventEqualityAndConsistency {

        @Test
        @DisplayName("should produce equal events with same fields")
        void shouldProduceEqualEventsWithSameFields() {
            SimulationEvent event1 = new SimulationEvent(
                    1000L, "MESSAGE_SENT", "node-1", "node-2", "Election message"
            );
            SimulationEvent event2 = new SimulationEvent(
                    1000L, "MESSAGE_SENT", "node-1", "node-2", "Election message"
            );

            assertEquals(event1, event2);
            assertEquals(event1.hashCode(), event2.hashCode());
        }

        @Test
        @DisplayName("should produce different events with different timestamps")
        void shouldProduceDifferentEventsWithDifferentTimestamps() {
            SimulationEvent event1 = new SimulationEvent(
                    1000L, "MESSAGE_SENT", "node-1", "node-2", "payload"
            );
            SimulationEvent event2 = new SimulationEvent(
                    2000L, "MESSAGE_SENT", "node-1", "node-2", "payload"
            );

            assertNotEquals(event1, event2);
        }

        @Test
        @DisplayName("should produce different events with different types")
        void shouldProduceDifferentEventsWithDifferentTypes() {
            SimulationEvent event1 = new SimulationEvent(
                    1000L, "MESSAGE_SENT", "node-1", "node-2", "payload"
            );
            SimulationEvent event2 = new SimulationEvent(
                    1000L, "MESSAGE_RECEIVED", "node-1", "node-2", "payload"
            );

            assertNotEquals(event1, event2);
        }

        @Test
        @DisplayName("should produce consistent events across subsystems")
        void shouldProduceConsistentEventsAcrossSubsystems() {
            // Simulating event creation from different subsystems
            SimulationEvent fromNode = new SimulationEvent(
                    5000L, "LEADER_ELECTED", "node-3", null, "Leader: node-3"
            );
            SimulationEvent fromMetrics = new SimulationEvent(
                    5000L, "LEADER_ELECTED", "node-3", null, "Leader: node-3"
            );

            assertEquals(fromNode, fromMetrics);
        }
    }

    @Nested
    @DisplayName("JSON Serialization - UI and Logging Compatibility")
    class JsonSerialization {

        @Test
        @DisplayName("should serialize event with all fields to JSON")
        void shouldSerializeEventWithAllFields() throws JsonProcessingException {
            SimulationEvent event = new SimulationEvent(
                    12345L,
                    "MESSAGE_SENT",
                    "node-1",
                    "node-2",
                    "Election: candidate=node-1, term=5"
            );

            String json = objectMapper.writeValueAsString(event);

            assertNotNull(json);
            assertTrue(json.contains("\"timestamp\":12345"));
            assertTrue(json.contains("\"type\":\"MESSAGE_SENT\""));
            assertTrue(json.contains("\"nodeId\":\"node-1\""));
            assertTrue(json.contains("\"peerId\":\"node-2\""));
            assertTrue(json.contains("\"payloadSummary\":\"Election: candidate=node-1, term=5\""));
        }

        @Test
        @DisplayName("should serialize event with null peerId to JSON")
        void shouldSerializeEventWithNullPeerId() throws JsonProcessingException {
            SimulationEvent event = new SimulationEvent(
                    9999L,
                    "STATE_CHANGED",
                    "node-7",
                    null,
                    "Became LEADER"
            );

            String json = objectMapper.writeValueAsString(event);

            assertNotNull(json);
            assertTrue(json.contains("\"timestamp\":9999"));
            assertTrue(json.contains("\"type\":\"STATE_CHANGED\""));
            assertTrue(json.contains("\"nodeId\":\"node-7\""));
            assertTrue(json.contains("\"peerId\":null"));
            assertTrue(json.contains("\"payloadSummary\":\"Became LEADER\""));
        }

        @Test
        @DisplayName("should deserialize event from JSON")
        void shouldDeserializeEventFromJson() throws JsonProcessingException {
            String json = """
                    {
                        "timestamp": 12345,
                        "type": "MESSAGE_SENT",
                        "nodeId": "node-1",
                        "peerId": "node-2",
                        "payloadSummary": "Test message"
                    }
                    """;

            SimulationEvent event = objectMapper.readValue(json, SimulationEvent.class);

            assertNotNull(event);
            assertEquals(12345L, event.timestamp());
            assertEquals("MESSAGE_SENT", event.type());
            assertEquals("node-1", event.nodeId());
            assertEquals("node-2", event.peerId());
            assertEquals("Test message", event.payloadSummary());
        }

        @Test
        @DisplayName("should deserialize event with null peerId from JSON")
        void shouldDeserializeEventWithNullPeerIdFromJson() throws JsonProcessingException {
            String json = """
                    {
                        "timestamp": 5000,
                        "type": "ERROR",
                        "nodeId": "node-3",
                        "peerId": null,
                        "payloadSummary": "Connection timeout"
                    }
                    """;

            SimulationEvent event = objectMapper.readValue(json, SimulationEvent.class);

            assertNotNull(event);
            assertEquals(5000L, event.timestamp());
            assertEquals("ERROR", event.type());
            assertEquals("node-3", event.nodeId());
            assertNull(event.peerId());
            assertEquals("Connection timeout", event.payloadSummary());
        }

        @Test
        @DisplayName("should roundtrip JSON serialization without data loss")
        void shouldRoundtripJsonSerialization() throws JsonProcessingException {
            SimulationEvent original = new SimulationEvent(
                    99999L,
                    "CONSENSUS_REACHED",
                    "node-10",
                    "node-11",
                    "Value agreed: 42"
            );

            String json = objectMapper.writeValueAsString(original);
            SimulationEvent deserialized = objectMapper.readValue(json, SimulationEvent.class);

            assertEquals(original, deserialized);
        }

        @Test
        @DisplayName("should reject invalid JSON with null type")
        void shouldRejectInvalidJsonWithNullType() {
            String invalidJson = """
                    {
                        "timestamp": 1000,
                        "type": null,
                        "nodeId": "node-1",
                        "peerId": "node-2",
                        "payloadSummary": "Test"
                    }
                    """;

            assertThrows(
                    Exception.class,
                    () -> objectMapper.readValue(invalidJson, SimulationEvent.class)
            );
        }

        @Test
        @DisplayName("should reject invalid JSON with blank nodeId")
        void shouldRejectInvalidJsonWithBlankNodeId() {
            String invalidJson = """
                    {
                        "timestamp": 1000,
                        "type": "MESSAGE_SENT",
                        "nodeId": "   ",
                        "peerId": "node-2",
                        "payloadSummary": "Test"
                    }
                    """;

            assertThrows(
                    Exception.class,
                    () -> objectMapper.readValue(invalidJson, SimulationEvent.class)
            );
        }
    }

    @Nested
    @DisplayName("Usability for UI and Logging")
    class UsabilityForUIAndLogging {

        @Test
        @DisplayName("should provide UI-friendly payloadSummary")
        void shouldProvideUIFriendlyPayloadSummary() {
            SimulationEvent event = new SimulationEvent(
                    1000L,
                    "MESSAGE_SENT",
                    "node-1",
                    "node-2",
                    "Election message: candidate=node-1, term=3"
            );

            String summary = event.payloadSummary();
            assertNotNull(summary);
            assertFalse(summary.isEmpty());
            // Summary should be concise and readable
            assertTrue(summary.length() < 200, "PayloadSummary should be concise for UI");
        }

        @Test
        @DisplayName("should contain all required metadata for logging")
        void shouldContainAllRequiredMetadataForLogging() {
            SimulationEvent event = new SimulationEvent(
                    System.currentTimeMillis(),
                    "ERROR",
                    "node-5",
                    null,
                    "Network partition detected"
            );

            // All required metadata present
            assertTrue(event.timestamp() >= 0);
            assertNotNull(event.type());
            assertNotNull(event.nodeId());
            assertNotNull(event.payloadSummary());
        }

        @Test
        @DisplayName("should support different event types consistently")
        void shouldSupportDifferentEventTypesConsistently() {
            SimulationEvent messageEvent = new SimulationEvent(
                    1000L, "MESSAGE_SENT", "node-1", "node-2", "Election msg"
            );
            SimulationEvent stateEvent = new SimulationEvent(
                    2000L, "STATE_CHANGED", "node-3", null, "Became LEADER"
            );
            SimulationEvent errorEvent = new SimulationEvent(
                    3000L, "ERROR", "node-4", null, "Timeout"
            );

            // All events have consistent structure
            assertNotNull(messageEvent.type());
            assertNotNull(stateEvent.type());
            assertNotNull(errorEvent.type());

            // Different types are distinguishable
            assertNotEquals(messageEvent.type(), stateEvent.type());
            assertNotEquals(stateEvent.type(), errorEvent.type());
        }
    }

    @Nested
    @DisplayName("Error Messages")
    class ErrorMessages {

        @Test
        @DisplayName("should provide clear error message for null type")
        void shouldProvideCleanErrorMessageForNullType() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new SimulationEvent(1000L, null, "node-1", "node-2", "payload")
            );

            String message = exception.getMessage();
            assertTrue(message.contains("type"), "Error message should mention 'type'");
            assertTrue(message.contains("null or blank"), "Error message should explain requirement");
        }

        @Test
        @DisplayName("should provide clear error message for blank type")
        void shouldProvideCleanErrorMessageForBlankType() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new SimulationEvent(1000L, "   ", "node-1", "node-2", "payload")
            );

            String message = exception.getMessage();
            assertTrue(message.contains("type"), "Error message should mention 'type'");
            assertTrue(message.contains("null or blank"), "Error message should explain requirement");
        }

        @Test
        @DisplayName("should provide clear error message for null nodeId")
        void shouldProvideCleanErrorMessageForNullNodeId() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new SimulationEvent(1000L, "MESSAGE_SENT", null, "node-2", "payload")
            );

            String message = exception.getMessage();
            assertTrue(message.contains("nodeId"), "Error message should mention 'nodeId'");
            assertTrue(message.contains("null or blank"), "Error message should explain requirement");
        }

        @Test
        @DisplayName("should provide clear error message for null payloadSummary")
        void shouldProvideCleanErrorMessageForNullPayloadSummary() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new SimulationEvent(1000L, "MESSAGE_SENT", "node-1", "node-2", null)
            );

            String message = exception.getMessage();
            assertTrue(message.contains("payloadSummary"), "Error message should mention 'payloadSummary'");
            assertTrue(message.contains("must not be null"), "Error message should explain requirement");
        }
    }
}

