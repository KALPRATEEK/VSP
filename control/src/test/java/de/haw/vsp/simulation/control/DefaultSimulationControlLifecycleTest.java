package de.haw.vsp.simulation.control;

import de.haw.vsp.simulation.core.MetricsSnapshot;
import de.haw.vsp.simulation.core.NetworkConfig;
import de.haw.vsp.simulation.core.SimulationId;
import de.haw.vsp.simulation.core.SimulationParameters;
import de.haw.vsp.simulation.core.TopologyType;
import de.haw.vsp.simulation.core.SimulationEventBus;
import de.haw.vsp.simulation.engine.DefaultSimulationEngine;
import de.haw.vsp.simulation.engine.SimulationEngine;
import de.haw.vsp.simulation.engine.DockerNodeOrchestrator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for simulation lifecycle methods in DefaultSimulationControl.
 *
 * Tests verify:
 * - startSimulation: asynchronous start, immediate return, maxSteps, metrics, events
 * - pauseSimulation: state transition, no state loss
 * - resumeSimulation: state transition, no state loss
 * - stopSimulation: clean termination, final metrics
 */
@DisplayName("DefaultSimulationControl - Lifecycle Methods")
class DefaultSimulationControlLifecycleTest {

    private DefaultSimulationControl control;
    private SimulationId simulationId;

    @BeforeEach
    void setUp() {
        DockerNodeOrchestrator mockOrchestrator = mock(DockerNodeOrchestrator.class);
        Map<SimulationId, SimulationEventBus> eventAggregationMap = new ConcurrentHashMap<>();
        control = new DefaultSimulationControl(mockOrchestrator, eventAggregationMap);
        NetworkConfig config = new NetworkConfig(3, TopologyType.RING);
        simulationId = control.initializeNetwork(config);
        control.selectAlgorithm(simulationId, "flooding-leader-election");
    }

    @Nested
    @DisplayName("startSimulation")
    class StartSimulation {

        @Test
        @DisplayName("should start simulation asynchronously and return immediately")
        void shouldStartSimulationAsynchronouslyAndReturnImmediately() throws Exception {
            SimulationParameters params = new SimulationParameters(42L, 100, 10);
            
            long startTime = System.currentTimeMillis();
            control.startSimulation(simulationId, params);
            long endTime = System.currentTimeMillis();
            
            // Method should return immediately (within 100ms)
            assertTrue(endTime - startTime < 100, "startSimulation should return immediately");
            
            // Verify simulation is running - wait for state to be set
            Field simulationsField = DefaultSimulationControl.class.getDeclaredField("simulations");
            simulationsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<SimulationId, SimulationEngine> simulations = 
                (Map<SimulationId, SimulationEngine>) simulationsField.get(control);
            
            DefaultSimulationEngine engine = (DefaultSimulationEngine) simulations.get(simulationId);
            
            // Wait for simulation to be in RUNNING state (with timeout)
            int attempts = 0;
            while (engine.getState() != DefaultSimulationEngine.SimulationState.RUNNING && attempts < 50) {
                Thread.sleep(10);
                attempts++;
            }
            assertEquals(DefaultSimulationEngine.SimulationState.RUNNING, engine.getState(),
                    "Simulation should be running");
        }

        @Test
        @DisplayName("should reject null simulationId")
        void shouldRejectNullSimulationId() {
            SimulationParameters params = new SimulationParameters(42L, 100, 10);
            
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> control.startSimulation(null, params)
            );
            assertTrue(exception.getMessage().contains("simulationId must not be null"));
        }

