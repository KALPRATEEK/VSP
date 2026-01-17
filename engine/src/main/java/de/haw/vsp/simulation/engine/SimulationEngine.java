package de.haw.vsp.simulation.engine;

import de.haw.vsp.simulation.core.MetricsSnapshot;
import de.haw.vsp.simulation.core.NetworkConfig;
import de.haw.vsp.simulation.core.NodeId;
import de.haw.vsp.simulation.core.SimulationEventPublisher;
import de.haw.vsp.simulation.core.SimulationParameters;

import java.util.Map;
import java.util.Set;

/**
 * Interface for simulation engine operations.
 *
 * SimulationEngine encapsulates the execution of a single simulation instance.
 * It manages nodes, algorithms, and the simulation lifecycle.
 *
 * According to the API documentation, this interface provides:
 * - Network and node creation
 * - Algorithm configuration
 * - Simulation lifecycle control (start, pause, resume, stop)
 * - Event publishing integration
 * - Metrics and topology access
 */
public interface SimulationEngine {

    /**
     * Creates the engine and nodes according to the network configuration.
     *
     * This method initializes the simulation with the specified network topology
     * but does not start the simulation. Nodes are created but remain in an
     * unstarted state until startSimulation() is called.
     *
     * @param config the network configuration (must not be null)
     * @throws IllegalArgumentException if config is null or invalid
     */
    void createEngineAndNodes(NetworkConfig config);

    /**
     * Configures the algorithm to be used by all nodes in the simulation.
     *
     * @param algorithmId the algorithm identifier (must not be null or blank)
     * @throws IllegalArgumentException if algorithmId is null or blank
     */
    void configureAlgorithm(String algorithmId);

    /**
     * Starts the simulation with the given parameters.
     *
     * Nodes will be initialized and begin executing the configured algorithm.
     *
     * @param parameters the simulation parameters (must not be null)
     * @throws IllegalArgumentException if parameters is null or invalid
     * @throws IllegalStateException if simulation is already running or not properly initialized
     */
    void startSimulation(SimulationParameters parameters);

    /**
     * Pauses the running simulation.
     *
     * The simulation state is preserved and can be resumed later.
     *
     * @throws IllegalStateException if simulation is not running
     */
    void pauseSimulation();

    /**
     * Resumes a paused simulation.
     *
     * @throws IllegalStateException if simulation is not paused
     */
    void resumeSimulation();

    /**
     * Stops the simulation.
     *
     * The simulation is terminated and cannot be resumed.
     * Nodes and engine state are cleaned up.
     */
    void stopSimulation();

    /**
     * Sets the event publisher for this simulation engine.
     *
     * Events generated during simulation execution will be published
     * through this publisher.
     *
     * @param eventPublisher the event publisher (may be null to disable event publishing)
     */
    void setEventPublisher(SimulationEventPublisher eventPublisher);

    /**
     * Gets the current metrics snapshot.
     *
     * @return current metrics snapshot
     */
    MetricsSnapshot getMetrics();

    /**
     * Gets the topology of the network.
     * Returns a map from node ID to set of neighbor node IDs.
     *
     * @return map from node ID to set of neighbor IDs (read-only)
     */
    Map<NodeId, Set<NodeId>> getTopology();

    /**
     * Gets the number of nodes in the simulation.
     *
     * @return number of nodes
     */
    int getNodeCount();
}
