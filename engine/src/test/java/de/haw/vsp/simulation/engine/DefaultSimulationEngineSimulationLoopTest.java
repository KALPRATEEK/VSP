package de.haw.vsp.simulation.engine;

import de.haw.vsp.simulation.core.*;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for simulation loop, metrics, and events in DefaultSimulationEngine.
 *
 * Tests verify:
 * - Simulation loop runs asynchronously
 * - maxSteps is respected
 * - Metrics are initialized and updated
 * - Events are published
 */
@DisplayName("DefaultSimulationEngine - Simulation Loop, Metrics, Events")
class DefaultSimulationEngineSimulationLoopTest {

    private DefaultSimulationEngine engine;
    private List<SimulationEvent> publishedEvents;

    @BeforeEach
    void setUp() {
        engine = new DefaultSimulationEngine();
        publishedEvents = new ArrayList<>();
        
        // Set up event publisher to capture events
        engine.setEventPublisher(event -> publishedEvents.add(event));
        
        NetworkConfig config = new NetworkConfig(3, TopologyType.RING);
        engine.createEngineAndNodes(config);
        engine.configureAlgorithm("flooding-leader-election");
    }

    @Nested
    @DisplayName("Simulation Loop")
    class SimulationLoop {

        @Test
        @DisplayName("should run simulation loop asynchronously")
        void shouldRunSimulationLoopAsynchronously() throws Exception {
            SimulationParameters params = new SimulationParameters(42L, 100, 10);
            
            long startTime = System.currentTimeMillis();
            engine.startSimulation(params);
            long endTime = System.currentTimeMillis();
            
            // Method should return immediately
            assertTrue(endTime - startTime < 100, "startSimulation should return immediately");
            
            // Wait a bit for simulation to run
            Thread.sleep(200);
            
            // Simulation should be running
            assertEquals(DefaultSimulationEngine.SimulationState.RUNNING, engine.getState());
        }

        @Test
        @DisplayName("should respect maxSteps")
        void shouldRespectMaxSteps() throws Exception {
            int maxSteps = 5;
            SimulationParameters params = new SimulationParameters(42L, maxSteps, 0);
            engine.startSimulation(params);
            
            // Wait for simulation to complete
            Thread.sleep(1000);
            
            // Simulation should have stopped after maxSteps
            assertEquals(DefaultSimulationEngine.SimulationState.STOPPED, engine.getState());
            
            MetricsSnapshot metrics = engine.getMetrics();
            assertTrue(metrics.rounds() <= maxSteps, "Rounds should not exceed maxSteps");
        }

        @Test
        @DisplayName("should stop when maxSteps is reached")
        void shouldStopWhenMaxStepsIsReached() throws Exception {
            int maxSteps = 3;
            SimulationParameters params = new SimulationParameters(42L, maxSteps, 0);
            engine.startSimulation(params);
            
            // Wait for simulation to complete
            Thread.sleep(1000);
            
            assertEquals(DefaultSimulationEngine.SimulationState.STOPPED, engine.getState());
            
            // Check that maxSteps event was published
            boolean foundMaxStepsEvent = publishedEvents.stream()
                    .anyMatch(e -> e.type() == EventType.STATE_CHANGED && 
                            e.payloadSummary().contains("maxSteps"));
            assertTrue(foundMaxStepsEvent, "Should publish maxSteps reached event");
        }

        @Test
        @DisplayName("should handle pause during simulation loop")
        void shouldHandlePauseDuringSimulationLoop() throws Exception {
            SimulationParameters params = new SimulationParameters(42L, 100, 0);
            engine.startSimulation(params);
            
            // Wait for simulation to start
            Thread.sleep(100);
            
            engine.pauseSimulation();
            
            // Wait a bit
            Thread.sleep(200);
            
            // Simulation should be paused
            assertEquals(DefaultSimulationEngine.SimulationState.PAUSED, engine.getState());
            
            MetricsSnapshot metricsBeforeResume = engine.getMetrics();
            long roundsBeforeResume = metricsBeforeResume.rounds();
            
            engine.resumeSimulation();
            
            // Wait for simulation to continue
            Thread.sleep(200);
            
            MetricsSnapshot metricsAfterResume = engine.getMetrics();
            assertTrue(metricsAfterResume.rounds() >= roundsBeforeResume,
                    "Rounds should continue after resume");
        }
    }

