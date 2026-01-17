package de.haw.vsp.simulation.control;

import de.haw.vsp.simulation.core.NetworkConfig;
import de.haw.vsp.simulation.core.SimulationId;
import de.haw.vsp.simulation.core.SimulationParameters;
import de.haw.vsp.simulation.core.TopologyType;
import de.haw.vsp.simulation.engine.DefaultSimulationEngine;
import de.haw.vsp.simulation.engine.SimulationEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link DefaultSimulationControl#selectAlgorithm(SimulationId, String)}.
 *
 * Tests verify the acceptance criteria:
 * - Algorithm is set before startSimulation
 * - Invalid AlgorithmId is rejected
 */
@DisplayName("DefaultSimulationControl - selectAlgorithm")
class DefaultSimulationControlSelectAlgorithmTest {

    private DefaultSimulationControl control;
    private SimulationId simulationId;

    @BeforeEach
    void setUp() {
        control = new DefaultSimulationControl();
        // Initialize a network first
        NetworkConfig config = new NetworkConfig(3, TopologyType.RING);
        simulationId = control.initializeNetwork(config);
    }

    @Nested
    @DisplayName("Success Cases")
    class SuccessCases {

        @Test
        @DisplayName("should select algorithm for initialized simulation")
        void shouldSelectAlgorithmForInitializedSimulation() {
            assertDoesNotThrow(() -> control.selectAlgorithm(simulationId, "flooding-leader-election"));
        }

        @Test
        @DisplayName("should allow algorithm selection before starting simulation")
        void shouldAllowAlgorithmSelectionBeforeStartingSimulation() {
            // Algorithm can be selected after initialization but before start
            assertDoesNotThrow(() -> control.selectAlgorithm(simulationId, "flooding-leader-election"));
            
            // Algorithm is now set and ready for simulation start
            // (startSimulation will be tested when implemented in SimulationControl)
        }

        @Test
        @DisplayName("should allow changing algorithm before starting")
        void shouldAllowChangingAlgorithmBeforeStarting() {
            control.selectAlgorithm(simulationId, "flooding-leader-election");
            
            // Should be able to change algorithm before starting
            assertDoesNotThrow(() -> control.selectAlgorithm(simulationId, "flooding-leader-election"));
        }

        @Test
        @DisplayName("should configure algorithm on engine")
        void shouldConfigureAlgorithmOnEngine() throws Exception {
            // Get the engine to verify algorithm was configured
            Field simulationsField = DefaultSimulationControl.class.getDeclaredField("simulations");
            simulationsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<SimulationId, SimulationEngine> simulations = 
                (Map<SimulationId, SimulationEngine>) simulationsField.get(control);
            
            SimulationEngine engine = simulations.get(simulationId);
            assertNotNull(engine);
            assertInstanceOf(DefaultSimulationEngine.class, engine);
            
            // Configure algorithm
            control.selectAlgorithm(simulationId, "flooding-leader-election");
            
            // Verify algorithm is configured by trying to start (should not throw algorithm-related exception)
            DefaultSimulationEngine defaultEngine = (DefaultSimulationEngine) engine;
            SimulationParameters params = new SimulationParameters(42L, 100, 10);
            assertDoesNotThrow(() -> defaultEngine.startSimulation(params));
        }
    }

    @Nested
    @DisplayName("Validation")
    class Validation {

