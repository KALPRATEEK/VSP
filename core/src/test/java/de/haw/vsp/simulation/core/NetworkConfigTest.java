package de.haw.vsp.simulation.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link NetworkConfig}.
 *
 * Tests validation rules, JSON serialization/deserialization,
 * and reconstruction of topology configuration.
 */
class NetworkConfigTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldCreateValidNetworkConfig() {
        NetworkConfig config = new NetworkConfig(5, TopologyType.RING);

        assertEquals(5, config.nodeCount());
        assertEquals(TopologyType.RING, config.topologyType());
    }

    @Test
    void shouldRejectZeroNodeCount() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new NetworkConfig(0, TopologyType.LINE)
        );
        assertTrue(exception.getMessage().contains("nodeCount must be greater than 0"));
    }

    @Test
    void shouldRejectNegativeNodeCount() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new NetworkConfig(-1, TopologyType.GRID)
        );
        assertTrue(exception.getMessage().contains("nodeCount must be greater than 0"));
    }

    @Test
    void shouldRejectNullTopologyType() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new NetworkConfig(5, null)
        );
        assertTrue(exception.getMessage().contains("topologyType must not be null"));
    }

    @Test
    void shouldSerializeToJson() throws Exception {
        NetworkConfig config = new NetworkConfig(10, TopologyType.RANDOM);

        String json = objectMapper.writeValueAsString(config);

        assertNotNull(json);
        assertTrue(json.contains("\"nodeCount\":10"));
        assertTrue(json.contains("\"topologyType\":\"RANDOM\""));
    }

    @Test
    void shouldDeserializeFromJson() throws Exception {
        String json = "{\"nodeCount\":8,\"topologyType\":\"GRID\"}";

        NetworkConfig config = objectMapper.readValue(json, NetworkConfig.class);

        assertNotNull(config);
        assertEquals(8, config.nodeCount());
        assertEquals(TopologyType.GRID, config.topologyType());
    }

    @Test
    void shouldPreserveTopologyAfterSerializationRoundtrip() throws Exception {
        NetworkConfig original = new NetworkConfig(15, TopologyType.LINE);

        String json = objectMapper.writeValueAsString(original);
        NetworkConfig deserialized = objectMapper.readValue(json, NetworkConfig.class);

        assertEquals(original.nodeCount(), deserialized.nodeCount());
        assertEquals(original.topologyType(), deserialized.topologyType());
        assertEquals(original, deserialized);
    }

    @Test
    void shouldFailDeserializationWithInvalidNodeCount() {
        String json = "{\"nodeCount\":0,\"topologyType\":\"RING\"}";

        assertThrows(Exception.class, () -> objectMapper.readValue(json, NetworkConfig.class));
    }

    @Test
    void shouldFailDeserializationWithNegativeNodeCount() {
        String json = "{\"nodeCount\":-5,\"topologyType\":\"RING\"}";

        assertThrows(Exception.class, () -> objectMapper.readValue(json, NetworkConfig.class));
    }

    @Test
    void shouldSupportAllTopologyTypes() {
        assertDoesNotThrow(() -> new NetworkConfig(1, TopologyType.LINE));
        assertDoesNotThrow(() -> new NetworkConfig(2, TopologyType.RING));
        assertDoesNotThrow(() -> new NetworkConfig(3, TopologyType.GRID));
        assertDoesNotThrow(() -> new NetworkConfig(4, TopologyType.RANDOM));
    }

    @Test
    void shouldMaintainImmutability() {
        NetworkConfig config = new NetworkConfig(7, TopologyType.RING);

        // Record fields are final and cannot be modified
        assertEquals(7, config.nodeCount());
        assertEquals(TopologyType.RING, config.topologyType());
    }

    @Test
    void shouldHaveProperEquality() {
        NetworkConfig config1 = new NetworkConfig(5, TopologyType.GRID);
        NetworkConfig config2 = new NetworkConfig(5, TopologyType.GRID);
        NetworkConfig config3 = new NetworkConfig(6, TopologyType.GRID);

        assertEquals(config1, config2);
        assertNotEquals(config1, config3);
    }

    @Test
    void shouldHaveConsistentHashCode() {
        NetworkConfig config1 = new NetworkConfig(5, TopologyType.GRID);
        NetworkConfig config2 = new NetworkConfig(5, TopologyType.GRID);

        assertEquals(config1.hashCode(), config2.hashCode());
    }

    @Test
    void shouldHaveReadableToString() {
        NetworkConfig config = new NetworkConfig(12, TopologyType.RANDOM);

        String toString = config.toString();

        assertNotNull(toString);
        assertTrue(toString.contains("12"));
        assertTrue(toString.contains("RANDOM"));
    }
}