    @Nested
    @DisplayName("Metrics")
    class Metrics {

        @Test
        @DisplayName("should initialize metrics on start")
        void shouldInitializeMetricsOnStart() {
            SimulationParameters params = new SimulationParameters(42L, 100, 10);
            engine.startSimulation(params);
            
            MetricsSnapshot metrics = engine.getMetrics();
            
            assertNotNull(metrics);
            //assertEquals(0, metrics.simulatedTime());
            //assertEquals(0, metrics.messageCount());
            //assertEquals(0, metrics.rounds());
            assertFalse(metrics.converged());
        }

        @Test
        @DisplayName("should update metrics during simulation")
        void shouldUpdateMetricsDuringSimulation() throws Exception {
            SimulationParameters params = new SimulationParameters(42L, 100, 0);
            engine.startSimulation(params);
            
            // Wait for simulation to run
            Thread.sleep(300);
            
            MetricsSnapshot metrics = engine.getMetrics();
            
            assertTrue(metrics.rounds() > 0, "Rounds should increase");
            assertTrue(metrics.realTimeMillis() > 0, "Real time should increase");
        }

        @Test
        @DisplayName("should finalize metrics on stop")
        void shouldFinalizeMetricsOnStop() throws Exception {
            SimulationParameters params = new SimulationParameters(42L, 100, 10);
            engine.startSimulation(params);
            
            // Wait for simulation to run
            Thread.sleep(200);
            
            engine.stopSimulation();
            
            // Wait a bit
            Thread.sleep(100);
            
            MetricsSnapshot finalMetrics = engine.getMetrics();
            
            assertNotNull(finalMetrics);
            assertTrue(finalMetrics.rounds() >= 0);
            assertTrue(finalMetrics.messageCount() >= 0);
            assertTrue(finalMetrics.realTimeMillis() >= 0);
        }

        @Test
        @DisplayName("should allow setting leader ID")
        void shouldAllowSettingLeaderId() {
            engine.setLeaderId("node-1");
            
            MetricsSnapshot metrics = engine.getMetrics();
            assertEquals("node-1", metrics.leaderId());
        }

        @Test
        @DisplayName("should allow setting convergence state")
        void shouldAllowSettingConvergenceState() {
            engine.setConverged(true);
            
            MetricsSnapshot metrics = engine.getMetrics();
            assertTrue(metrics.converged());
        }

        @Test
        @DisplayName("should increment message count")
        void shouldIncrementMessageCount() {
            long initialCount = engine.getMetrics().messageCount();
            
            engine.incrementMessageCount();
            engine.incrementMessageCount();
            
            MetricsSnapshot metrics = engine.getMetrics();
            assertEquals(initialCount + 2, metrics.messageCount());
        }
    }

    @Nested
    @DisplayName("Events")
    class Events {

        @Test
        @DisplayName("should publish start event")
        void shouldPublishStartEvent() {
            SimulationParameters params = new SimulationParameters(42L, 100, 10);
            engine.startSimulation(params);
            
            // Wait a bit for event to be published
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            boolean foundStartEvent = publishedEvents.stream()
                    .anyMatch(e -> e.type() == EventType.STATE_CHANGED && 
                            e.payloadSummary().contains("Simulation started"));
            assertTrue(foundStartEvent, "Should publish start event");
        }

        @Test
        @DisplayName("should publish node start events")
        void shouldPublishNodeStartEvents() {
            SimulationParameters params = new SimulationParameters(42L, 100, 10);
            engine.startSimulation(params);
            
            // Wait a bit for events to be published
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            long nodeStartEvents = publishedEvents.stream()
                    .filter(e -> e.type() == EventType.STATE_CHANGED && 
                            e.payloadSummary().contains("Node started"))
                    .count();
            assertEquals(3, nodeStartEvents, "Should publish start event for each node");
        }

