package de.haw.vsp.simulation.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MetricsSnapshot.
 *
 * Covers:
 * - Immutability (record properties)
 * - Valid snapshot construction
 * - Validation of non-negative metrics
 * - Optional leaderId (may be null)
 * - JSON serialization/deserialization
 * - Factory methods (initial, withoutLeader)
 * - Reflection of actual simulation state
 * - Availability during and after simulation
 */
class MetricsSnapshotTest {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Nested
    @DisplayName("Immutability")
    class Immutability {

        @Test
        @DisplayName("should be immutable record")
        void shouldBeImmutableRecord() {
            MetricsSnapshot snapshot = new MetricsSnapshot(1000L, 5000L, 42L, 10L, true, "node-3");

            // Record fields are implicitly final - attempting to modify would cause compile error
            // We verify that getter methods return consistent values
            assertEquals(1000L, snapshot.simulatedTime());
            assertEquals(5000L, snapshot.realTimeMillis());
            assertEquals(42L, snapshot.messageCount());
            assertEquals(10L, snapshot.rounds());
            assertTrue(snapshot.converged());
            assertEquals("node-3", snapshot.leaderId());

            // Calling getters again should return same values
            assertEquals(1000L, snapshot.simulatedTime());
            assertEquals(5000L, snapshot.realTimeMillis());
        }
    }

    @Nested
    @DisplayName("Valid Construction")
    class ValidConstruction {

        @Test
        @DisplayName("should create valid MetricsSnapshot with all fields")
        void shouldCreateValidSnapshotWithAllFields() {
            MetricsSnapshot snapshot = new MetricsSnapshot(
                    1000L,
                    5000L,
                    42L,
                    10L,
                    true,
                    "node-5"
            );

            assertNotNull(snapshot);
            assertEquals(1000L, snapshot.simulatedTime());
            assertEquals(5000L, snapshot.realTimeMillis());
            assertEquals(42L, snapshot.messageCount());
            assertEquals(10L, snapshot.rounds());
            assertTrue(snapshot.converged());
            assertEquals("node-5", snapshot.leaderId());
        }

        @Test
        @DisplayName("should create snapshot with null leaderId")
        void shouldCreateSnapshotWithNullLeaderId() {
            MetricsSnapshot snapshot = new MetricsSnapshot(
                    2000L,
                    10000L,
                    100L,
                    20L,
                    false,
                    null
            );

            assertNotNull(snapshot);
            assertNull(snapshot.leaderId());
            assertFalse(snapshot.converged());
        }

        @Test
        @DisplayName("should create snapshot with zero values")
        void shouldCreateSnapshotWithZeroValues() {
            MetricsSnapshot snapshot = new MetricsSnapshot(0L, 0L, 0L, 0L, false, null);

            assertEquals(0L, snapshot.simulatedTime());
            assertEquals(0L, snapshot.realTimeMillis());
            assertEquals(0L, snapshot.messageCount());
            assertEquals(0L, snapshot.rounds());
            assertFalse(snapshot.converged());
            assertNull(snapshot.leaderId());
        }

        @Test
        @DisplayName("should create snapshot representing unconverged state")
        void shouldCreateSnapshotRepresentingUnconvergedState() {
            MetricsSnapshot snapshot = new MetricsSnapshot(
                    500L,
                    2500L,
                    15L,
                    5L,
                    false,
                    null
            );

            assertFalse(snapshot.converged());
            assertNull(snapshot.leaderId());
        }

        @Test
        @DisplayName("should create snapshot representing converged state with leader")
        void shouldCreateSnapshotRepresentingConvergedStateWithLeader() {
            MetricsSnapshot snapshot = new MetricsSnapshot(
                    3000L,
                    15000L,
                    200L,
                    50L,
                    true,
                    "node-7"
            );

            assertTrue(snapshot.converged());
            assertEquals("node-7", snapshot.leaderId());
        }
    }

    @Nested
    @DisplayName("Validation of Non-Negative Metrics")
    class ValidationOfNonNegativeMetrics {

        @ParameterizedTest
        @ValueSource(longs = {-1L, -10L, -100L, Long.MIN_VALUE})
        @DisplayName("should reject negative simulatedTime")
        void shouldRejectNegativeSimulatedTime(long negativeTime) {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new MetricsSnapshot(negativeTime, 0L, 0L, 0L, false, null)
            );

