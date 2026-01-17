package de.haw.vsp.simulation.control;

import de.haw.vsp.simulation.core.MetricsSnapshot;
import de.haw.vsp.simulation.core.NetworkConfig;
import de.haw.vsp.simulation.core.SimulationConfig;
import de.haw.vsp.simulation.core.SimulationId;
import de.haw.vsp.simulation.core.VisualizationListener;
import de.haw.vsp.simulation.core.VisualizationSnapshot;

import java.util.List;

/**
 * Interface for simulation control operations.
 *
 * SimulationControl is the use-case façade between the UI (config, control, metrics, visualization, logs)
 * and the internal simulation core. It is UI-framework-agnostic and designed around coarse-grained capabilities,
 * not single UI button clicks.
 *
 * According to the API documentation, this interface provides:
 * - Network initialization
 * - Algorithm selection
 * - Simulation lifecycle control
 * - Visualization and metrics access
 * - Save/load/export capabilities
 * - Log access
 */
public interface SimulationControl {

    /**
     * Initializes a new simulation network.
     *
     * Creates a new simulation with the specified network topology.
     * The simulation is initialized but not started. Previous simulations
     * are cleanly discarded.
     *
     * @param config the network configuration (must not be null)
     * @return a unique SimulationId for the created simulation
     * @throws IllegalArgumentException if config is null or invalid
     */
    SimulationId initializeNetwork(NetworkConfig config);

    /**
     * Selects and configures the algorithm for a simulation.
     *
     * Configures the algorithm to be used by all nodes in the specified simulation.
     * The algorithm must be selected before starting the simulation.
     *
     * Note: Currently uses {@code String} for algorithmId. The documentation mentions
     * {@code AlgorithmId} as a type, but the implementation uses String for simplicity.
     * This may be refactored to a dedicated AlgorithmId type in the future.
     *
     * @param simulationId the simulation identifier (must not be null)
     * @param algorithmId  the algorithm identifier (must not be null or blank)
     * @throws IllegalArgumentException if simulationId is null, algorithmId is null/blank, or algorithmId is invalid
     * @throws IllegalStateException    if simulation is not found or already running
     */
    void selectAlgorithm(SimulationId simulationId, String algorithmId);

    /**
     * Starts a simulation asynchronously.
     *
     * The simulation runs in the background. The method returns immediately.
     * The simulation will automatically stop after maxSteps steps.
     *
     * @param simulationId the simulation identifier (must not be null)
     * @param parameters   the simulation parameters (must not be null)
     * @throws IllegalArgumentException if simulationId is null or parameters is null
     * @throws IllegalStateException    if simulation is not found or not in a valid state to start
     */
    void startSimulation(SimulationId simulationId, de.haw.vsp.simulation.core.SimulationParameters parameters);

    /**
     * Pauses a running simulation.
     *
     * The simulation state is preserved and can be resumed later.
     *
     * @param simulationId the simulation identifier (must not be null)
     * @throws IllegalArgumentException if simulationId is null
     * @throws IllegalStateException    if simulation is not found or not running
     */
    void pauseSimulation(SimulationId simulationId);

    /**
     * Resumes a paused simulation.
     *
     * The simulation continues from where it was paused.
     *
     * @param simulationId the simulation identifier (must not be null)
     * @throws IllegalArgumentException if simulationId is null
     * @throws IllegalStateException    if simulation is not found or not paused
     */
    void resumeSimulation(SimulationId simulationId);

    /**
     * Stops a simulation.
     *
     * The simulation is terminated deterministically and cannot be resumed.
     * Metrics are finalized and the simulation state is cleaned up.
     *
     * @param simulationId the simulation identifier (must not be null)
     * @throws IllegalArgumentException if simulationId is null
     * @throws IllegalStateException    if simulation is not found
     */
    void stopSimulation(SimulationId simulationId);

    /**
     * Gets the current visualization snapshot for a simulation.
     *
     * @param simulationId the simulation identifier (must not be null)
     * @return the current visualization snapshot
     * @throws IllegalArgumentException if simulationId is null
     * @throws IllegalStateException if simulation is not found
     */
    VisualizationSnapshot getCurrentVisualization(SimulationId simulationId);

    /**
     * Registers a visualization listener to receive events.
     *
     * @param simulationId the simulation identifier (must not be null)
     * @param listener the visualization listener (must not be null)
     * @throws IllegalArgumentException if simulationId or listener is null
     * @throws IllegalStateException if simulation is not found
     */
    void registerVisualizationListener(SimulationId simulationId, VisualizationListener listener);

    /**
     * Gets the current metrics for a simulation.
     *
     * @param simulationId the simulation identifier (must not be null)
     * @return the current metrics snapshot
     * @throws IllegalArgumentException if simulationId is null
     * @throws IllegalStateException if simulation is not found
     */
    MetricsSnapshot getMetrics(SimulationId simulationId);

    /**
     * Loads a simulation configuration.
     *
     * @param config the simulation configuration (must not be null)
     * @return the simulation identifier for the loaded configuration
     * @throws IllegalArgumentException if config is null
     */
    SimulationId loadConfig(SimulationConfig config);

    /**
     * Gets the current configuration for a simulation.
     *
     * @param simulationId the simulation identifier (must not be null)
     * @return the current simulation configuration
     * @throws IllegalArgumentException if simulationId is null
     * @throws IllegalStateException if simulation is not found
     */
    SimulationConfig getCurrentConfig(SimulationId simulationId);

    /**
     * Exports run data in the specified format.
     *
     * @param simulationId the simulation identifier (must not be null)
     * @param format the export format (JSON or CSV) (must not be null or blank)
     * @return the exported data as bytes
     * @throws IllegalArgumentException if simulationId is null, format is null/blank, or format is unsupported
     * @throws IllegalStateException if simulation is not found
     */
    byte[] exportRunData(SimulationId simulationId, String format);

    /**
     * Gets logs for a simulation with optional filtering.
     *
     * @param simulationId the simulation identifier (must not be null)
     * @param filter optional filter string (may be null for all logs)
     * @return list of log entries
     * @throws IllegalArgumentException if simulationId is null
     * @throws IllegalStateException if simulation is not found
     */
    List<String> getLogs(SimulationId simulationId, String filter);
}
