package de.haw.vsp.simulation.control;

import de.haw.vsp.simulation.core.NetworkConfig;
import de.haw.vsp.simulation.core.SimulationId;
import de.haw.vsp.simulation.engine.DefaultSimulationEngine;
import de.haw.vsp.simulation.engine.SimulationEngine;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default implementation of SimulationControl.
 *
 * Manages multiple simulation instances and provides a facade for
 * simulation control operations. Each simulation is identified by
 * a unique SimulationId.
 *
 * According to the API documentation and acceptance criteria:
 * - Simulation is initialized after call, but not started
 * - Previous simulation is cleanly discarded
 * - Invalid configurations are rejected
 */
public class DefaultSimulationControl implements SimulationControl {

    private final Map<SimulationId, SimulationEngine> simulations;
    private SimulationId currentSimulationId;

    /**
     * Creates a new simulation control instance.
     */
    public DefaultSimulationControl() {
        this.simulations = new ConcurrentHashMap<>();
        this.currentSimulationId = null;
    }

    @Override
    public SimulationId initializeNetwork(NetworkConfig config) {
        // Validate configuration
        // NetworkConfig constructor already validates nodeCount > 0 and topologyType != null
        // But we check for null config here for additional safety
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }

        // Clean up previous simulation if exists
        if (currentSimulationId != null && simulations.containsKey(currentSimulationId)) {
            SimulationEngine previousEngine = simulations.get(currentSimulationId);
            try {
                previousEngine.stopSimulation();
            } catch (Exception e) {
                // Log but don't fail - we're cleaning up anyway
                // In a production system, we'd use a proper logger here
                System.err.println("Warning: Error stopping previous simulation: " + e.getMessage());
            }
            simulations.remove(currentSimulationId);
        }

        // Create new simulation engine
        SimulationEngine engine = new DefaultSimulationEngine();

        // Create engine and nodes according to network configuration
        engine.createEngineAndNodes(config);

        // Generate new simulation ID
        SimulationId simulationId = SimulationId.generate();

        // Store the simulation
        simulations.put(simulationId, engine);
        currentSimulationId = simulationId;

        // Return the simulation ID
        return simulationId;
    }
}