        @Test
        @DisplayName("should reject null simulationId")
        void shouldRejectNullSimulationId() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> control.selectAlgorithm(null, "flooding-leader-election")
            );
            assertTrue(exception.getMessage().contains("simulationId must not be null"));
        }

        @Test
        @DisplayName("should reject null algorithmId")
        void shouldRejectNullAlgorithmId() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> control.selectAlgorithm(simulationId, null)
            );
            assertTrue(exception.getMessage().contains("algorithmId must not be null or blank"));
        }

        @Test
        @DisplayName("should reject blank algorithmId")
        void shouldRejectBlankAlgorithmId() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> control.selectAlgorithm(simulationId, "   ")
            );
            assertTrue(exception.getMessage().contains("algorithmId must not be null or blank"));
        }

        @Test
        @DisplayName("should reject invalid algorithmId")
        void shouldRejectInvalidAlgorithmId() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> control.selectAlgorithm(simulationId, "invalid-algorithm")
            );
            assertTrue(exception.getMessage().contains("Unsupported algorithm"));
        }

        @Test
        @DisplayName("should reject unknown simulationId")
        void shouldRejectUnknownSimulationId() {
            SimulationId unknownId = SimulationId.generate();
            
            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> control.selectAlgorithm(unknownId, "flooding-leader-election")
            );
            assertTrue(exception.getMessage().contains("Simulation not found"));
        }
    }

    @Nested
    @DisplayName("State Management")
    class StateManagement {

        @Test
        @DisplayName("should reject algorithm change while simulation is running")
        void shouldRejectAlgorithmChangeWhileSimulationIsRunning() throws Exception {
            control.selectAlgorithm(simulationId, "flooding-leader-election");
            
            // Start simulation by accessing engine directly
            Field simulationsField = DefaultSimulationControl.class.getDeclaredField("simulations");
            simulationsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<SimulationId, SimulationEngine> simulations = 
                (Map<SimulationId, SimulationEngine>) simulationsField.get(control);
            
            DefaultSimulationEngine engine = (DefaultSimulationEngine) simulations.get(simulationId);
            SimulationParameters params = new SimulationParameters(42L, 100, 10);
            engine.startSimulation(params);
            
            // Wait for simulation to be in RUNNING state
            int attempts = 0;
            while (engine.getState() != DefaultSimulationEngine.SimulationState.RUNNING && attempts < 50) {
                Thread.sleep(10);
                attempts++;
            }
            assertEquals(DefaultSimulationEngine.SimulationState.RUNNING, engine.getState(),
                    "Simulation should be running");
            
            // Now try to change algorithm - should fail
            IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> control.selectAlgorithm(simulationId, "flooding-leader-election")
            );
            assertTrue(exception.getMessage().contains("cannot be changed while simulation is running"));
        }

        @Test
        @DisplayName("should reject algorithm change while simulation is paused")
        void shouldRejectAlgorithmChangeWhileSimulationIsPaused() throws Exception {
            control.selectAlgorithm(simulationId, "flooding-leader-election");
            
            // Start and pause simulation
            Field simulationsField = DefaultSimulationControl.class.getDeclaredField("simulations");
            simulationsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<SimulationId, SimulationEngine> simulations = 
                (Map<SimulationId, SimulationEngine>) simulationsField.get(control);
            
            DefaultSimulationEngine engine = (DefaultSimulationEngine) simulations.get(simulationId);
            SimulationParameters params = new SimulationParameters(42L, 100, 10);
            engine.startSimulation(params);
            
            // Wait for simulation to be in RUNNING state
            int attempts = 0;
            while (engine.getState() != DefaultSimulationEngine.SimulationState.RUNNING && attempts < 50) {
                Thread.sleep(10);
                attempts++;
            }
            assertEquals(DefaultSimulationEngine.SimulationState.RUNNING, engine.getState(),
                    "Simulation should be running");
            
            engine.pauseSimulation();
            assertEquals(DefaultSimulationEngine.SimulationState.PAUSED, engine.getState(),
                    "Simulation should be paused");
            
            // Now try to change algorithm - should fail
            IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> control.selectAlgorithm(simulationId, "flooding-leader-election")
            );
            assertTrue(exception.getMessage().contains("cannot be changed while simulation is running or paused"));
        }

        @Test
        @DisplayName("should allow algorithm change when simulation is stopped")
        void shouldAllowAlgorithmChangeWhenSimulationIsStopped() throws Exception {
            control.selectAlgorithm(simulationId, "flooding-leader-election");
            
            // Start and stop simulation
            Field simulationsField = DefaultSimulationControl.class.getDeclaredField("simulations");
            simulationsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<SimulationId, SimulationEngine> simulations = 
                (Map<SimulationId, SimulationEngine>) simulationsField.get(control);
            
            DefaultSimulationEngine engine = (DefaultSimulationEngine) simulations.get(simulationId);
            SimulationParameters params = new SimulationParameters(42L, 100, 10);
            engine.startSimulation(params);
            
            // Wait for simulation to be in RUNNING state
            int attempts = 0;
            while (engine.getState() != DefaultSimulationEngine.SimulationState.RUNNING && attempts < 50) {
                Thread.sleep(10);
                attempts++;
            }
            
            engine.stopSimulation();
            
            // Wait for simulation to be stopped
            attempts = 0;
            while (engine.getState() != DefaultSimulationEngine.SimulationState.STOPPED && attempts < 50) {
                Thread.sleep(10);
                attempts++;
            }
            
            // Should be able to change algorithm after stopping
            assertDoesNotThrow(() -> control.selectAlgorithm(simulationId, "flooding-leader-election"));
        }

        @Test
        @DisplayName("should allow algorithm selection when simulation is initialized")
        void shouldAllowAlgorithmSelectionWhenSimulationIsInitialized() {
            // Simulation is initialized but not started
            assertDoesNotThrow(() -> control.selectAlgorithm(simulationId, "flooding-leader-election"));
        }
    }

    @Nested
    @DisplayName("Integration with startSimulation")
    class IntegrationWithStartSimulation {

        @Test
        @DisplayName("should require algorithm before starting simulation")
        void shouldRequireAlgorithmBeforeStartingSimulation() throws Exception {
            // Create a new simulation without selecting algorithm
            NetworkConfig config = new NetworkConfig(2, TopologyType.LINE);
            SimulationId newSimulationId = control.initializeNetwork(config);
            assertNotNull(newSimulationId);
            
            // Try to start without selecting algorithm - should fail
            Field simulationsField = DefaultSimulationControl.class.getDeclaredField("simulations");
            simulationsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<SimulationId, SimulationEngine> simulations = 
                (Map<SimulationId, SimulationEngine>) simulationsField.get(control);
            
            DefaultSimulationEngine engine = (DefaultSimulationEngine) simulations.get(newSimulationId);
            SimulationParameters params = new SimulationParameters(42L, 100, 10);
            
            IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> engine.startSimulation(params)
            );
            assertTrue(exception.getMessage().contains("Algorithm must be configured before starting simulation"));
        }
    }
}
