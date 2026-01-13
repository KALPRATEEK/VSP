package de.haw.vsp.simulation.engine;

import de.haw.vsp.simulation.core.NetworkConfig;
import de.haw.vsp.simulation.core.TopologyType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link DefaultSimulationEngine#configureAlgorithm(String)}.
 *
 * Tests verify:
 * - Algorithm configuration validation
 * - Algorithm recreation when nodes already exist
 * - State transitions
 */
@DisplayName("DefaultSimulationEngine - configureAlgorithm")
class DefaultSimulationEngineConfigureAlgorithmTest {

    private DefaultSimulationEngine engine;

    @BeforeEach
    void setUp() {
        engine = new DefaultSimulationEngine();
    }

    @Nested
    @DisplayName("Validation")
    class Validation {

        @Test
        @DisplayName("should reject null algorithmId")
        void shouldRejectNullAlgorithmId() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> engine.configureAlgorithm(null)
            );
            assertTrue(exception.getMessage().contains("algorithmId must not be null or blank"));
        }

        @Test
        @DisplayName("should reject blank algorithmId")
        void shouldRejectBlankAlgorithmId() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> engine.configureAlgorithm("   ")
            );
            assertTrue(exception.getMessage().contains("algorithmId must not be null or blank"));
        }

        @Test
        @DisplayName("should reject empty algorithmId")
        void shouldRejectEmptyAlgorithmId() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> engine.configureAlgorithm("")
            );
            assertTrue(exception.getMessage().contains("algorithmId must not be null or blank"));
        }

        @Test
        @DisplayName("should reject unsupported algorithmId")
        void shouldRejectUnsupportedAlgorithmId() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> engine.configureAlgorithm("unsupported-algorithm")
            );
            assertTrue(exception.getMessage().contains("Unsupported algorithm"));
        }
    }

    @Nested
    @DisplayName("Success Cases")
    class SuccessCases {

        @Test
        @DisplayName("should configure valid algorithm")
        void shouldConfigureValidAlgorithm() {
            assertDoesNotThrow(() -> engine.configureAlgorithm("flooding-leader-election"));
        }

        @Test
        @DisplayName("should configure algorithm before nodes are created")
        void shouldConfigureAlgorithmBeforeNodesAreCreated() {
            engine.configureAlgorithm("flooding-leader-election");
            
            // Should be able to create nodes after algorithm is configured
            NetworkConfig config = new NetworkConfig(3, TopologyType.RING);
            assertDoesNotThrow(() -> engine.createEngineAndNodes(config));
        }

        @Test
        @DisplayName("should configure algorithm after nodes are created")
        void shouldConfigureAlgorithmAfterNodesAreCreated() {
            NetworkConfig config = new NetworkConfig(3, TopologyType.RING);
            engine.createEngineAndNodes(config);
            
            // Should be able to configure algorithm after nodes are created
            assertDoesNotThrow(() -> engine.configureAlgorithm("flooding-leader-election"));
        }

        @Test
        @DisplayName("should recreate nodes when algorithm is changed")
        void shouldRecreateNodesWhenAlgorithmIsChanged() {
            NetworkConfig config = new NetworkConfig(3, TopologyType.RING);
            engine.createEngineAndNodes(config);
            
            int nodeCountBefore = engine.getNodeCount();
            
            // Configure algorithm - should recreate nodes
            engine.configureAlgorithm("flooding-leader-election");
            
            // Node count should remain the same
            assertEquals(nodeCountBefore, engine.getNodeCount());
            assertEquals(3, engine.getNodeCount());
        }

        @Test
        @DisplayName("should allow changing algorithm multiple times")
        void shouldAllowChangingAlgorithmMultipleTimes() {
            NetworkConfig config = new NetworkConfig(2, TopologyType.LINE);
            engine.createEngineAndNodes(config);
            
            engine.configureAlgorithm("flooding-leader-election");
            assertDoesNotThrow(() -> engine.configureAlgorithm("flooding-leader-election"));
        }
    }

    @Nested
    @DisplayName("State Management")
    class StateManagement {

        @Test
        @DisplayName("should configure algorithm in UNINITIALIZED state")
        void shouldConfigureAlgorithmInUninitializedState() {
            assertEquals(DefaultSimulationEngine.SimulationState.UNINITIALIZED, engine.getState());
            assertDoesNotThrow(() -> engine.configureAlgorithm("flooding-leader-election"));
        }

        @Test
        @DisplayName("should configure algorithm in INITIALIZED state")
        void shouldConfigureAlgorithmInInitializedState() {
            NetworkConfig config = new NetworkConfig(3, TopologyType.RING);
            engine.createEngineAndNodes(config);
            
            assertEquals(DefaultSimulationEngine.SimulationState.INITIALIZED, engine.getState());
            assertDoesNotThrow(() -> engine.configureAlgorithm("flooding-leader-election"));
        }
    }
}
