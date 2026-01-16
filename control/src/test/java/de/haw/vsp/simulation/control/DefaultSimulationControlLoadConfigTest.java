package de.haw.vsp.simulation.control;

import de.haw.vsp.simulation.core.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DefaultSimulationControl - loadConfig")
class DefaultSimulationControlLoadConfigTest {

    private DefaultSimulationControl control;
    private SimulationConfig testConfig;

    @BeforeEach
    void setUp() {
        control = new DefaultSimulationControl();
        NetworkConfig networkConfig = new NetworkConfig(5, TopologyType.RING);
        SimulationParameters parameters = new SimulationParameters(42L, 200, 50);
        testConfig = new SimulationConfig(networkConfig, "flooding-leader-election", parameters);
    }

    @Test
    @DisplayName("should create new simulation from config")
    void shouldCreateNewSimulationFromConfig() {
        // Act
        SimulationId simulationId = control.loadConfig(testConfig);

        // Assert
        assertNotNull(simulationId);
        // Verify simulation exists
        assertDoesNotThrow(() -> control.getCurrentVisualization(simulationId));
    }

    @Test
    @DisplayName("should restore network configuration")
    void shouldRestoreNetworkConfiguration() {
        // Act
        SimulationId simulationId = control.loadConfig(testConfig);

        // Assert
        SimulationConfig loadedConfig = control.getCurrentConfig(simulationId);
        assertEquals(testConfig.networkConfig().nodeCount(), loadedConfig.networkConfig().nodeCount());
        assertEquals(testConfig.networkConfig().topologyType(), loadedConfig.networkConfig().topologyType());
    }

    @Test
    @DisplayName("should restore algorithm selection")
    void shouldRestoreAlgorithmSelection() {
        // Act
        SimulationId simulationId = control.loadConfig(testConfig);

        // Assert
        SimulationConfig loadedConfig = control.getCurrentConfig(simulationId);
        assertEquals(testConfig.algorithmId(), loadedConfig.algorithmId());
    }

    @Test
    @DisplayName("should store simulation parameters")
    void shouldStoreSimulationParameters() {
        // Act
        SimulationId simulationId = control.loadConfig(testConfig);

        // Assert
        SimulationConfig loadedConfig = control.getCurrentConfig(simulationId);
        assertEquals(testConfig.defaultParameters(), loadedConfig.defaultParameters());
        assertEquals(testConfig.defaultParameters().randomSeed(), loadedConfig.defaultParameters().randomSeed());
        assertEquals(testConfig.defaultParameters().maxSteps(), loadedConfig.defaultParameters().maxSteps());
        assertEquals(testConfig.defaultParameters().messageDelayMillis(), loadedConfig.defaultParameters().messageDelayMillis());
    }

    @Test
    @DisplayName("should behave like initializeNetwork + selectAlgorithm")
    void shouldBehaveLikeInitializeNetworkAndSelectAlgorithm() {
        // Arrange
        NetworkConfig networkConfig = new NetworkConfig(3, TopologyType.GRID);
        String algorithmId = "flooding-leader-election";
        SimulationParameters parameters = new SimulationParameters(123L, 150, 25);
        SimulationConfig config = new SimulationConfig(networkConfig, algorithmId, parameters);

        // Act - load config
        SimulationId loadedId = control.loadConfig(config);

        // Act - equivalent manual steps
        SimulationId manualId = control.initializeNetwork(networkConfig);
        control.selectAlgorithm(manualId, algorithmId);
        // Parameters are stored when startSimulation is called, but for comparison
        // we can just verify the config matches after loadConfig

        // Assert - both should result in equivalent configurations
        SimulationConfig loadedConfig = control.getCurrentConfig(loadedId);
        SimulationConfig manualConfig = control.getCurrentConfig(manualId);

        assertEquals(loadedConfig.networkConfig(), manualConfig.networkConfig());
        assertEquals(loadedConfig.algorithmId(), manualConfig.algorithmId());
        assertEquals(loadedConfig.defaultParameters(), manualConfig.defaultParameters());
    }

    @Test
    @DisplayName("should not start simulation automatically")
    void shouldNotStartSimulationAutomatically() {
        // Act
        SimulationId simulationId = control.loadConfig(testConfig);

        // Assert - simulation should be in initialized state, not running
        MetricsSnapshot metrics = control.getMetrics(simulationId);
        assertEquals(0L, metrics.rounds());
        assertEquals(0L, metrics.messageCount());
    }

    @Test
    @DisplayName("should allow starting simulation after loading")
    void shouldAllowStartingSimulationAfterLoading() {
        // Arrange
        SimulationId simulationId = control.loadConfig(testConfig);

        // Act
        control.startSimulation(simulationId, testConfig.defaultParameters());

        // Assert - simulation should be running
        // Wait a bit and check metrics
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        MetricsSnapshot metrics = control.getMetrics(simulationId);
        assertTrue(metrics.realTimeMillis() >= 0);
    }

    @Test
    @DisplayName("should create new simulation for each load")
    void shouldCreateNewSimulationForEachLoad() {
        // Act
        SimulationId id1 = control.loadConfig(testConfig);
        SimulationId id2 = control.loadConfig(testConfig);

        // Assert - should be different IDs
        assertNotEquals(id1, id2);
        
        // Both should exist and be independent
        assertDoesNotThrow(() -> control.getCurrentConfig(id1));
        assertDoesNotThrow(() -> control.getCurrentConfig(id2));
    }

