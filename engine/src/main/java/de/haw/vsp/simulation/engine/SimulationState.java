package de.haw.vsp.simulation.engine;

/**
 * Enumeration of simulation states.
 *
 * Represents the lifecycle states of a simulation engine.
 * State transitions follow a defined sequence to ensure consistent behavior.
 *
 * State transition diagram:
 * UNINITIALIZED -> INITIALIZED -> CONFIGURED -> RUNNING <-> PAUSED -> STOPPED
 */
public enum SimulationState {
    /**
     * Initial state. Engine is created but not initialized.
     */
    UNINITIALIZED,

    /**
     * Network and nodes are initialized but algorithm not configured.
     */
    INITIALIZED,

    /**
     * Algorithm is configured and ready to start.
     */
    CONFIGURED,

    /**
     * Simulation is actively running.
     */
    RUNNING,

    /**
     * Simulation is paused and can be resumed.
     */
    PAUSED,

    /**
     * Simulation has stopped. Terminal state.
     */
    STOPPED
}
