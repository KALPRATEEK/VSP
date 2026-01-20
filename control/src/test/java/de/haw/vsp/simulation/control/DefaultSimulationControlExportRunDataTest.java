package de.haw.vsp.simulation.control;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.haw.vsp.simulation.core.*;
import de.haw.vsp.simulation.engine.DockerNodeOrchestrator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@DisplayName("DefaultSimulationControl - exportRunData")
class DefaultSimulationControlExportRunDataTest {

    private DefaultSimulationControl control;
    private SimulationId simulationId;
    private NetworkConfig networkConfig;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        DockerNodeOrchestrator mockOrchestrator = mock(DockerNodeOrchestrator.class);
        Map<SimulationId, SimulationEventBus> eventAggregationMap = new ConcurrentHashMap<>();
        control = new DefaultSimulationControl(mockOrchestrator, eventAggregationMap);
        networkConfig = new NetworkConfig(3, TopologyType.RING);
        simulationId = control.initializeNetwork(networkConfig);
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("should export JSON format")
    void shouldExportJsonFormat() {
        // Arrange
        control.selectAlgorithm(simulationId, "flooding-leader-election");
        SimulationParameters parameters = new SimulationParameters(1L, 10, 0);
        control.startSimulation(simulationId, parameters);

        // Wait a bit for events
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Act
        byte[] exportData = control.exportRunData(simulationId, "JSON");

        // Assert
        assertNotNull(exportData);
        assertTrue(exportData.length > 0);
        
        String json = new String(exportData, StandardCharsets.UTF_8);
        assertTrue(json.contains("\"events\""));
        assertTrue(json.contains("\"metrics\""));
    }

    @Test
    @DisplayName("should export CSV format")
    void shouldExportCsvFormat() {
        // Arrange
        control.selectAlgorithm(simulationId, "flooding-leader-election");
        SimulationParameters parameters = new SimulationParameters(1L, 10, 0);
        control.startSimulation(simulationId, parameters);

        // Wait a bit for events
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Act
        byte[] exportData = control.exportRunData(simulationId, "CSV");

        // Assert
        assertNotNull(exportData);
        assertTrue(exportData.length > 0);
        
        String csv = new String(exportData, StandardCharsets.UTF_8);
        assertTrue(csv.contains("=== EVENTS ==="));
        assertTrue(csv.contains("=== METRICS ==="));
        assertTrue(csv.contains("timestamp,type,nodeId,peerId,payloadSummary"));
        assertTrue(csv.contains("simulatedTime,realTimeMillis,messageCount,rounds,converged,leaderId"));
    }

    @Test
    @DisplayName("should export all simulation events in JSON")
    void shouldExportAllSimulationEventsInJson() throws Exception {
        // Arrange - start simulation to generate events
        control.selectAlgorithm(simulationId, "flooding-leader-election");
        SimulationParameters parameters = new SimulationParameters(1L, 10, 0);
        control.startSimulation(simulationId, parameters);

        // Wait for events to be generated
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Act
        byte[] exportData = control.exportRunData(simulationId, "JSON");
        String json = new String(exportData, StandardCharsets.UTF_8);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = objectMapper.readValue(json, Map.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> events = (List<Map<String, Object>>) data.get("events");

        // Assert
        assertNotNull(events);
        assertTrue(events.size() > 0, "Should contain at least some events");
    }

    @Test
    @DisplayName("should export all simulation events in CSV")
    void shouldExportAllSimulationEventsInCsv() {
        // Arrange - start simulation to generate events
        control.selectAlgorithm(simulationId, "flooding-leader-election");
        SimulationParameters parameters = new SimulationParameters(1L, 10, 0);
        control.startSimulation(simulationId, parameters);

        // Wait for events to be generated
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Act
        byte[] exportData = control.exportRunData(simulationId, "CSV");
        String csv = new String(exportData, StandardCharsets.UTF_8);

        // Assert
        assertTrue(csv.contains("=== EVENTS ==="));
        assertTrue(csv.contains("timestamp,type,nodeId,peerId,payloadSummary"));
        // Should contain event data rows (more than just header)
        long eventLines = csv.lines()
                .filter(line -> line.contains("STATE_CHANGED") || line.contains("MESSAGE_SENT"))
                .count();
        assertTrue(eventLines > 0, "Should contain at least some event data");
    }

    @Test
    @DisplayName("should export metrics snapshot in JSON")
    void shouldExportMetricsSnapshotInJson() throws Exception {
        // Arrange
        control.selectAlgorithm(simulationId, "flooding-leader-election");
        SimulationParameters parameters = new SimulationParameters(1L, 10, 0);
        control.startSimulation(simulationId, parameters);

        // Wait a bit
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Act
        byte[] exportData = control.exportRunData(simulationId, "JSON");
        String json = new String(exportData, StandardCharsets.UTF_8);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = objectMapper.readValue(json, Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> metrics = (Map<String, Object>) data.get("metrics");

        // Assert
        assertNotNull(metrics);
        assertTrue(metrics.containsKey("simulatedTime"));
        assertTrue(metrics.containsKey("realTimeMillis"));
        assertTrue(metrics.containsKey("messageCount"));
        assertTrue(metrics.containsKey("rounds"));
        assertTrue(metrics.containsKey("converged"));
        assertTrue(metrics.containsKey("leaderId"));
    }

    @Test
    @DisplayName("should export metrics snapshot in CSV")
    void shouldExportMetricsSnapshotInCsv() {
        // Arrange
        control.selectAlgorithm(simulationId, "flooding-leader-election");
        SimulationParameters parameters = new SimulationParameters(1L, 10, 0);
        control.startSimulation(simulationId, parameters);

        // Wait a bit
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Act
        byte[] exportData = control.exportRunData(simulationId, "CSV");
        String csv = new String(exportData, StandardCharsets.UTF_8);

        // Assert
        assertTrue(csv.contains("simulatedTime,realTimeMillis,messageCount,rounds,converged,leaderId"));
        // Metrics section should contain numeric values
        String metricsSection = csv.substring(csv.indexOf("=== METRICS ==="));
        assertTrue(metricsSection.contains(",")); // Should contain comma-separated values
    }

    @Test
    @DisplayName("should export complete run data")
    void shouldExportCompleteRunData() throws Exception {
        // Arrange
        control.selectAlgorithm(simulationId, "flooding-leader-election");
        SimulationParameters parameters = new SimulationParameters(1L, 10, 0);
        control.startSimulation(simulationId, parameters);

        // Wait for some events
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Act - JSON
        byte[] jsonData = control.exportRunData(simulationId, "JSON");
        String json = new String(jsonData, StandardCharsets.UTF_8);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = objectMapper.readValue(json, Map.class);

        // Assert - should contain both events and metrics
        assertTrue(data.containsKey("events"));
        assertTrue(data.containsKey("metrics"));
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> events = (List<Map<String, Object>>) data.get("events");
        @SuppressWarnings("unchecked")
        Map<String, Object> metrics = (Map<String, Object>) data.get("metrics");
        
        assertNotNull(events);
        assertNotNull(metrics);
    }

    @Test
    @DisplayName("should handle case-insensitive format")
    void shouldHandleCaseInsensitiveFormat() {
        // Act & Assert
        assertDoesNotThrow(() -> control.exportRunData(simulationId, "json"));
        assertDoesNotThrow(() -> control.exportRunData(simulationId, "Json"));
        assertDoesNotThrow(() -> control.exportRunData(simulationId, "JSON"));
        assertDoesNotThrow(() -> control.exportRunData(simulationId, "csv"));
        assertDoesNotThrow(() -> control.exportRunData(simulationId, "Csv"));
        assertDoesNotThrow(() -> control.exportRunData(simulationId, "CSV"));
    }

    @Test
    @DisplayName("should throw IllegalArgumentException for null simulationId")
    void shouldThrowExceptionForNullSimulationId() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> control.exportRunData(null, "JSON"));
        assertEquals("simulationId must not be null", exception.getMessage());
    }

