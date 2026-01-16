package de.haw.vsp.simulation.core;

import java.util.List;

/**
 * Use-case façade for simulation control and observation.
 *
 * SimulationControl is the main interface between the UI and the simulation backend.
 * It provides coarse-grained capabilities for:
 * - Creating and initializing simulations
 * - Controlling simulation execution (start, pause, resume, stop)
 * - Observing simulation state (visualization, metrics, logs)
 * - Managing simulation configurations
 *
 * This interface is UI-framework-agnostic and designed around capabilities,
 * not single UI button clicks.
 */
public interface SimulationControl {

    /**
     * Creates a new simulation with the given network configuration.
     *
     * @param config network configuration (must not be null)
     * @return unique identifier for the created simulation
     * @throws IllegalArgumentException if config is null or invalid
     */
    SimulationId initializeNetwork(NetworkConfig config);

    /**
     * Selects the algorithm for a simulation.
     *
     * @param simulationId identifier of the simulation (must not be null)
     * @param algorithmId  identifier of the algorithm (must not be null or blank)
     * @throws IllegalArgumentException if simulationId or algorithmId is null/invalid
     * @throws IllegalStateException    if simulation is not in a valid state for algorithm selection
     */
    void selectAlgorithm(SimulationId simulationId, String algorithmId);

    /**
     * Starts a simulation with the given parameters.
     *
     * @param simulationId identifier of the simulation (must not be null)
     * @param parameters   simulation parameters (must not be null)
     * @throws IllegalArgumentException if simulationId or parameters is null/invalid
     * @throws IllegalStateException    if simulation is not in a valid state to start
     */
    void startSimulation(SimulationId simulationId, SimulationParameters parameters);

    /**
     * Pauses a running simulation.
     *
     * @param simulationId identifier of the simulation (must not be null)
     * @throws IllegalArgumentException if simulationId is null
     * @throws IllegalStateException    if simulation is not running
     */
    void pauseSimulation(SimulationId simulationId);

    /**
     * Resumes a paused simulation.
     *
     * @param simulationId identifier of the simulation (must not be null)
     * @throws IllegalArgumentException if simulationId is null
     * @throws IllegalStateException    if simulation is not paused
     */
    void resumeSimulation(SimulationId simulationId);

    /**
     * Stops a simulation.
     *
     * @param simulationId identifier of the simulation (must not be null)
     * @throws IllegalArgumentException if simulationId is null
     */
    void stopSimulation(SimulationId simulationId);

    /**
     * Gets the current visualization snapshot for a simulation.
     *
     * The snapshot is read-only and derived from the event stream and internal state.
     * It does not affect the simulation state and can be safely polled frequently.
     *
     * @param simulationId identifier of the simulation (must not be null)
     * @return current visualization snapshot (never null)
     * @throws IllegalArgumentException if simulationId is null
     * @throws IllegalStateException    if simulation does not exist
     */
    VisualizationSnapshot getCurrentVisualization(SimulationId simulationId);

    /**
     * Registers a visualization listener for live event updates.
     *
     * @param simulationId identifier of the simulation (must not be null)
     * @param listener     listener to receive events (must not be null)
     * @throws IllegalArgumentException if simulationId or listener is null
     */
    void registerVisualizationListener(SimulationId simulationId, VisualizationListener listener);

    /**
     * Gets the current metrics snapshot for a simulation.
     *
     * @param simulationId identifier of the simulation (must not be null)
     * @return current metrics snapshot (never null)
     * @throws IllegalArgumentException if simulationId is null
     * @throws IllegalStateException    if simulation does not exist
     */
    MetricsSnapshot getMetrics(SimulationId simulationId);

    /**
     * Gets the current configuration of a simulation.
     *
     * The returned configuration is fully reconstructible and consistent with the internal state.
     * It includes the network configuration, selected algorithm, and simulation parameters.
     *
     * @param simulationId identifier of the simulation (must not be null)
     * @return current simulation configuration (never null)
     * @throws IllegalArgumentException if simulationId is null
     * @throws IllegalStateException    if simulation does not exist
     */
    SimulationConfig getCurrentConfig(SimulationId simulationId);

    /**
     * Loads a simulation configuration and creates a new simulation instance.
     *
     * This method restores a complete simulation configuration (network, algorithm, parameters)
     * and initializes a new simulation. The behavior is equivalent to calling
     * {@link #initializeNetwork(NetworkConfig)} followed by {@link #selectAlgorithm(SimulationId, String)}.
     *
     * The simulation parameters from the config are stored but the simulation is not automatically started.
     * Use {@link #startSimulation(SimulationId, SimulationParameters)} to start the simulation.
     *
     * @param config the simulation configuration to load (must not be null)
     * @return a new unique identifier for the loaded simulation
     * @throws IllegalArgumentException if config is null or invalid
     */
    SimulationId loadConfig(SimulationConfig config);

    /**
     * Exports run data for a simulation in the specified format.
     *
     * The export includes:
     * - All simulation events from the event stream
     * - Current metrics snapshot
     *
     * Supported formats: "JSON", "CSV"
     *
     * @param simulationId identifier of the simulation (must not be null)
     * @param format       export format ("JSON" or "CSV", must not be null or blank)
     * @return byte array containing the exported data
     * @throws IllegalArgumentException if simulationId or format is null/invalid, or if format is unsupported
     * @throws IllegalStateException    if simulation does not exist
     */
    byte[] exportRunData(SimulationId simulationId, String format);

    /**
     * Returns a list of log entries for a simulation, optionally filtered.
     *
     * Logs are aggregated from the simulation event stream and formatted as human-readable strings.
     * The logs are sorted chronologically by timestamp.
     *
     * Filter options:
     * - null or empty: return all logs
     * - Event type name (e.g., "ERROR", "STATE_CHANGED"): filter by event type
     * - Node ID (e.g., "node-1"): filter by node ID
     * - Any other string: filter by substring match in payload summary
     *
     * @param simulationId identifier of the simulation (must not be null)
     * @param filter       optional filter string (may be null to return all logs)
     * @return list of log entries as strings, sorted by timestamp
     * @throws IllegalArgumentException if simulationId is null
     * @throws IllegalStateException    if simulation does not exist
     */
    List<String> getLogs(SimulationId simulationId, String filter);
}