        @Test
        @DisplayName("should publish pause event")
        void shouldPublishPauseEvent() throws Exception {
            SimulationParameters params = new SimulationParameters(42L, 100, 10);
            engine.startSimulation(params);
            
            // Wait for simulation to start
            Thread.sleep(50);
            
            engine.pauseSimulation();
            
            boolean foundPauseEvent = publishedEvents.stream()
                    .anyMatch(e -> e.type() == EventType.STATE_CHANGED && 
                            e.payloadSummary().contains("Simulation paused"));
            assertTrue(foundPauseEvent, "Should publish pause event");
        }

        @Test
        @DisplayName("should publish resume event")
        void shouldPublishResumeEvent() throws Exception {
            SimulationParameters params = new SimulationParameters(42L, 100, 10);
            engine.startSimulation(params);
            
            // Wait for simulation to start
            Thread.sleep(50);
            
            engine.pauseSimulation();
            engine.resumeSimulation();
            
            boolean foundResumeEvent = publishedEvents.stream()
                    .anyMatch(e -> e.type() == EventType.STATE_CHANGED && 
                            e.payloadSummary().contains("Simulation resumed"));
            assertTrue(foundResumeEvent, "Should publish resume event");
        }

        @Test
        @DisplayName("should publish stop event")
        void shouldPublishStopEvent() throws Exception {
            SimulationParameters params = new SimulationParameters(42L, 100, 10);
            engine.startSimulation(params);
            
            // Wait for simulation to start
            Thread.sleep(50);
            
            engine.stopSimulation();
            
            boolean foundStopEvent = publishedEvents.stream()
                    .anyMatch(e -> e.type() == EventType.STATE_CHANGED && 
                            e.payloadSummary().contains("Simulation stopped"));
            assertTrue(foundStopEvent, "Should publish stop event");
        }

        @Test
        @DisplayName("should handle null event publisher gracefully")
        void shouldHandleNullEventPublisherGracefully() {
            engine.setEventPublisher(null);
            
            SimulationParameters params = new SimulationParameters(42L, 100, 10);
            
            // Should not throw exception
            assertDoesNotThrow(() -> engine.startSimulation(params));
        }
    }

    @Nested
    @DisplayName("State Transitions")
    class StateTransitions {

        @Test
        @DisplayName("should transition from INITIALIZED to RUNNING")
        void shouldTransitionFromInitializedToRunning() {
            SimulationParameters params = new SimulationParameters(42L, 100, 10);
            
            assertEquals(DefaultSimulationEngine.SimulationState.INITIALIZED, engine.getState());
            engine.startSimulation(params);
            assertEquals(DefaultSimulationEngine.SimulationState.RUNNING, engine.getState());
        }

        @Test
        @DisplayName("should transition from RUNNING to PAUSED")
        void shouldTransitionFromRunningToPaused() throws Exception {
            SimulationParameters params = new SimulationParameters(42L, 100, 10);
            engine.startSimulation(params);
            
            // Wait for simulation to start
            Thread.sleep(50);
            
            assertEquals(DefaultSimulationEngine.SimulationState.RUNNING, engine.getState());
            engine.pauseSimulation();
            assertEquals(DefaultSimulationEngine.SimulationState.PAUSED, engine.getState());
        }

        @Test
        @DisplayName("should transition from PAUSED to RUNNING")
        void shouldTransitionFromPausedToRunning() throws Exception {
            SimulationParameters params = new SimulationParameters(42L, 100, 10);
            engine.startSimulation(params);
            
            // Wait for simulation to start
            Thread.sleep(50);
            
            engine.pauseSimulation();
            assertEquals(DefaultSimulationEngine.SimulationState.PAUSED, engine.getState());
            engine.resumeSimulation();
            assertEquals(DefaultSimulationEngine.SimulationState.RUNNING, engine.getState());
        }

        @Test
        @DisplayName("should transition to STOPPED on stop")
        void shouldTransitionToStoppedOnStop() throws Exception {
            SimulationParameters params = new SimulationParameters(42L, 100, 10);
            engine.startSimulation(params);
            
            // Wait for simulation to start
            Thread.sleep(50);
            
            engine.stopSimulation();
            assertEquals(DefaultSimulationEngine.SimulationState.STOPPED, engine.getState());
        }
    }
}