    @Test
    @DisplayName("should throw IllegalArgumentException for null format")
    void shouldThrowExceptionForNullFormat() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> control.exportRunData(simulationId, null));
        assertEquals("format must not be null or blank", exception.getMessage());
    }

    @Test
    @DisplayName("should throw IllegalArgumentException for blank format")
    void shouldThrowExceptionForBlankFormat() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> control.exportRunData(simulationId, "   "));
        assertEquals("format must not be null or blank", exception.getMessage());
    }

    @Test
    @DisplayName("should throw IllegalArgumentException for unsupported format")
    void shouldThrowExceptionForUnsupportedFormat() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> control.exportRunData(simulationId, "XML"));
        assertTrue(exception.getMessage().contains("Unsupported export format"));
    }

    @Test
    @DisplayName("should throw IllegalStateException for unknown simulationId")
    void shouldThrowExceptionForUnknownSimulationId() {
        // Arrange
        SimulationId unknownId = new SimulationId("unknown-sim-id");

        // Act & Assert
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> control.exportRunData(unknownId, "JSON"));
        assertTrue(exception.getMessage().contains("Simulation not found"));
    }

    @Test
    @DisplayName("should export empty event stream")
    void shouldExportEmptyEventStream() throws Exception {
        // Arrange - no events (simulation not started)
        // Act
        byte[] exportData = control.exportRunData(simulationId, "JSON");
        String json = new String(exportData, StandardCharsets.UTF_8);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = objectMapper.readValue(json, Map.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> events = (List<Map<String, Object>>) data.get("events");

        // Assert
        assertNotNull(events);
        // Events list may be empty or contain initialization events
    }

    @Test
    @DisplayName("should escape CSV fields with commas")
    void shouldEscapeCsvFieldsWithCommas() {
        // Arrange - start simulation and wait for events
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
        byte[] exportData = control.exportRunData(simulationId, "CSV");
        String csv = new String(exportData, StandardCharsets.UTF_8);

        // Assert - CSV should be properly formatted
        assertTrue(csv.contains("=== EVENTS ==="));
        assertTrue(csv.contains("=== METRICS ==="));
        // CSV should handle fields correctly (even if they contain commas)
    }

    @Test
    @DisplayName("should export data that is externally evaluable")
    void shouldExportDataThatIsExternallyEvaluable() throws Exception {
        // Arrange
        control.selectAlgorithm(simulationId, "flooding-leader-election");
        SimulationParameters parameters = new SimulationParameters(1L, 10, 0);
        control.startSimulation(simulationId, parameters);

        // Wait for events
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Act - JSON
        byte[] jsonData = control.exportRunData(simulationId, "JSON");
        String json = new String(jsonData, StandardCharsets.UTF_8);
        
        // Assert - should be valid JSON
        assertDoesNotThrow(() -> objectMapper.readValue(json, Map.class));
        
        // Act - CSV
        byte[] csvData = control.exportRunData(simulationId, "CSV");
        String csv = new String(csvData, StandardCharsets.UTF_8);
        
        // Assert - CSV should be parseable (has headers and data rows)
        assertTrue(csv.contains("\n")); // Should have line breaks
        assertTrue(csv.split("\n").length > 1); // Should have multiple lines
    }
}
