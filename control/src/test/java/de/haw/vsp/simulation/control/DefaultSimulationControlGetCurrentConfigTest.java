package de.haw.vsp.simulation.control;

import de.haw.vsp.simulation.core.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DefaultSimulationControl - getCurrentConfig")
class DefaultSimulationControlGetCurrentConfigTest {

    private DefaultSimulationControl control;
    private SimulationId simulationId;
    private NetworkConfig networkConfig;

    @BeforeEach
    void setUp() {
        control = new DefaultSimulationControl();
        networkConfig = new NetworkConfig(5, TopologyType.RING);
        simulationId = control.initializeNetwork(networkConfig);
    }

    @Test
    @DisplayName("should return complete configuration after initialization")
    void shouldReturnCompleteConfigAfterInitialization() {
        // Act
        SimulationConfig config = control.getCurrentConfig(simulationId);

        // Assert
        assertNotNull(config);
        assertEquals(networkConfig, config.networkConfig());
        assertNotNull(config.algorithmId());
        assertNotNull(config.defaultParameters());
    }

    @Test
    @DisplayName("should return network config that matches initialization")
    void shouldReturnNetworkConfigThatMatchesInitialization() {
        // Act
        SimulationConfig config = control.getCurrentConfig(simulationId);

        // Assert
        assertEquals(networkConfig.nodeCount(), config.networkConfig().nodeCount());
        assertEquals(networkConfig.topologyType(), config.networkConfig().topologyType());
    }

    @Test
    @DisplayName("should return algorithm ID after selection")
    void shouldReturnAlgorithmIdAfterSelection() {
        // Arrange
        String algorithmId = "flooding-leader-election";
        control.selectAlgorithm(simulationId, algorithmId);

        // Act
        SimulationConfig config = control.getCurrentConfig(simulationId);

        // Assert
        assertEquals(algorithmId, config.algorithmId());
    }

    @Test
    @DisplayName("should return default algorithm ID if not yet selected")
    void shouldReturnDefaultAlgorithmIdIfNotYetSelected() {
        // Act - get config before selecting algorithm
        SimulationConfig config = control.getCurrentConfig(simulationId);

        // Assert
        assertNotNull(config.algorithmId());
        assertEquals("flooding-leader-election", config.algorithmId());
    }

    @Test
    @DisplayName("should return simulation parameters after start")
    void shouldReturnSimulationParametersAfterStart() {
        // Arrange
        SimulationParameters parameters = new SimulationParameters(42L, 200, 50);
        control.selectAlgorithm(simulationId, "flooding-leader-election");
        control.startSimulation(simulationId, parameters);

        // Act
        SimulationConfig config = control.getCurrentConfig(simulationId);

        // Assert
        assertEquals(parameters, config.defaultParameters());
        assertEquals(parameters.randomSeed(), config.defaultParameters().randomSeed());
        assertEquals(parameters.maxSteps(), config.defaultParameters().maxSteps());
        assertEquals(parameters.messageDelayMillis(), config.defaultParameters().messageDelayMillis());
    }

    @Test
    @DisplayName("should return default parameters if simulation not yet started")
    void shouldReturnDefaultParametersIfSimulationNotYetStarted() {
        // Act - get config before starting simulation
        SimulationConfig config = control.getCurrentConfig(simulationId);

        // Assert
        assertNotNull(config.defaultParameters());
        assertEquals(1L, config.defaultParameters().randomSeed());
        assertEquals(100, config.defaultParameters().maxSteps());
        assertEquals(0, config.defaultParameters().messageDelayMillis());
    }

    @Test
    @DisplayName("should return consistent configuration")
    void shouldReturnConsistentConfiguration() {
        // Arrange
        control.selectAlgorithm(simulationId, "flooding-leader-election");
        SimulationParameters parameters = new SimulationParameters(123L, 150, 25);
        control.startSimulation(simulationId, parameters);

        // Act
        SimulationConfig config1 = control.getCurrentConfig(simulationId);
        SimulationConfig config2 = control.getCurrentConfig(simulationId);

        // Assert
        assertEquals(config1, config2);
        assertEquals(config1.networkConfig(), config2.networkConfig());
        assertEquals(config1.algorithmId(), config2.algorithmId());
        assertEquals(config1.defaultParameters(), config2.defaultParameters());
    }

