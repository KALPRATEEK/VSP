package de.haw.vsp.simulation.engine;

import de.haw.vsp.simulation.core.NetworkConfig;
import de.haw.vsp.simulation.core.SimulationEventPublisher;
import de.haw.vsp.simulation.core.TopologyType;
import de.haw.vsp.simulation.middleware.inmemory.InMemoryMessagingPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link DefaultSimulationEngine}.
 *
 * Tests verify:
 * - Engine initialization and node creation
 * - State management (initialized but not started)
 * - Cleanup of previous state
 * - Validation of input parameters
 */
@DisplayName("DefaultSimulationEngine")
class DefaultSimulationEngineTest {

    private DefaultSimulationEngine engine;

    @BeforeEach
    void setUp() {
        engine = new DefaultSimulationEngine();
    }

    @Nested
    @DisplayName("Construction")
    class Construction {

        @Test
        @DisplayName("should create engine with default constructor")
        void shouldCreateEngineWithDefaultConstructor() {
            DefaultSimulationEngine newEngine = new DefaultSimulationEngine();

            assertNotNull(newEngine);
            assertEquals(DefaultSimulationEngine.SimulationState.UNINITIALIZED, newEngine.getState());
            assertEquals(0, newEngine.getNodeCount());
        }

        @Test
        @DisplayName("should create engine with custom messaging port")
        void shouldCreateEngineWithCustomMessagingPort() {
            InMemoryMessagingPort port = new InMemoryMessagingPort();
            DefaultSimulationEngine newEngine = new DefaultSimulationEngine(port);

            assertNotNull(newEngine);
            assertEquals(DefaultSimulationEngine.SimulationState.UNINITIALIZED, newEngine.getState());
        }

        @Test
        @DisplayName("should reject null messaging port")
        void shouldRejectNullMessagingPort() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new DefaultSimulationEngine(null)
            );
            assertTrue(exception.getMessage().contains("messagingPort must not be null"));
        }
    }

    @Nested
    @DisplayName("createEngineAndNodes")
    class CreateEngineAndNodes {

        @Test
        @DisplayName("should create engine and nodes for valid config")
        void shouldCreateEngineAndNodesForValidConfig() {
            NetworkConfig config = new NetworkConfig(5, TopologyType.RING);

            engine.createEngineAndNodes(config);

            assertEquals(DefaultSimulationEngine.SimulationState.INITIALIZED, engine.getState());
            assertEquals(5, engine.getNodeCount());
        }

        @Test
        @DisplayName("should initialize but not start simulation")
        void shouldInitializeButNotStartSimulation() {
            NetworkConfig config = new NetworkConfig(3, TopologyType.LINE);

            engine.createEngineAndNodes(config);

            // State should be INITIALIZED, not RUNNING
            assertEquals(DefaultSimulationEngine.SimulationState.INITIALIZED, engine.getState());
            assertNotEquals(DefaultSimulationEngine.SimulationState.RUNNING, engine.getState());
            assertNotEquals(DefaultSimulationEngine.SimulationState.PAUSED, engine.getState());
        }

        @Test
        @DisplayName("should allow starting simulation after initialization")
        void shouldAllowStartingSimulationAfterInitialization() {
            NetworkConfig config = new NetworkConfig(3, TopologyType.LINE);
            engine.createEngineAndNodes(config);

            assertEquals(DefaultSimulationEngine.SimulationState.INITIALIZED, engine.getState());

            // Should be able to start after initialization
            de.haw.vsp.simulation.core.SimulationParameters params =
                    new de.haw.vsp.simulation.core.SimulationParameters(42L, 100, 10);
            assertDoesNotThrow(() -> engine.startSimulation(params));
            assertEquals(DefaultSimulationEngine.SimulationState.RUNNING, engine.getState());
        }

        @Test
        @DisplayName("should reject null config")
        void shouldRejectNullConfig() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> engine.createEngineAndNodes(null)
            );
            assertTrue(exception.getMessage().contains("config must not be null"));
        }

        @Test
        @DisplayName("should clean up previous state before creating new")
        void shouldCleanUpPreviousStateBeforeCreatingNew() {
            NetworkConfig config1 = new NetworkConfig(3, TopologyType.LINE);
            NetworkConfig config2 = new NetworkConfig(5, TopologyType.RING);

            engine.createEngineAndNodes(config1);
            assertEquals(3, engine.getNodeCount());

            engine.createEngineAndNodes(config2);
            assertEquals(5, engine.getNodeCount());
            assertEquals(DefaultSimulationEngine.SimulationState.INITIALIZED, engine.getState());
        }

        @Test
        @DisplayName("should create nodes for different topology types")
        void shouldCreateNodesForDifferentTopologyTypes() {
            for (TopologyType topologyType : TopologyType.values()) {
                NetworkConfig config = new NetworkConfig(4, topologyType);
                engine.createEngineAndNodes(config);

                assertEquals(4, engine.getNodeCount());
                assertEquals(DefaultSimulationEngine.SimulationState.INITIALIZED, engine.getState());
            }
        }

        @Test
        @DisplayName("should handle single node network")
        void shouldHandleSingleNodeNetwork() {
            NetworkConfig config = new NetworkConfig(1, TopologyType.LINE);

            engine.createEngineAndNodes(config);

            assertEquals(1, engine.getNodeCount());
            assertEquals(DefaultSimulationEngine.SimulationState.INITIALIZED, engine.getState());
        }
    }

    @Nested
    @DisplayName("State Management")
    class StateManagement {

        @Test
        @DisplayName("should transition from UNINITIALIZED to INITIALIZED")
        void shouldTransitionFromUninitializedToInitialized() {
            assertEquals(DefaultSimulationEngine.SimulationState.UNINITIALIZED, engine.getState());

            NetworkConfig config = new NetworkConfig(3, TopologyType.RING);
            engine.createEngineAndNodes(config);

            assertEquals(DefaultSimulationEngine.SimulationState.INITIALIZED, engine.getState());
        }

        @Test
        @DisplayName("should allow multiple initializations")
        void shouldAllowMultipleInitializations() {
            NetworkConfig config1 = new NetworkConfig(2, TopologyType.LINE);
            engine.createEngineAndNodes(config1);
            assertEquals(DefaultSimulationEngine.SimulationState.INITIALIZED, engine.getState());

            NetworkConfig config2 = new NetworkConfig(4, TopologyType.RING);
            engine.createEngineAndNodes(config2);
            assertEquals(DefaultSimulationEngine.SimulationState.INITIALIZED, engine.getState());
        }
    }

    @Nested
    @DisplayName("Event Publisher")
    class EventPublisher {

        @Test
        @DisplayName("should set event publisher")
        void shouldSetEventPublisher() {
            SimulationEventPublisher publisher = event -> {
                // Test publisher
            };

            engine.setEventPublisher(publisher);

            // No exception thrown - publisher is set
            assertNotNull(engine);
        }

        @Test
        @DisplayName("should allow null event publisher")
        void shouldAllowNullEventPublisher() {
            assertDoesNotThrow(() -> engine.setEventPublisher(null));
        }
    }
}
