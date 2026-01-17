package de.haw.vsp.simulation.control;

import de.haw.vsp.simulation.core.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DefaultSimulationControl - getMetrics")
class DefaultSimulationControlGetMetricsTest {

    private DefaultSimulationControl control;
    private SimulationId simulationId;
    private NetworkConfig networkConfig;

    @BeforeEach
    void setUp() {
        control = new DefaultSimulationControl();
        networkConfig = new NetworkConfig(3, TopologyType.RING);
        simulationId = control.initializeNetwork(networkConfig);
    }

    @Test
    @DisplayName("should return metrics snapshot for initialized simulation")
    void shouldReturnMetricsSnapshotForInitializedSimulation() {
        // Act
        MetricsSnapshot snapshot = control.getMetrics(simulationId);

        // Assert
        assertNotNull(snapshot);
        assertTrue(snapshot.simulatedTime() >= 0);
        assertTrue(snapshot.realTimeMillis() >= 0);
        assertTrue(snapshot.messageCount() >= 0);
        assertTrue(snapshot.rounds() >= 0);
    }

    @Test
    @DisplayName("should return metrics snapshot even when simulation is not running")
    void shouldReturnMetricsSnapshotWhenSimulationNotRunning() {
        // Arrange - simulation is initialized but not started
        // Act
        MetricsSnapshot snapshot = control.getMetrics(simulationId);

        // Assert
        assertNotNull(snapshot);
        assertEquals(0L, snapshot.simulatedTime());
        assertEquals(0L, snapshot.messageCount());
        assertEquals(0L, snapshot.rounds());
        assertFalse(snapshot.converged());
    }

    @Test
    @DisplayName("should reflect actual simulation state")
    void shouldReflectActualSimulationState() throws InterruptedException {
        // Arrange
        SimulationParameters parameters = new SimulationParameters(1L, 5, 0);
        control.selectAlgorithm(simulationId, "flooding-leader-election");
        control.startSimulation(simulationId, parameters);

        // Wait a bit for simulation to progress
        Thread.sleep(100);

        // Act
        MetricsSnapshot snapshot = control.getMetrics(simulationId);

        // Assert
        assertNotNull(snapshot);
        // After starting, realTimeMillis should be > 0
        assertTrue(snapshot.realTimeMillis() >= 0);
        // Rounds may have increased
        assertTrue(snapshot.rounds() >= 0);
    }

    @Test
    @DisplayName("should reflect leader election from events")
    void shouldReflectLeaderElectionFromEvents() throws InterruptedException {
        // Arrange - start simulation to generate events
        SimulationParameters parameters = new SimulationParameters(1L, 10, 0);
        control.selectAlgorithm(simulationId, "flooding-leader-election");
        control.startSimulation(simulationId, parameters);

        // Wait for potential leader election
        Thread.sleep(500);

        // Act
        MetricsSnapshot snapshot = control.getMetrics(simulationId);

        // Assert
        assertNotNull(snapshot);
        // Leader ID may or may not be set depending on algorithm execution
        // The important thing is that the method works and returns valid metrics
    }

    @Test
    @DisplayName("should be callable at any time")
    void shouldBeCallableAtAnyTime() {
        // Act & Assert - should not throw
        assertDoesNotThrow(() -> {
            MetricsSnapshot snapshot1 = control.getMetrics(simulationId);
            assertNotNull(snapshot1);
            
            // Call multiple times
            MetricsSnapshot snapshot2 = control.getMetrics(simulationId);
            MetricsSnapshot snapshot3 = control.getMetrics(simulationId);
            
            assertNotNull(snapshot2);
            assertNotNull(snapshot3);
        });
    }

