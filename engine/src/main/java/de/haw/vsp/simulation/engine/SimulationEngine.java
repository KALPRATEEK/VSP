package de.haw.vsp.simulation.engine;

import de.haw.vsp.simulation.core.MetricsSnapshot;
import de.haw.vsp.simulation.core.NetworkConfig;
import de.haw.vsp.simulation.core.NodeId;
import de.haw.vsp.simulation.core.SimulationEventPublisher;
import de.haw.vsp.simulation.core.SimulationParameters;

import java.util.Map;
import java.util.Set;

/**
 * Interface for simulation engine.
 *
 * SimulationEngine encapsulates the execution of a single simulation instance.
 * It manages nodes, algorithms, and the simulation lifecycle.
 */
public interface SimulationEngine {

    /**
     * Creates the engine and nodes according to the network configuration.
     *
     * @param config network configuration (must not be null)
     * @throws IllegalArgumentException if config is null
     */
    void createEngineAndNodes(NetworkConfig config);

    /**
     * Configures the algorithm for the simulation.
     *
     * @param algorithmId algorithm identifier (must not be null or blank)
     * @throws IllegalArgumentException if algorithmId is null or blank
     */
    void configureAlgorithm(String algorithmId);

    /**
     * Starts the simulation with the given parameters.
     *
     * @param parameters simulation parameters (must not be null)
     * @throws IllegalArgumentException if parameters is null
     * @throws IllegalStateException    if simulation is not in a valid state to start
     */
    void startSimulation(SimulationParameters parameters);

    /**
     * Pauses the simulation.
     *
     * @throws IllegalStateException if simulation is not running
     */
    void pauseSimulation();

    /**
     * Resumes the simulation.
     *
     * @throws IllegalStateException if simulation is not paused
     */
    void resumeSimulation();

    /**
     * Stops the simulation.
     */
    void stopSimulation();

    /**
     * Sets the event publisher for the simulation.
     *
     * @param eventPublisher event publisher (may be null)
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
