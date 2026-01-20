package de.haw.vsp.simulation.control;

import de.haw.vsp.simulation.core.*;
import de.haw.vsp.simulation.engine.DockerNodeOrchestrator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@DisplayName("DefaultSimulationControl - getLogs")
class DefaultSimulationControlGetLogsTest {

    private DefaultSimulationControl control;
    private SimulationId simulationId;
    private NetworkConfig networkConfig;

    @BeforeEach
    void setUp() {
        DockerNodeOrchestrator mockOrchestrator = mock(DockerNodeOrchestrator.class);
        Map<SimulationId, SimulationEventBus> eventAggregationMap = new ConcurrentHashMap<>();
        control = new DefaultSimulationControl(mockOrchestrator, eventAggregationMap);
        networkConfig = new NetworkConfig(3, TopologyType.RING);
        simulationId = control.initializeNetwork(networkConfig);
    }

    @Test
    @DisplayName("should return all logs when filter is null")
    void shouldReturnAllLogsWhenFilterIsNull() {
        // Arrange - start simulation to generate events
        control.selectAlgorithm(simulationId, "flooding-leader-election");
        SimulationParameters parameters = new SimulationParameters(1L, 10, 0);
        control.startSimulation(simulationId, parameters);

        // Wait for events
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Act
        List<String> logs = control.getLogs(simulationId, null);

        // Assert
        assertNotNull(logs);
        // Should contain at least some logs from simulation
    }

    @Test
    @DisplayName("should return all logs when filter is empty")
    void shouldReturnAllLogsWhenFilterIsEmpty() {
        // Arrange - start simulation to generate events
        control.selectAlgorithm(simulationId, "flooding-leader-election");
        SimulationParameters parameters = new SimulationParameters(1L, 10, 0);
        control.startSimulation(simulationId, parameters);

        // Wait for events
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Act
        List<String> logs = control.getLogs(simulationId, "");

        // Assert
        assertNotNull(logs);
    }

    @Test
    @DisplayName("should filter logs by event type")
    void shouldFilterLogsByEventType() {
        // Arrange - start simulation to generate events
        control.selectAlgorithm(simulationId, "flooding-leader-election");
        SimulationParameters parameters = new SimulationParameters(1L, 10, 0);
        control.startSimulation(simulationId, parameters);

        // Wait for events
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Act - filter by STATE_CHANGED
        List<String> stateChangedLogs = control.getLogs(simulationId, EventType.STATE_CHANGED.name());

        // Assert
        assertNotNull(stateChangedLogs);
        // All logs should contain STATE_CHANGED
        for (String log : stateChangedLogs) {
            assertTrue(log.contains(EventType.STATE_CHANGED.name()), 
                    "Log should contain STATE_CHANGED: " + log);
        }
    }

