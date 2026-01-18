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
 * Unit tests for NodeId.
 *
 * Verifies:
 * - Valid construction
 * - Validation of null/blank values
 * - Equality and comparison
 * - JSON serialization/deserialization
 * - String representation
 */
@DisplayName("NodeId")
class NodeIdTest {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Nested
    @DisplayName("Construction")
    class Construction {

        @Test
        @DisplayName("should create NodeId with valid value")
        void shouldCreateNodeIdWithValidValue() {
            NodeId nodeId = new NodeId("node-1");

            assertNotNull(nodeId);
            assertEquals("node-1", nodeId.value());
        }

        @Test
        @DisplayName("should create NodeId with numeric value")
        void shouldCreateNodeIdWithNumericValue() {
            NodeId nodeId = new NodeId("123");

            assertNotNull(nodeId);
            assertEquals("123", nodeId.value());
        }

        @Test
        @DisplayName("should reject null value")
        void shouldRejectNullValue() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new NodeId(null)
            );
            assertTrue(exception.getMessage().contains("NodeId value must not be null or blank"));
            assertTrue(exception.getMessage().contains("null"));
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "   ", "\t", "\n"})
        @DisplayName("should reject blank values")
        void shouldRejectBlankValues(String blankValue) {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new NodeId(blankValue)
            );
            assertTrue(exception.getMessage().contains("NodeId value must not be null or blank"));
        }
    }

    @Nested
    @DisplayName("Equality")
    class Equality {

        @Test
        @DisplayName("should be equal for same value")
        void shouldBeEqualForSameValue() {
            NodeId nodeId1 = new NodeId("node-1");
            NodeId nodeId2 = new NodeId("node-1");

            assertEquals(nodeId1, nodeId2);
            assertEquals(nodeId1.hashCode(), nodeId2.hashCode());
        }

        @Test
        @DisplayName("should not be equal for different values")
        void shouldNotBeEqualForDifferentValues() {
            NodeId nodeId1 = new NodeId("node-1");
            NodeId nodeId2 = new NodeId("node-2");

            assertNotEquals(nodeId1, nodeId2);
        }

        @Test
        @DisplayName("should be equal to itself")
        void shouldBeEqualToItself() {
            NodeId nodeId = new NodeId("node-1");

            assertEquals(nodeId, nodeId);
        }
    }

    @Nested
    @DisplayName("Comparison")
    class Comparison {

        @Test
        @DisplayName("should compare lexicographically")
        void shouldCompareLexicographically() {
            NodeId nodeA = new NodeId("node-a");
            NodeId nodeB = new NodeId("node-b");
            NodeId nodeC = new NodeId("node-c");

            assertTrue(nodeA.compareTo(nodeB) < 0);
            assertTrue(nodeB.compareTo(nodeA) > 0);
            assertTrue(nodeB.compareTo(nodeC) < 0);
            assertEquals(0, nodeA.compareTo(nodeA));
        }

        @Test
        @DisplayName("should compare numeric strings correctly")
        void shouldCompareNumericStringsCorrectly() {
            NodeId node1 = new NodeId("1");
            NodeId node2 = new NodeId("2");
            NodeId node10 = new NodeId("10");

            assertTrue(node1.compareTo(node2) < 0);
            // Note: Lexicographic comparison, not numeric
            assertTrue(node10.compareTo(node2) > 0); // "10" < "2" lexicographically
        }

    }

    @Nested
    @DisplayName("String Representation")
    class StringRepresentation {

        @Test
        @DisplayName("should return value as toString")
        void shouldReturnValueAsToString() {
            NodeId nodeId = new NodeId("node-1");

            assertEquals("node-1", nodeId.toString());
        }
    }

    @Nested
    @DisplayName("JSON Serialization")
    class JsonSerialization {

        @Test
        @DisplayName("should serialize to JSON string value")
        void shouldSerializeToJsonStringValue() throws JsonProcessingException {
            NodeId nodeId = new NodeId("node-1");

            String json = objectMapper.writeValueAsString(nodeId);

            assertEquals("\"node-1\"", json);
        }

        @Test
        @DisplayName("should deserialize from JSON string value")
        void shouldDeserializeFromJsonStringValue() throws JsonProcessingException {
            String json = "\"node-1\"";

            NodeId nodeId = objectMapper.readValue(json, NodeId.class);

            assertNotNull(nodeId);
            assertEquals("node-1", nodeId.value());
        }

        @Test
        @DisplayName("should maintain equality after serialization round-trip")
        void shouldMaintainEqualityAfterSerializationRoundTrip() throws JsonProcessingException {
            NodeId original = new NodeId("node-1");

            String json = objectMapper.writeValueAsString(original);
            NodeId deserialized = objectMapper.readValue(json, NodeId.class);

            assertEquals(original, deserialized);
        }

        @Test
        @DisplayName("should reject deserialization of blank JSON value")
        void shouldRejectDeserializationOfBlankJsonValue() {
            String json = "\"\"";

            assertThrows(
                    JsonProcessingException.class,
                    () -> objectMapper.readValue(json, NodeId.class)
            );
        }
    }
}

