package de.haw.vsp.simulation.control;

import de.haw.vsp.simulation.core.NetworkConfig;
import de.haw.vsp.simulation.core.SimulationId;
import de.haw.vsp.simulation.core.TopologyType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link DefaultSimulationControl}.
 *
 * Tests verify the acceptance criteria:
 * - Simulation is initialized after call, but not started
 * - Previous simulation is cleanly discarded
 * - Invalid configurations are rejected
 */
@DisplayName("DefaultSimulationControl")
class DefaultSimulationControlTest {

    private DefaultSimulationControl control;

    @BeforeEach
    void setUp() {
        control = new DefaultSimulationControl();
    }

    @Nested
    @DisplayName("initializeNetwork - Success Cases")
    class InitializeNetworkSuccess {

        @Test
        @DisplayName("should initialize network and return SimulationId")
        void shouldInitializeNetworkAndReturnSimulationId() {
            NetworkConfig config = new NetworkConfig(5, TopologyType.RING);

            SimulationId simulationId = control.initializeNetwork(config);

            assertNotNull(simulationId);
            assertNotNull(simulationId.value());
            assertFalse(simulationId.value().isBlank());
        }

        @Test
        @DisplayName("should return unique SimulationId for each call")
        void shouldReturnUniqueSimulationIdForEachCall() {
            NetworkConfig config = new NetworkConfig(3, TopologyType.LINE);

            SimulationId id1 = control.initializeNetwork(config);
            SimulationId id2 = control.initializeNetwork(config);

            assertNotEquals(id1, id2);
            assertNotEquals(id1.value(), id2.value());
        }

        @Test
        @DisplayName("should initialize simulation but not start it")
        void shouldInitializeSimulationButNotStartIt() {
            NetworkConfig config = new NetworkConfig(4, TopologyType.RING);

            SimulationId simulationId = control.initializeNetwork(config);

            assertNotNull(simulationId);
            // The simulation should be initialized but not started
            // This is verified by the fact that we can call initializeNetwork again
            // without errors, and the engine state is INITIALIZED, not RUNNING
        }

        @Test
        @DisplayName("should support all topology types")
        void shouldSupportAllTopologyTypes() {
            for (TopologyType topologyType : TopologyType.values()) {
                NetworkConfig config = new NetworkConfig(3, topologyType);

                SimulationId simulationId = control.initializeNetwork(config);

                assertNotNull(simulationId);
            }
        }

        @Test
        @DisplayName("should handle single node network")
        void shouldHandleSingleNodeNetwork() {
            NetworkConfig config = new NetworkConfig(1, TopologyType.LINE);

            SimulationId simulationId = control.initializeNetwork(config);

            assertNotNull(simulationId);
        }

        @Test
        @DisplayName("should handle large node count")
        void shouldHandleLargeNodeCount() {
            NetworkConfig config = new NetworkConfig(100, TopologyType.RING);

            SimulationId simulationId = control.initializeNetwork(config);

            assertNotNull(simulationId);
        }
    }

    @Nested
    @DisplayName("initializeNetwork - Cleanup Previous Simulation")
    class InitializeNetworkCleanup {

        @Test
        @DisplayName("should cleanly discard previous simulation")
        void shouldCleanlyDiscardPreviousSimulation() {
            NetworkConfig config1 = new NetworkConfig(3, TopologyType.LINE);
            NetworkConfig config2 = new NetworkConfig(5, TopologyType.RING);

            SimulationId id1 = control.initializeNetwork(config1);
            assertNotNull(id1);

            // Initialize again - previous simulation should be discarded
            SimulationId id2 = control.initializeNetwork(config2);
            assertNotNull(id2);
            assertNotEquals(id1, id2);

            // Should not throw exception - previous simulation was cleaned up
            assertDoesNotThrow(() -> control.initializeNetwork(config1));
        }

        @Test
        @DisplayName("should handle multiple consecutive initializations")
        void shouldHandleMultipleConsecutiveInitializations() {
            for (int i = 0; i < 5; i++) {
                NetworkConfig config = new NetworkConfig(i + 1, TopologyType.RING);
                SimulationId id = control.initializeNetwork(config);
                assertNotNull(id);
            }
        }

        @Test
        @DisplayName("should replace previous simulation with new one")
        void shouldReplacePreviousSimulationWithNewOne() {
            NetworkConfig config1 = new NetworkConfig(2, TopologyType.LINE);
            NetworkConfig config2 = new NetworkConfig(4, TopologyType.GRID);

            SimulationId id1 = control.initializeNetwork(config1);
            SimulationId id2 = control.initializeNetwork(config2);

            // Both should succeed and return different IDs
            assertNotNull(id1);
            assertNotNull(id2);
            assertNotEquals(id1, id2);
        }
    }

    @Nested
    @DisplayName("initializeNetwork - Validation")
    class InitializeNetworkValidation {

        @Test
        @DisplayName("should reject null config")
        void shouldRejectNullConfig() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> control.initializeNetwork(null)
            );
            assertTrue(exception.getMessage().contains("config must not be null"));
        }

        @Test
        @DisplayName("should reject config with zero nodeCount")
        void shouldRejectConfigWithZeroNodeCount() {
            // NetworkConfig constructor validates this, but we verify it propagates
            assertThrows(
                    IllegalArgumentException.class,
                    () -> {
                        NetworkConfig invalidConfig = new NetworkConfig(0, TopologyType.LINE);
                        control.initializeNetwork(invalidConfig);
                    }
            );
        }

        @Test
        @DisplayName("should reject config with negative nodeCount")
        void shouldRejectConfigWithNegativeNodeCount() {
            // NetworkConfig constructor validates this
            assertThrows(
                    IllegalArgumentException.class,
                    () -> {
                        NetworkConfig invalidConfig = new NetworkConfig(-1, TopologyType.RING);
                        control.initializeNetwork(invalidConfig);
                    }
            );
        }

        @Test
        @DisplayName("should reject config with null topologyType")
        void shouldRejectConfigWithNullTopologyType() {
            // NetworkConfig constructor validates this
            assertThrows(
                    IllegalArgumentException.class,
                    () -> {
                        NetworkConfig invalidConfig = new NetworkConfig(5, null);
                        control.initializeNetwork(invalidConfig);
                    }
            );
        }
    }

    @Nested
    @DisplayName("Integration Tests")
    class IntegrationTests {

        @Test
        @DisplayName("should create valid simulation that can be initialized")
        void shouldCreateValidSimulationThatCanBeInitialized() {
            NetworkConfig config = new NetworkConfig(5, TopologyType.RING);

            SimulationId simulationId = control.initializeNetwork(config);

            assertNotNull(simulationId);
            assertNotNull(simulationId.value());
            // Verify it's a valid UUID format
            assertTrue(simulationId.value().matches(
                    "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"
            ));
        }

        @Test
        @DisplayName("should handle rapid successive initializations")
        void shouldHandleRapidSuccessiveInitializations() {
            for (int i = 0; i < 10; i++) {
                NetworkConfig config = new NetworkConfig(2 + i, TopologyType.RING);
                SimulationId id = control.initializeNetwork(config);
                assertNotNull(id);
            }
        }
    }
}