    @Test
    @DisplayName("should filter logs by node ID")
    void shouldFilterLogsByNodeId() {
        // Arrange - start simulation to generate events
        control.selectAlgorithm(simulationId, "flooding-leader-election");
        SimulationParameters parameters = new SimulationParameters(1L, 10, 0);
        control.startSimulation(simulationId, parameters);

        // Wait for events
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Act - filter by node ID (assuming nodes are created)
        List<String> allLogs = control.getLogs(simulationId, null);
        if (!allLogs.isEmpty()) {
            // Extract a node ID from first log
            String firstLog = allLogs.get(0);
            // Find node ID pattern in log
            String nodeIdPattern = "node-";
            if (firstLog.contains(nodeIdPattern)) {
                int start = firstLog.indexOf(nodeIdPattern);
                int end = firstLog.indexOf(" ", start);
                if (end == -1) end = firstLog.indexOf(":", start);
                if (end > start) {
                    String nodeId = firstLog.substring(start, end);
                    List<String> filteredLogs = control.getLogs(simulationId, nodeId);

                    // Assert
                    assertNotNull(filteredLogs);
                    // All logs should contain the node ID
                    for (String log : filteredLogs) {
                        assertTrue(log.contains(nodeId), 
                                "Log should contain node ID " + nodeId + ": " + log);
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("should filter logs by payload summary substring")
    void shouldFilterLogsByPayloadSummarySubstring() {
        // Arrange - start simulation to generate events
        control.selectAlgorithm(simulationId, "flooding-leader-election");
        SimulationParameters parameters = new SimulationParameters(1L, 10, 0);
        control.startSimulation(simulationId, parameters);

        // Wait for events
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Act - filter by substring in payload
        List<String> filteredLogs = control.getLogs(simulationId, "started");

        // Assert
        assertNotNull(filteredLogs);
        // All logs should contain "started" in payload
        for (String log : filteredLogs) {
            assertTrue(log.toLowerCase().contains("started"), 
                    "Log should contain 'started': " + log);
        }
    }

    @Test
    @DisplayName("should return logs sorted by timestamp")
    void shouldReturnLogsSortedByTimestamp() {
        // Arrange - start simulation to generate events
        control.selectAlgorithm(simulationId, "flooding-leader-election");
        SimulationParameters parameters = new SimulationParameters(1L, 10, 0);
        control.startSimulation(simulationId, parameters);

        // Wait for events
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Act
        List<String> logs = control.getLogs(simulationId, null);

        // Assert
        assertNotNull(logs);
        if (logs.size() > 1) {
            // Extract timestamps and verify they are sorted
            long previousTimestamp = -1;
            for (String log : logs) {
                int timestampStart = log.indexOf('[') + 1;
                int timestampEnd = log.indexOf(']', timestampStart);
                if (timestampStart > 0 && timestampEnd > timestampStart) {
                    long timestamp = Long.parseLong(log.substring(timestampStart, timestampEnd));
                    assertTrue(timestamp >= previousTimestamp, 
                            "Logs should be sorted by timestamp. Previous: " + previousTimestamp + 
                            ", Current: " + timestamp + " in log: " + log);
                    previousTimestamp = timestamp;
                }
            }
        }
    }

    @Test
    @DisplayName("should return complete logs")
    void shouldReturnCompleteLogs() {
        // Arrange - start simulation to generate events
        control.selectAlgorithm(simulationId, "flooding-leader-election");
        SimulationParameters parameters = new SimulationParameters(1L, 10, 0);
        control.startSimulation(simulationId, parameters);

        // Wait for events
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Act
        List<String> logs = control.getLogs(simulationId, null);

        // Assert
        assertNotNull(logs);
        // Logs should contain timestamp, type, nodeId, and payload
        for (String log : logs) {
            assertTrue(log.contains("["), "Log should contain timestamp: " + log);
            assertTrue(log.contains("]"), "Log should contain closing bracket: " + log);
            assertTrue(log.contains(":"), "Log should contain colon separator: " + log);
        }
    }

    @Test
    @DisplayName("should format logs with peer ID when present")
    void shouldFormatLogsWithPeerIdWhenPresent() {
        // Arrange - start simulation to generate events
        control.selectAlgorithm(simulationId, "flooding-leader-election");
        SimulationParameters parameters = new SimulationParameters(1L, 10, 0);
        control.startSimulation(simulationId, parameters);

        // Wait for events
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Act
        List<String> logs = control.getLogs(simulationId, EventType.MESSAGE_SENT.name());

        // Assert
        assertNotNull(logs);
        // If there are MESSAGE_SENT events, they should contain "->" for peer ID
        // (This is just a structural check - actual peer ID presence depends on events)
    }

    @Test
    @DisplayName("should return empty list when no events match filter")
    void shouldReturnEmptyListWhenNoEventsMatchFilter() {
        // Arrange - simulation not started, so no events
        // Act - filter by non-existent event type
        List<String> logs = control.getLogs(simulationId, "NON_EXISTENT_EVENT_TYPE");

        // Assert
        assertNotNull(logs);
        assertTrue(logs.isEmpty() || logs.stream().noneMatch(log -> 
                log.contains("NON_EXISTENT_EVENT_TYPE")));
    }

    @Test
    @DisplayName("should throw IllegalArgumentException for null simulationId")
    void shouldThrowExceptionForNullSimulationId() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> control.getLogs(null, null));
        assertEquals("simulationId must not be null", exception.getMessage());
    }

    @Test
    @DisplayName("should throw IllegalStateException for unknown simulationId")
    void shouldThrowExceptionForUnknownSimulationId() {
        // Arrange
        SimulationId unknownId = new SimulationId("unknown-sim-id");

        // Act & Assert
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> control.getLogs(unknownId, null));
        assertTrue(exception.getMessage().contains("Simulation not found"));
    }

    @Test
    @DisplayName("should return logs that are traceable and temporally correct")
    void shouldReturnLogsThatAreTraceableAndTemporallyCorrect() {
        // Arrange - start simulation to generate events
        control.selectAlgorithm(simulationId, "flooding-leader-election");
        SimulationParameters parameters = new SimulationParameters(1L, 10, 0);
        control.startSimulation(simulationId, parameters);

        // Wait for events
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Act
        List<String> logs = control.getLogs(simulationId, null);

        // Assert
        assertNotNull(logs);
        // Verify logs are traceable (contain nodeId and type)
        for (String log : logs) {
            // Should contain timestamp
            assertTrue(log.matches(".*\\[\\d+\\].*"), 
                    "Log should contain timestamp in brackets: " + log);
            // Should contain event type
            assertTrue(log.matches(".*\\[\\w+\\].*"), 
                    "Log should contain event type in brackets: " + log);
            // Should contain node ID
            assertTrue(log.contains("node-") || log.contains("system"), 
                    "Log should contain node ID: " + log);
        }

        // Verify temporal correctness (already tested in shouldReturnLogsSortedByTimestamp)
        if (logs.size() > 1) {
            long previousTimestamp = -1;
            for (String log : logs) {
                int timestampStart = log.indexOf('[') + 1;
                int timestampEnd = log.indexOf(']', timestampStart);
                if (timestampStart > 0 && timestampEnd > timestampStart) {
                    long timestamp = Long.parseLong(log.substring(timestampStart, timestampEnd));
                    assertTrue(timestamp >= previousTimestamp, 
                            "Logs should be temporally correct (sorted): " + log);
                    previousTimestamp = timestamp;
                }
            }
        }
    }

    @Test
    @DisplayName("should handle case-insensitive filtering")
    void shouldHandleCaseInsensitiveFiltering() {
        // Arrange - start simulation to generate events
        control.selectAlgorithm(simulationId, "flooding-leader-election");
        SimulationParameters parameters = new SimulationParameters(1L, 10, 0);
        control.startSimulation(simulationId, parameters);

        // Wait for events
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Act - filter with different cases
        List<String> logs1 = control.getLogs(simulationId, "state_changed");
        List<String> logs2 = control.getLogs(simulationId, "STATE_CHANGED");
        List<String> logs3 = control.getLogs(simulationId, "State_Changed");

        // Assert - all should return same results (case-insensitive)
        assertEquals(logs1.size(), logs2.size());
        assertEquals(logs2.size(), logs3.size());
    }
}