    @Test
    @DisplayName("should throw IllegalArgumentException for null config")
    void shouldThrowExceptionForNullConfig() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> control.loadConfig(null));
        assertEquals("config must not be null", exception.getMessage());
    }

    @Test
    @DisplayName("should handle different topology types")
    void shouldHandleDifferentTopologyTypes() {
        // Arrange
        NetworkConfig ringConfig = new NetworkConfig(4, TopologyType.RING);
        NetworkConfig gridConfig = new NetworkConfig(6, TopologyType.GRID);
        NetworkConfig lineConfig = new NetworkConfig(3, TopologyType.LINE);

        SimulationParameters params = new SimulationParameters(1L, 100, 0);
        SimulationConfig ringSimConfig = new SimulationConfig(ringConfig, "flooding-leader-election", params);
        SimulationConfig gridSimConfig = new SimulationConfig(gridConfig, "flooding-leader-election", params);
        SimulationConfig lineSimConfig = new SimulationConfig(lineConfig, "flooding-leader-election", params);

        // Act
        SimulationId ringId = control.loadConfig(ringSimConfig);
        SimulationId gridId = control.loadConfig(gridSimConfig);
        SimulationId lineId = control.loadConfig(lineSimConfig);

        // Assert
        assertEquals(TopologyType.RING, control.getCurrentConfig(ringId).networkConfig().topologyType());
        assertEquals(TopologyType.GRID, control.getCurrentConfig(gridId).networkConfig().topologyType());
        assertEquals(TopologyType.LINE, control.getCurrentConfig(lineId).networkConfig().topologyType());
    }

    @Test
    @DisplayName("should handle different algorithm IDs")
    void shouldHandleDifferentAlgorithmIds() {
        // Arrange
        NetworkConfig networkConfig = new NetworkConfig(3, TopologyType.RING);
        SimulationParameters params = new SimulationParameters(1L, 100, 0);
        String algorithmId = "flooding-leader-election";
        SimulationConfig config = new SimulationConfig(networkConfig, algorithmId, params);

        // Act
        SimulationId simulationId = control.loadConfig(config);

        // Assert
        SimulationConfig loadedConfig = control.getCurrentConfig(simulationId);
        assertEquals(algorithmId, loadedConfig.algorithmId());
    }

    @Test
    @DisplayName("should handle different simulation parameters")
    void shouldHandleDifferentSimulationParameters() {
        // Arrange
        NetworkConfig networkConfig = new NetworkConfig(3, TopologyType.RING);
        SimulationParameters params1 = new SimulationParameters(999L, 500, 100);
        SimulationParameters params2 = new SimulationParameters(1L, 50, 0);
        
        SimulationConfig config1 = new SimulationConfig(networkConfig, "flooding-leader-election", params1);
        SimulationConfig config2 = new SimulationConfig(networkConfig, "flooding-leader-election", params2);

        // Act
        SimulationId id1 = control.loadConfig(config1);
        SimulationId id2 = control.loadConfig(config2);

        // Assert
        SimulationConfig loadedConfig1 = control.getCurrentConfig(id1);
        SimulationConfig loadedConfig2 = control.getCurrentConfig(id2);
        
        assertEquals(params1, loadedConfig1.defaultParameters());
        assertEquals(params2, loadedConfig2.defaultParameters());
    }

    @Test
    @DisplayName("should create independent simulations")
    void shouldCreateIndependentSimulations() {
        // Arrange
        NetworkConfig networkConfig1 = new NetworkConfig(3, TopologyType.RING);
        NetworkConfig networkConfig2 = new NetworkConfig(5, TopologyType.GRID);
        SimulationParameters params = new SimulationParameters(1L, 100, 0);
        
        SimulationConfig config1 = new SimulationConfig(networkConfig1, "flooding-leader-election", params);
        SimulationConfig config2 = new SimulationConfig(networkConfig2, "flooding-leader-election", params);

        // Act
        SimulationId id1 = control.loadConfig(config1);
        SimulationId id2 = control.loadConfig(config2);

        // Assert - both should exist independently
        SimulationConfig loadedConfig1 = control.getCurrentConfig(id1);
        SimulationConfig loadedConfig2 = control.getCurrentConfig(id2);
        
        assertNotEquals(loadedConfig1.networkConfig().nodeCount(), loadedConfig2.networkConfig().nodeCount());
        assertNotEquals(loadedConfig1.networkConfig().topologyType(), loadedConfig2.networkConfig().topologyType());
    }

    @Test
    @DisplayName("should restore complete configuration")
    void shouldRestoreCompleteConfiguration() {
        // Arrange
        NetworkConfig networkConfig = new NetworkConfig(7, TopologyType.GRID);
        String algorithmId = "flooding-leader-election";
        SimulationParameters parameters = new SimulationParameters(777L, 300, 75);
        SimulationConfig originalConfig = new SimulationConfig(networkConfig, algorithmId, parameters);

        // Act
        SimulationId simulationId = control.loadConfig(originalConfig);
        SimulationConfig restoredConfig = control.getCurrentConfig(simulationId);

        // Assert - all components should match
        assertEquals(originalConfig.networkConfig().nodeCount(), restoredConfig.networkConfig().nodeCount());
        assertEquals(originalConfig.networkConfig().topologyType(), restoredConfig.networkConfig().topologyType());
        assertEquals(originalConfig.algorithmId(), restoredConfig.algorithmId());
        assertEquals(originalConfig.defaultParameters().randomSeed(), restoredConfig.defaultParameters().randomSeed());
        assertEquals(originalConfig.defaultParameters().maxSteps(), restoredConfig.defaultParameters().maxSteps());
        assertEquals(originalConfig.defaultParameters().messageDelayMillis(), restoredConfig.defaultParameters().messageDelayMillis());
    }
}