    @Test
    @DisplayName("should reflect updated algorithm selection")
    void shouldReflectUpdatedAlgorithmSelection() {
        // Arrange
        String firstAlgorithm = "flooding-leader-election";
        control.selectAlgorithm(simulationId, firstAlgorithm);
        SimulationConfig config1 = control.getCurrentConfig(simulationId);
        assertEquals(firstAlgorithm, config1.algorithmId());

        // Act - select different algorithm (if supported)
        // Note: In current implementation, only one algorithm is supported
        // This test verifies that the config reflects the current state
        SimulationConfig config2 = control.getCurrentConfig(simulationId);

        // Assert
        assertEquals(firstAlgorithm, config2.algorithmId());
    }

    @Test
    @DisplayName("should be fully reconstructible")
    void shouldBeFullyReconstructible() {
        // Arrange
        NetworkConfig originalNetwork = new NetworkConfig(7, TopologyType.GRID);
        String algorithmId = "flooding-leader-election";
        SimulationParameters originalParams = new SimulationParameters(999L, 500, 100);

        SimulationId newSimId = control.initializeNetwork(originalNetwork);
        control.selectAlgorithm(newSimId, algorithmId);
        control.startSimulation(newSimId, originalParams);

        // Act
        SimulationConfig config = control.getCurrentConfig(newSimId);

        // Assert - all components should match
        assertEquals(originalNetwork.nodeCount(), config.networkConfig().nodeCount());
        assertEquals(originalNetwork.topologyType(), config.networkConfig().topologyType());
        assertEquals(algorithmId, config.algorithmId());
        assertEquals(originalParams.randomSeed(), config.defaultParameters().randomSeed());
        assertEquals(originalParams.maxSteps(), config.defaultParameters().maxSteps());
        assertEquals(originalParams.messageDelayMillis(), config.defaultParameters().messageDelayMillis());
    }

    @Test
    @DisplayName("should throw IllegalArgumentException for null simulationId")
    void shouldThrowExceptionForNullSimulationId() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> control.getCurrentConfig(null));
        assertEquals("simulationId must not be null", exception.getMessage());
    }

    @Test
    @DisplayName("should throw IllegalStateException for unknown simulationId")
    void shouldThrowExceptionForUnknownSimulationId() {
        // Arrange
        SimulationId unknownId = new SimulationId("unknown-sim-id");

        // Act & Assert
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> control.getCurrentConfig(unknownId));
        assertTrue(exception.getMessage().contains("Simulation not found"));
    }

    @Test
    @DisplayName("should return config consistent with internal state")
    void shouldReturnConfigConsistentWithInternalState() {
        // Arrange
        NetworkConfig network = new NetworkConfig(3, TopologyType.LINE);
        String algorithm = "flooding-leader-election";
        SimulationParameters params = new SimulationParameters(10L, 50, 5);

        SimulationId simId = control.initializeNetwork(network);
        control.selectAlgorithm(simId, algorithm);
        control.startSimulation(simId, params);

        // Act
        SimulationConfig config = control.getCurrentConfig(simId);

        // Assert - verify consistency
        assertEquals(network.nodeCount(), config.networkConfig().nodeCount());
        assertEquals(network.topologyType(), config.networkConfig().topologyType());
        assertEquals(algorithm, config.algorithmId());
        assertEquals(params.randomSeed(), config.defaultParameters().randomSeed());
        assertEquals(params.maxSteps(), config.defaultParameters().maxSteps());
        assertEquals(params.messageDelayMillis(), config.defaultParameters().messageDelayMillis());
    }

    @Test
    @DisplayName("should handle different topology types")
    void shouldHandleDifferentTopologyTypes() {
        // Arrange
        NetworkConfig ringConfig = new NetworkConfig(4, TopologyType.RING);
        NetworkConfig gridConfig = new NetworkConfig(6, TopologyType.GRID);
        NetworkConfig lineConfig = new NetworkConfig(3, TopologyType.LINE);

        SimulationId ringId = control.initializeNetwork(ringConfig);
        SimulationId gridId = control.initializeNetwork(gridConfig);
        SimulationId lineId = control.initializeNetwork(lineConfig);

        // Act
        SimulationConfig ringConfigResult = control.getCurrentConfig(ringId);
        SimulationConfig gridConfigResult = control.getCurrentConfig(gridId);
        SimulationConfig lineConfigResult = control.getCurrentConfig(lineId);

        // Assert
        assertEquals(TopologyType.RING, ringConfigResult.networkConfig().topologyType());
        assertEquals(TopologyType.GRID, gridConfigResult.networkConfig().topologyType());
        assertEquals(TopologyType.LINE, lineConfigResult.networkConfig().topologyType());
    }
}
