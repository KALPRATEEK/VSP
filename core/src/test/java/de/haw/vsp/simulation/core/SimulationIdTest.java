package de.haw.vsp.simulation.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link SimulationId}.
 *
 * Tests validation rules, JSON serialization/deserialization,
 * and ID generation.
 */
@DisplayName("SimulationId")
class SimulationIdTest {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Nested
    @DisplayName("Construction")
    class Construction {

        @Test
        @DisplayName("should create SimulationId with valid value")
        void shouldCreateSimulationIdWithValidValue() {
            SimulationId id = new SimulationId("sim-123");

            assertNotNull(id);
            assertEquals("sim-123", id.value());
        }

        @Test
        @DisplayName("should create SimulationId with UUID-like value")
        void shouldCreateSimulationIdWithUuidLikeValue() {
            String uuid = "550e8400-e29b-41d4-a716-446655440000";
            SimulationId id = new SimulationId(uuid);

            assertNotNull(id);
            assertEquals(uuid, id.value());
        }

        @Test
        @DisplayName("should reject null value")
        void shouldRejectNullValue() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new SimulationId(null)
            );
            assertTrue(exception.getMessage().contains("SimulationId value must not be null or blank"));
            assertTrue(exception.getMessage().contains("null"));
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "   ", "\t", "\n"})
        @DisplayName("should reject blank values")
        void shouldRejectBlankValues(String blankValue) {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new SimulationId(blankValue)
            );
            assertTrue(exception.getMessage().contains("SimulationId value must not be null or blank"));
        }
    }

    @Nested
    @DisplayName("ID Generation")
    class IdGeneration {

        @Test
        @DisplayName("should generate unique SimulationId")
        void shouldGenerateUniqueSimulationId() {
            SimulationId id1 = SimulationId.generate();
            SimulationId id2 = SimulationId.generate();

            assertNotNull(id1);
            assertNotNull(id2);
            assertNotEquals(id1, id2);
            assertNotEquals(id1.value(), id2.value());
        }

        @Test
        @DisplayName("should generate valid UUID format")
        void shouldGenerateValidUuidFormat() {
            SimulationId id = SimulationId.generate();

            // UUID format: 8-4-4-4-12 hex digits
            String uuidPattern = "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$";
            assertTrue(id.value().toLowerCase().matches(uuidPattern));
        }
    }

    @Nested
    @DisplayName("Equality")
    class Equality {

        @Test
        @DisplayName("should be equal for same value")
        void shouldBeEqualForSameValue() {
            SimulationId id1 = new SimulationId("sim-123");
            SimulationId id2 = new SimulationId("sim-123");

            assertEquals(id1, id2);
            assertEquals(id1.hashCode(), id2.hashCode());
        }

        @Test
        @DisplayName("should not be equal for different values")
        void shouldNotBeEqualForDifferentValues() {
            SimulationId id1 = new SimulationId("sim-123");
            SimulationId id2 = new SimulationId("sim-456");

            assertNotEquals(id1, id2);
        }

        @Test
        @DisplayName("should be equal to itself")
        void shouldBeEqualToItself() {
            SimulationId id = new SimulationId("sim-123");

            assertEquals(id, id);
        }
    }

    @Nested
    @DisplayName("String Representation")
    class StringRepresentation {

        @Test
        @DisplayName("should return value as toString")
        void shouldReturnValueAsToString() {
            SimulationId id = new SimulationId("sim-123");

            assertEquals("sim-123", id.toString());
        }
    }

    @Nested
    @DisplayName("JSON Serialization")
    class JsonSerialization {

        @Test
        @DisplayName("should serialize to JSON string value")
        void shouldSerializeToJsonStringValue() throws Exception {
            SimulationId id = new SimulationId("sim-123");

            String json = objectMapper.writeValueAsString(id);

            assertEquals("\"sim-123\"", json);
        }

        @Test
        @DisplayName("should deserialize from JSON string value")
        void shouldDeserializeFromJsonStringValue() throws Exception {
            String json = "\"sim-123\"";

            SimulationId id = objectMapper.readValue(json, SimulationId.class);

            assertNotNull(id);
            assertEquals("sim-123", id.value());
        }

        @Test
        @DisplayName("should maintain equality after serialization round-trip")
        void shouldMaintainEqualityAfterSerializationRoundTrip() throws Exception {
            SimulationId original = new SimulationId("sim-123");

            String json = objectMapper.writeValueAsString(original);
            SimulationId deserialized = objectMapper.readValue(json, SimulationId.class);

            assertEquals(original, deserialized);
        }

        @Test
        @DisplayName("should reject deserialization of blank JSON value")
        void shouldRejectDeserializationOfBlankJsonValue() {
            String json = "\"\"";

            assertThrows(
                    Exception.class,
                    () -> objectMapper.readValue(json, SimulationId.class)
            );
        }
    }
}
