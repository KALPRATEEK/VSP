package de.haw.vsp.simulation.control;

import de.haw.vsp.simulation.core.NetworkConfig;
import de.haw.vsp.simulation.core.SimulationId;

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
}