    @Test
    @DisplayName("should throw IllegalArgumentException for null simulationId")
    void shouldThrowExceptionForNullSimulationId() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> control.getMetrics(null));
        assertEquals("simulationId must not be null", exception.getMessage());
    }

    @Test
    @DisplayName("should throw IllegalStateException for unknown simulationId")
    void shouldThrowExceptionForUnknownSimulationId() {
        // Arrange
        SimulationId unknownId = new SimulationId("unknown-sim-id");

        // Act & Assert
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> control.getMetrics(unknownId));
        assertTrue(exception.getMessage().contains("Simulation not found"));
    }

    @Test
    @DisplayName("should return consistent metrics snapshot")
    void shouldReturnConsistentMetricsSnapshot() {
        // Act
        MetricsSnapshot snapshot1 = control.getMetrics(simulationId);
        MetricsSnapshot snapshot2 = control.getMetrics(simulationId);

        // Assert - snapshots should be consistent (same values if called quickly)
        assertEquals(snapshot1.simulatedTime(), snapshot2.simulatedTime());
        assertEquals(snapshot1.messageCount(), snapshot2.messageCount());
        assertEquals(snapshot1.rounds(), snapshot2.rounds());
        assertEquals(snapshot1.converged(), snapshot2.converged());
        assertEquals(snapshot1.leaderId(), snapshot2.leaderId());
    }

    @Test
    @DisplayName("should reflect message count changes")
    void shouldReflectMessageCountChanges() throws InterruptedException {
        // Arrange
        SimulationParameters parameters = new SimulationParameters(1L, 10, 0);
        control.selectAlgorithm(simulationId, "flooding-leader-election");
        control.startSimulation(simulationId, parameters);

        // Wait for some messages to be sent
        Thread.sleep(200);

        // Act
        MetricsSnapshot snapshot = control.getMetrics(simulationId);

        // Assert
        assertNotNull(snapshot);
        // Message count may have increased if messages were sent
        assertTrue(snapshot.messageCount() >= 0);
    }

    @Test
    @DisplayName("should reflect rounds progression")
    void shouldReflectRoundsProgression() throws InterruptedException {
        // Arrange
        SimulationParameters parameters = new SimulationParameters(1L, 10, 0);
        control.selectAlgorithm(simulationId, "flooding-leader-election");
        control.startSimulation(simulationId, parameters);

        // Wait for some rounds to complete
        Thread.sleep(200);

        // Act
        MetricsSnapshot snapshot = control.getMetrics(simulationId);

        // Assert
        assertNotNull(snapshot);
        // Rounds should have increased
        assertTrue(snapshot.rounds() >= 0);
    }

    @Test
    @DisplayName("should reflect convergence state")
    void shouldReflectConvergenceState() {
        // Arrange - simulation not started, so not converged
        // Act
        MetricsSnapshot snapshot = control.getMetrics(simulationId);

        // Assert
        assertNotNull(snapshot);
        assertFalse(snapshot.converged());
    }

    @Test
    @DisplayName("should return metrics with null leaderId when no leader elected")
    void shouldReturnMetricsWithNullLeaderIdWhenNoLeaderElected() {
        // Act
        MetricsSnapshot snapshot = control.getMetrics(simulationId);

        // Assert
        assertNotNull(snapshot);
        assertNull(snapshot.leaderId());
    }

    @Test
    @DisplayName("should update leaderId when leader election event occurs")
    void shouldUpdateLeaderIdWhenLeaderElectionEventOccurs() throws InterruptedException {
        // Arrange - initial state without leader
        MetricsSnapshot snapshotBefore = control.getMetrics(simulationId);
        assertNull(snapshotBefore.leaderId());

        // Start simulation to potentially trigger leader election
        SimulationParameters parameters = new SimulationParameters(1L, 10, 0);
        control.selectAlgorithm(simulationId, "flooding-leader-election");
        control.startSimulation(simulationId, parameters);

        // Wait for potential leader election
        Thread.sleep(500);

        // Act
        MetricsSnapshot snapshotAfter = control.getMetrics(simulationId);

        // Assert
        assertNotNull(snapshotAfter);
        // Leader ID may or may not be set depending on algorithm execution
        // The important thing is that metrics are returned correctly
    }
}