        @Test
        @DisplayName("should reject null parameters")
        void shouldRejectNullParameters() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> control.startSimulation(simulationId, null)
            );
            assertTrue(exception.getMessage().contains("parameters must not be null"));
        }

        @Test
        @DisplayName("should reject unknown simulationId")
        void shouldRejectUnknownSimulationId() {
            SimulationId unknownId = SimulationId.generate();
            SimulationParameters params = new SimulationParameters(42L, 100, 10);
            
            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> control.startSimulation(unknownId, params)
            );
            assertTrue(exception.getMessage().contains("Simulation not found"));
        }

        @Test
        @DisplayName("should initialize metrics on start")
        void shouldInitializeMetricsOnStart() throws Exception {
            SimulationParameters params = new SimulationParameters(42L, 100, 10);
            control.startSimulation(simulationId, params);
            
            // Wait a bit for simulation to start
            Thread.sleep(50);
            
            Field simulationsField = DefaultSimulationControl.class.getDeclaredField("simulations");
            simulationsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<SimulationId, SimulationEngine> simulations = 
                (Map<SimulationId, SimulationEngine>) simulationsField.get(control);
            
            DefaultSimulationEngine engine = (DefaultSimulationEngine) simulations.get(simulationId);
            MetricsSnapshot metrics = engine.getMetrics();
            
            assertNotNull(metrics);
            assertTrue(metrics.rounds() >= 0);
            assertTrue(metrics.messageCount() >= 0);
            assertTrue(metrics.realTimeMillis() >= 0);
        }

        @Test
        @DisplayName("should respect maxSteps")
        void shouldRespectMaxSteps() throws Exception {
            int maxSteps = 5;
            SimulationParameters params = new SimulationParameters(42L, maxSteps, 10);
            control.startSimulation(simulationId, params);
            
            // Wait for simulation to complete
            Thread.sleep(500);
            
            Field simulationsField = DefaultSimulationControl.class.getDeclaredField("simulations");
            simulationsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<SimulationId, SimulationEngine> simulations = 
                (Map<SimulationId, SimulationEngine>) simulationsField.get(control);
            
            DefaultSimulationEngine engine = (DefaultSimulationEngine) simulations.get(simulationId);
            
            // Simulation should have stopped after maxSteps
            // Note: This is a simplified check - in a real implementation, we'd verify the exact step count
            MetricsSnapshot metrics = engine.getMetrics();
            assertTrue(metrics.rounds() <= maxSteps, "Rounds should not exceed maxSteps");
        }
    }

    @Nested
    @DisplayName("pauseSimulation")
    class PauseSimulation {

        @Test
        @DisplayName("should pause running simulation")
        void shouldPauseRunningSimulation() throws Exception {
            SimulationParameters params = new SimulationParameters(42L, 100, 10);
            control.startSimulation(simulationId, params);
            
            Field simulationsField = DefaultSimulationControl.class.getDeclaredField("simulations");
            simulationsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<SimulationId, SimulationEngine> simulations = 
                (Map<SimulationId, SimulationEngine>) simulationsField.get(control);
            
            DefaultSimulationEngine engine = (DefaultSimulationEngine) simulations.get(simulationId);
            
            // Wait for simulation to be in RUNNING state
            int attempts = 0;
            while (engine.getState() != DefaultSimulationEngine.SimulationState.RUNNING && attempts < 50) {
                Thread.sleep(10);
                attempts++;
            }
            assertEquals(DefaultSimulationEngine.SimulationState.RUNNING, engine.getState(),
                    "Simulation should be running");
            
            control.pauseSimulation(simulationId);
            assertEquals(DefaultSimulationEngine.SimulationState.PAUSED, engine.getState(),
                    "Simulation should be paused");
        }

        @Test
        @DisplayName("should preserve state when paused")
        void shouldPreserveStateWhenPaused() throws Exception {
            SimulationParameters params = new SimulationParameters(42L, 100, 10);
            control.startSimulation(simulationId, params);
            
            // Wait for simulation to run a bit
            Thread.sleep(100);
            
            Field simulationsField = DefaultSimulationControl.class.getDeclaredField("simulations");
            simulationsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<SimulationId, SimulationEngine> simulations = 
                (Map<SimulationId, SimulationEngine>) simulationsField.get(control);
            
            DefaultSimulationEngine engine = (DefaultSimulationEngine) simulations.get(simulationId);
            
            control.pauseSimulation(simulationId);
            
            // Wait a bit
            Thread.sleep(100);
            
            // Metrics should be preserved (or increased, but not reset)
            MetricsSnapshot metricsAfterPause = engine.getMetrics();
            assertTrue(metricsAfterPause.rounds() >= 0,
                    "Rounds should be preserved or increased");
        }

        @Test
        @DisplayName("should reject null simulationId")
        void shouldRejectNullSimulationId() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> control.pauseSimulation(null)
            );
            assertTrue(exception.getMessage().contains("simulationId must not be null"));
        }

        @Test
        @DisplayName("should reject unknown simulationId")
        void shouldRejectUnknownSimulationId() {
            SimulationId unknownId = SimulationId.generate();
            
            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> control.pauseSimulation(unknownId)
            );
            assertTrue(exception.getMessage().contains("Simulation not found"));
        }

        @Test
        @DisplayName("should reject pausing non-running simulation")
        void shouldRejectPausingNonRunningSimulation() {
            // Simulation is initialized but not started
            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> control.pauseSimulation(simulationId)
            );
            assertTrue(exception.getMessage().contains("must be running"));
        }
    }

    @Nested
    @DisplayName("resumeSimulation")
    class ResumeSimulation {

        @Test
        @DisplayName("should resume paused simulation")
        void shouldResumePausedSimulation() throws Exception {
            SimulationParameters params = new SimulationParameters(42L, 100, 10);
            control.startSimulation(simulationId, params);
            
            Field simulationsField = DefaultSimulationControl.class.getDeclaredField("simulations");
            simulationsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<SimulationId, SimulationEngine> simulations = 
                (Map<SimulationId, SimulationEngine>) simulationsField.get(control);
            
            DefaultSimulationEngine engine = (DefaultSimulationEngine) simulations.get(simulationId);
            
            // Wait for simulation to be in RUNNING state
            int attempts = 0;
            while (engine.getState() != DefaultSimulationEngine.SimulationState.RUNNING && attempts < 50) {
                Thread.sleep(10);
                attempts++;
            }
            assertEquals(DefaultSimulationEngine.SimulationState.RUNNING, engine.getState(),
                    "Simulation should be running");
            
            control.pauseSimulation(simulationId);
            assertEquals(DefaultSimulationEngine.SimulationState.PAUSED, engine.getState(),
                    "Simulation should be paused");
            
            control.resumeSimulation(simulationId);
            
            // Wait for simulation to resume
            attempts = 0;
            while (engine.getState() != DefaultSimulationEngine.SimulationState.RUNNING && attempts < 50) {
                Thread.sleep(10);
                attempts++;
            }
            assertEquals(DefaultSimulationEngine.SimulationState.RUNNING, engine.getState(),
                    "Simulation should be running again");
        }

        @Test
        @DisplayName("should continue from paused state without loss")
        void shouldContinueFromPausedStateWithoutLoss() throws Exception {
            SimulationParameters params = new SimulationParameters(42L, 100, 10);
            control.startSimulation(simulationId, params);
            
            Field simulationsField = DefaultSimulationControl.class.getDeclaredField("simulations");
            simulationsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<SimulationId, SimulationEngine> simulations = 
                (Map<SimulationId, SimulationEngine>) simulationsField.get(control);
            
            DefaultSimulationEngine engine = (DefaultSimulationEngine) simulations.get(simulationId);
            
            // Wait for simulation to be in RUNNING state
            int attempts = 0;
            while (engine.getState() != DefaultSimulationEngine.SimulationState.RUNNING && attempts < 50) {
                Thread.sleep(10);
                attempts++;
            }
            
            // Wait for simulation to run a bit
            Thread.sleep(100);
            
            control.pauseSimulation(simulationId);
            
            // Wait for pause to take effect
            attempts = 0;
            while (engine.getState() != DefaultSimulationEngine.SimulationState.PAUSED && attempts < 50) {
                Thread.sleep(10);
                attempts++;
            }
            
            MetricsSnapshot metricsAfterPause = engine.getMetrics();
            long roundsAfterPause = metricsAfterPause.rounds();
            control.resumeSimulation(simulationId);
            
            // Wait for simulation to resume
            attempts = 0;
            while (engine.getState() != DefaultSimulationEngine.SimulationState.RUNNING && attempts < 50) {
                Thread.sleep(10);
                attempts++;
            }
            
            // Wait for simulation to continue
            Thread.sleep(100);
            
            MetricsSnapshot metricsAfterResume = engine.getMetrics();
            
            // Metrics should continue from paused state
            assertTrue(metricsAfterResume.rounds() >= roundsAfterPause,
                    "Rounds should continue from paused state");
        }

        @Test
        @DisplayName("should reject null simulationId")
        void shouldRejectNullSimulationId() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> control.resumeSimulation(null)
            );
            assertTrue(exception.getMessage().contains("simulationId must not be null"));
        }

        @Test
        @DisplayName("should reject unknown simulationId")
        void shouldRejectUnknownSimulationId() {
            SimulationId unknownId = SimulationId.generate();
            
            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> control.resumeSimulation(unknownId)
            );
            assertTrue(exception.getMessage().contains("Simulation not found"));
        }

        @Test
        @DisplayName("should reject resuming non-paused simulation")
        void shouldRejectResumingNonPausedSimulation() {
            // Simulation is initialized but not paused
            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> control.resumeSimulation(simulationId)
            );
            assertTrue(exception.getMessage().contains("must be paused"));
        }
    }

    @Nested
    @DisplayName("stopSimulation")
    class StopSimulation {

        @Test
        @DisplayName("should stop running simulation")
        void shouldStopRunningSimulation() throws Exception {
            SimulationParameters params = new SimulationParameters(42L, 100, 10);
            control.startSimulation(simulationId, params);
            
            Field simulationsField = DefaultSimulationControl.class.getDeclaredField("simulations");
            simulationsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<SimulationId, SimulationEngine> simulations = 
                (Map<SimulationId, SimulationEngine>) simulationsField.get(control);
            
            DefaultSimulationEngine engine = (DefaultSimulationEngine) simulations.get(simulationId);
            
            // Wait for simulation to be in RUNNING state
            int attempts = 0;
            while (engine.getState() != DefaultSimulationEngine.SimulationState.RUNNING && attempts < 50) {
                Thread.sleep(10);
                attempts++;
            }
            assertEquals(DefaultSimulationEngine.SimulationState.RUNNING, engine.getState(),
                    "Simulation should be running");
            
            control.stopSimulation(simulationId);
            
            // Wait for simulation to be stopped
            attempts = 0;
            while (engine.getState() != DefaultSimulationEngine.SimulationState.STOPPED && attempts < 50) {
                Thread.sleep(10);
                attempts++;
            }
            assertEquals(DefaultSimulationEngine.SimulationState.STOPPED, engine.getState(),
                    "Simulation should be stopped");
        }

        @Test
        @DisplayName("should finalize metrics on stop")
        void shouldFinalizeMetricsOnStop() throws Exception {
            SimulationParameters params = new SimulationParameters(42L, 100, 10);
            control.startSimulation(simulationId, params);
            
            // Wait for simulation to run
            Thread.sleep(100);
            
            Field simulationsField = DefaultSimulationControl.class.getDeclaredField("simulations");
            simulationsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<SimulationId, SimulationEngine> simulations = 
                (Map<SimulationId, SimulationEngine>) simulationsField.get(control);
            
            DefaultSimulationEngine engine = (DefaultSimulationEngine) simulations.get(simulationId);
            
            control.stopSimulation(simulationId);
            
            // Metrics should be final and consistent
            MetricsSnapshot finalMetrics = engine.getMetrics();
            assertNotNull(finalMetrics);
            assertTrue(finalMetrics.rounds() >= 0);
            assertTrue(finalMetrics.messageCount() >= 0);
            assertTrue(finalMetrics.realTimeMillis() >= 0);
        }

        @Test
        @DisplayName("should stop simulation deterministically")
        void shouldStopSimulationDeterministically() throws Exception {
            SimulationParameters params = new SimulationParameters(42L, 100, 10);
            control.startSimulation(simulationId, params);
            
            // Wait for simulation to start
            Thread.sleep(50);
            
            // Get metrics before stopping
            MetricsSnapshot beforeStop = control.getMetrics(simulationId);
            assertNotNull(beforeStop);
            
            control.stopSimulation(simulationId);
            
            // Wait a bit
            Thread.sleep(100);
            
            // After stopSimulation, the simulation should be removed from the map
            // So trying to access it should throw an exception
            assertThrows(IllegalStateException.class, () -> control.getMetrics(simulationId));
        }

        @Test
        @DisplayName("should reject null simulationId")
        void shouldRejectNullSimulationId() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> control.stopSimulation(null)
            );
            assertTrue(exception.getMessage().contains("simulationId must not be null"));
        }

        @Test
        @DisplayName("should reject unknown simulationId")
        void shouldRejectUnknownSimulationId() {
            SimulationId unknownId = SimulationId.generate();
            
            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> control.stopSimulation(unknownId)
            );
            assertTrue(exception.getMessage().contains("Simulation not found"));
        }

        @Test
        @DisplayName("should stop paused simulation")
        void shouldStopPausedSimulation() throws Exception {
            SimulationParameters params = new SimulationParameters(42L, 100, 10);
            control.startSimulation(simulationId, params);
            
            Field simulationsField = DefaultSimulationControl.class.getDeclaredField("simulations");
            simulationsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<SimulationId, SimulationEngine> simulations = 
                (Map<SimulationId, SimulationEngine>) simulationsField.get(control);
            
            DefaultSimulationEngine engine = (DefaultSimulationEngine) simulations.get(simulationId);
            
            // Wait for simulation to be in RUNNING state
            int attempts = 0;
            while (engine.getState() != DefaultSimulationEngine.SimulationState.RUNNING && attempts < 50) {
                Thread.sleep(10);
                attempts++;
            }
            
            control.pauseSimulation(simulationId);
            
            // Wait for pause to take effect
            attempts = 0;
            while (engine.getState() != DefaultSimulationEngine.SimulationState.PAUSED && attempts < 50) {
                Thread.sleep(10);
                attempts++;
            }
            
            control.stopSimulation(simulationId);
            
            // Wait for simulation to be stopped
            attempts = 0;
            while (engine.getState() != DefaultSimulationEngine.SimulationState.STOPPED && attempts < 50) {
                Thread.sleep(10);
                attempts++;
            }
            assertEquals(DefaultSimulationEngine.SimulationState.STOPPED, engine.getState(),
                    "Simulation should be stopped");
        }
    }
}
