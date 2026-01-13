package de.haw.vsp.simulation.engine;

import de.haw.vsp.simulation.core.SimulationMessage;

/**
 * Interface for simulation nodes.
 *
 * Node represents a single node in the distributed simulation.
 * It manages the node's lifecycle and delegates algorithm-specific behavior
 * to a pluggable NodeAlgorithm instance.
 *
 * The simulation engine interacts with nodes through this interface.
 */
public interface Node {

    /**
     * Called exactly once to start the node.
     *
     * This method initializes the node and triggers the algorithm's onStart() method.
     * Must be called before any messages are delivered.
     */
    void onStart();

    /**
     * Called for each incoming message.
     *
     * The node delegates message processing to its algorithm instance.
     *
     * @param context the node context for algorithm interactions
     * @param message the received message
     */
    void onMessage(NodeContext context, SimulationMessage message);
}