            assertTrue(exception.getMessage().contains("simulatedTime"));
            assertTrue(exception.getMessage().contains("non-negative"));
            assertTrue(exception.getMessage().contains(String.valueOf(negativeTime)));
        }

        @ParameterizedTest
        @ValueSource(longs = {-1L, -10L, -100L, Long.MIN_VALUE})
        @DisplayName("should reject negative realTimeMillis")
        void shouldRejectNegativeRealTimeMillis(long negativeTime) {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new MetricsSnapshot(0L, negativeTime, 0L, 0L, false, null)
            );

            assertTrue(exception.getMessage().contains("realTimeMillis"));
            assertTrue(exception.getMessage().contains("non-negative"));
            assertTrue(exception.getMessage().contains(String.valueOf(negativeTime)));
        }

        @ParameterizedTest
        @ValueSource(longs = {-1L, -10L, -100L, Long.MIN_VALUE})
        @DisplayName("should reject negative messageCount")
        void shouldRejectNegativeMessageCount(long negativeCount) {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new MetricsSnapshot(0L, 0L, negativeCount, 0L, false, null)
            );

            assertTrue(exception.getMessage().contains("messageCount"));
            assertTrue(exception.getMessage().contains("non-negative"));
            assertTrue(exception.getMessage().contains(String.valueOf(negativeCount)));
        }

        @ParameterizedTest
        @ValueSource(longs = {-1L, -10L, -100L, Long.MIN_VALUE})
        @DisplayName("should reject negative rounds")
        void shouldRejectNegativeRounds(long negativeRounds) {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new MetricsSnapshot(0L, 0L, 0L, negativeRounds, false, null)
            );

            assertTrue(exception.getMessage().contains("rounds"));
            assertTrue(exception.getMessage().contains("non-negative"));
            assertTrue(exception.getMessage().contains(String.valueOf(negativeRounds)));
        }
    }

    @Nested
    @DisplayName("Optional LeaderId")
    class OptionalLeaderId {

        @Test
        @DisplayName("should allow null leaderId when no leader elected")
        void shouldAllowNullLeaderIdWhenNoLeaderElected() {
            MetricsSnapshot snapshot = new MetricsSnapshot(1000L, 5000L, 50L, 10L, false, null);

            assertNull(snapshot.leaderId());
        }

        @Test
        @DisplayName("should allow null leaderId even when converged")
        void shouldAllowNullLeaderIdEvenWhenConverged() {
            // Some algorithms may converge without electing a leader
            MetricsSnapshot snapshot = new MetricsSnapshot(1000L, 5000L, 50L, 10L, true, null);

            assertTrue(snapshot.converged());
            assertNull(snapshot.leaderId());
        }

        @Test
        @DisplayName("should allow non-null leaderId when leader is elected")
        void shouldAllowNonNullLeaderIdWhenLeaderIsElected() {
            MetricsSnapshot snapshot = new MetricsSnapshot(1000L, 5000L, 50L, 10L, true, "node-1");

            assertEquals("node-1", snapshot.leaderId());
        }

        @Test
        @DisplayName("should allow empty string as leaderId")
        void shouldAllowEmptyStringAsLeaderId() {
            MetricsSnapshot snapshot = new MetricsSnapshot(1000L, 5000L, 50L, 10L, true, "");

            assertEquals("", snapshot.leaderId());
        }
    }

    @Nested
    @DisplayName("Factory Methods")
    class FactoryMethods {

        @Test
        @DisplayName("should create initial snapshot with all zeros")
        void shouldCreateInitialSnapshotWithAllZeros() {
            MetricsSnapshot snapshot = MetricsSnapshot.initial();

            assertNotNull(snapshot);
            assertEquals(0L, snapshot.simulatedTime());
            assertEquals(0L, snapshot.realTimeMillis());
            assertEquals(0L, snapshot.messageCount());
            assertEquals(0L, snapshot.rounds());
            assertFalse(snapshot.converged());
            assertNull(snapshot.leaderId());
        }

        @Test
        @DisplayName("should create snapshot without leader using factory method")
        void shouldCreateSnapshotWithoutLeaderUsingFactoryMethod() {
            MetricsSnapshot snapshot = MetricsSnapshot.withoutLeader(
                    1500L,
                    7500L,
                    75L,
                    15L,
                    false
            );

            assertNotNull(snapshot);
            assertEquals(1500L, snapshot.simulatedTime());
            assertEquals(7500L, snapshot.realTimeMillis());
            assertEquals(75L, snapshot.messageCount());
            assertEquals(15L, snapshot.rounds());
            assertFalse(snapshot.converged());
            assertNull(snapshot.leaderId());
        }

        @Test
        @DisplayName("should create converged snapshot without leader using factory method")
        void shouldCreateConvergedSnapshotWithoutLeaderUsingFactoryMethod() {
            MetricsSnapshot snapshot = MetricsSnapshot.withoutLeader(
                    2000L,
                    10000L,
                    100L,
                    20L,
                    true
            );

            assertTrue(snapshot.converged());
            assertNull(snapshot.leaderId());
        }
    }

    @Nested
    @DisplayName("JSON Serialization - Compact and Pollable")
    class JsonSerialization {

        @Test
        @DisplayName("should serialize to JSON")
        void shouldSerializeToJson() throws JsonProcessingException {
            MetricsSnapshot snapshot = new MetricsSnapshot(
                    1000L,
                    5000L,
                    42L,
                    10L,
                    true,
                    "node-3"
            );

            String json = objectMapper.writeValueAsString(snapshot);

            assertNotNull(json);
            assertTrue(json.contains("\"simulatedTime\":1000"));
            assertTrue(json.contains("\"realTimeMillis\":5000"));
            assertTrue(json.contains("\"messageCount\":42"));
            assertTrue(json.contains("\"rounds\":10"));
            assertTrue(json.contains("\"converged\":true"));
            assertTrue(json.contains("\"leaderId\":\"node-3\""));
        }

        @Test
        @DisplayName("should serialize snapshot with null leaderId to JSON")
        void shouldSerializeSnapshotWithNullLeaderIdToJson() throws JsonProcessingException {
            MetricsSnapshot snapshot = new MetricsSnapshot(
                    2000L,
                    10000L,
                    100L,
                    20L,
                    false,
                    null
            );

            String json = objectMapper.writeValueAsString(snapshot);

            assertNotNull(json);
            assertTrue(json.contains("\"leaderId\":null"));
            assertTrue(json.contains("\"converged\":false"));
        }

        @Test
        @DisplayName("should deserialize from JSON")
        void shouldDeserializeFromJson() throws JsonProcessingException {
            String json = """
                    {
                        "simulatedTime": 1000,
                        "realTimeMillis": 5000,
                        "messageCount": 42,
                        "rounds": 10,
                        "converged": true,
                        "leaderId": "node-3"
                    }
                    """;

            MetricsSnapshot snapshot = objectMapper.readValue(json, MetricsSnapshot.class);

            assertNotNull(snapshot);
            assertEquals(1000L, snapshot.simulatedTime());
            assertEquals(5000L, snapshot.realTimeMillis());
            assertEquals(42L, snapshot.messageCount());
            assertEquals(10L, snapshot.rounds());
            assertTrue(snapshot.converged());
            assertEquals("node-3", snapshot.leaderId());
        }

        @Test
        @DisplayName("should deserialize snapshot with null leaderId from JSON")
        void shouldDeserializeSnapshotWithNullLeaderIdFromJson() throws JsonProcessingException {
            String json = """
                    {
                        "simulatedTime": 2000,
                        "realTimeMillis": 10000,
                        "messageCount": 100,
                        "rounds": 20,
                        "converged": false,
                        "leaderId": null
                    }
                    """;

            MetricsSnapshot snapshot = objectMapper.readValue(json, MetricsSnapshot.class);

            assertNotNull(snapshot);
            assertEquals(2000L, snapshot.simulatedTime());
            assertFalse(snapshot.converged());
            assertNull(snapshot.leaderId());
        }

        @Test
        @DisplayName("should roundtrip JSON serialization without data loss")
        void shouldRoundtripJsonSerialization() throws JsonProcessingException {
            MetricsSnapshot original = new MetricsSnapshot(
                    3000L,
                    15000L,
                    200L,
                    50L,
                    true,
                    "node-7"
            );

            String json = objectMapper.writeValueAsString(original);
            MetricsSnapshot deserialized = objectMapper.readValue(json, MetricsSnapshot.class);

            assertEquals(original, deserialized);
        }

        @Test
        @DisplayName("should reject invalid JSON with negative simulatedTime")
        void shouldRejectInvalidJsonWithNegativeSimulatedTime() {
            String invalidJson = """
                    {
                        "simulatedTime": -100,
                        "realTimeMillis": 5000,
                        "messageCount": 42,
                        "rounds": 10,
                        "converged": true,
                        "leaderId": "node-3"
                    }
                    """;

            assertThrows(
                    Exception.class,
                    () -> objectMapper.readValue(invalidJson, MetricsSnapshot.class)
            );
        }

        @Test
        @DisplayName("should reject invalid JSON with negative messageCount")
        void shouldRejectInvalidJsonWithNegativeMessageCount() {
            String invalidJson = """
                    {
                        "simulatedTime": 1000,
                        "realTimeMillis": 5000,
                        "messageCount": -42,
                        "rounds": 10,
                        "converged": true,
                        "leaderId": "node-3"
                    }
                    """;

            assertThrows(
                    Exception.class,
                    () -> objectMapper.readValue(invalidJson, MetricsSnapshot.class)
            );
        }
    }

    @Nested
    @DisplayName("Reflection of Actual Simulation State")
    class ReflectionOfActualState {

        @Test
        @DisplayName("should reflect progress during simulation")
        void shouldReflectProgressDuringSimulation() {
            // Simulate progression over time
            MetricsSnapshot snapshot1 = new MetricsSnapshot(100L, 500L, 5L, 1L, false, null);
            MetricsSnapshot snapshot2 = new MetricsSnapshot(200L, 1000L, 10L, 2L, false, null);
            MetricsSnapshot snapshot3 = new MetricsSnapshot(300L, 1500L, 15L, 3L, true, "node-1");

            // All metrics should increase over time
            assertTrue(snapshot2.simulatedTime() > snapshot1.simulatedTime());
            assertTrue(snapshot3.simulatedTime() > snapshot2.simulatedTime());

            assertTrue(snapshot2.messageCount() > snapshot1.messageCount());
            assertTrue(snapshot3.messageCount() > snapshot2.messageCount());

            assertTrue(snapshot2.rounds() > snapshot1.rounds());
            assertTrue(snapshot3.rounds() > snapshot2.rounds());

            // Convergence achieved in final snapshot
            assertFalse(snapshot1.converged());
            assertFalse(snapshot2.converged());
            assertTrue(snapshot3.converged());
        }

        @Test
        @DisplayName("should reflect leader election outcome")
        void shouldReflectLeaderElectionOutcome() {
            MetricsSnapshot beforeElection = new MetricsSnapshot(
                    500L, 2500L, 25L, 5L, false, null
            );
            MetricsSnapshot afterElection = new MetricsSnapshot(
                    600L, 3000L, 30L, 6L, true, "node-5"
            );

            assertNull(beforeElection.leaderId());
            assertFalse(beforeElection.converged());

            assertEquals("node-5", afterElection.leaderId());
            assertTrue(afterElection.converged());
        }

        @Test
        @DisplayName("should reflect resource usage accurately")
        void shouldReflectResourceUsageAccurately() {
            MetricsSnapshot snapshot = new MetricsSnapshot(
                    1000L,
                    5000L,
                    1000L,  // High message count
                    100L,   // Many rounds
                    true,
                    "node-1"
            );

            // Verify metrics can capture high resource usage
            assertEquals(1000L, snapshot.messageCount());
            assertEquals(100L, snapshot.rounds());
        }
    }

    @Nested
    @DisplayName("Availability During and After Simulation")
    class AvailabilityDuringAndAfterSimulation {

        @Test
        @DisplayName("should be available at simulation start")
        void shouldBeAvailableAtSimulationStart() {
            MetricsSnapshot startSnapshot = MetricsSnapshot.initial();

            assertNotNull(startSnapshot);
            assertEquals(0L, startSnapshot.simulatedTime());
            assertEquals(0L, startSnapshot.rounds());
        }

        @Test
        @DisplayName("should be available during simulation")
        void shouldBeAvailableDuringSimulation() {
            MetricsSnapshot duringSnapshot = new MetricsSnapshot(
                    500L, 2500L, 25L, 5L, false, null
            );

            assertNotNull(duringSnapshot);
            assertFalse(duringSnapshot.converged());
        }

        @Test
        @DisplayName("should be available after simulation completes")
        void shouldBeAvailableAfterSimulationCompletes() {
            MetricsSnapshot finalSnapshot = new MetricsSnapshot(
                    2000L, 10000L, 100L, 20L, true, "node-3"
            );

            assertNotNull(finalSnapshot);
            assertTrue(finalSnapshot.converged());
            assertEquals("node-3", finalSnapshot.leaderId());
        }

        @Test
        @DisplayName("should support multiple snapshots over time")
        void shouldSupportMultipleSnapshotsOverTime() {
            MetricsSnapshot[] snapshots = new MetricsSnapshot[5];

            for (int i = 0; i < 5; i++) {
                snapshots[i] = new MetricsSnapshot(
                        i * 100L,
                        i * 500L,
                        i * 10L,
                        i,
                        false,
                        null
                );
            }

            // All snapshots should be independently accessible
            for (int i = 0; i < 5; i++) {
                assertNotNull(snapshots[i]);
                assertEquals(i * 100L, snapshots[i].simulatedTime());
            }
        }
    }

    @Nested
    @DisplayName("Equality and Consistency")
    class EqualityAndConsistency {

        @Test
        @DisplayName("should produce equal snapshots with same values")
        void shouldProduceEqualSnapshotsWithSameValues() {
            MetricsSnapshot snapshot1 = new MetricsSnapshot(
                    1000L, 5000L, 42L, 10L, true, "node-3"
            );
            MetricsSnapshot snapshot2 = new MetricsSnapshot(
                    1000L, 5000L, 42L, 10L, true, "node-3"
            );

            assertEquals(snapshot1, snapshot2);
            assertEquals(snapshot1.hashCode(), snapshot2.hashCode());
        }

        @Test
        @DisplayName("should produce different snapshots with different simulatedTime")
        void shouldProduceDifferentSnapshotsWithDifferentSimulatedTime() {
            MetricsSnapshot snapshot1 = new MetricsSnapshot(
                    1000L, 5000L, 42L, 10L, true, "node-3"
            );
            MetricsSnapshot snapshot2 = new MetricsSnapshot(
                    2000L, 5000L, 42L, 10L, true, "node-3"
            );

            assertNotEquals(snapshot1, snapshot2);
        }

        @Test
        @DisplayName("should produce different snapshots with different converged flag")
        void shouldProduceDifferentSnapshotsWithDifferentConvergedFlag() {
            MetricsSnapshot snapshot1 = new MetricsSnapshot(
                    1000L, 5000L, 42L, 10L, true, "node-3"
            );
            MetricsSnapshot snapshot2 = new MetricsSnapshot(
                    1000L, 5000L, 42L, 10L, false, "node-3"
            );

            assertNotEquals(snapshot1, snapshot2);
        }
    }

    @Nested
    @DisplayName("Error Messages")
    class ErrorMessages {

        @Test
        @DisplayName("should provide clear error message for negative simulatedTime")
        void shouldProvideCleanErrorMessageForNegativeSimulatedTime() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new MetricsSnapshot(-100L, 5000L, 42L, 10L, true, "node-3")
            );

            String message = exception.getMessage();
            assertTrue(message.contains("simulatedTime"), "Error message should mention 'simulatedTime'");
            assertTrue(message.contains("non-negative"), "Error message should explain requirement");
            assertTrue(message.contains("-100"), "Error message should show actual invalid value");
        }

        @Test
        @DisplayName("should provide clear error message for negative messageCount")
        void shouldProvideCleanErrorMessageForNegativeMessageCount() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new MetricsSnapshot(1000L, 5000L, -42L, 10L, true, "node-3")
            );

            String message = exception.getMessage();
            assertTrue(message.contains("messageCount"), "Error message should mention 'messageCount'");
            assertTrue(message.contains("non-negative"), "Error message should explain requirement");
            assertTrue(message.contains("-42"), "Error message should show actual invalid value");
        }
    }
}

