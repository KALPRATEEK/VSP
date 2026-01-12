package de.haw.vsp.simulation.engine;

import de.haw.vsp.simulation.core.SimulationMessage;

/**
 * Interface for pluggable distributed algorithms.
 *
 * NodeAlgorithm defines the lifecycle and message handling behavior of a distributed algorithm.
 * Algorithms are executed within nodes and interact with the simulation environment
 * exclusively through the provided NodeContext.
 *
 * Implementations must:
 * - Be stateful (maintain algorithm-specific state)
 * - Use only the provided NodeContext for communication
 * - Not depend on transport details or infrastructure components
 */
public interface NodeAlgorithm {

    /**
     * Called exactly once when the node starts.
     *
     * Algorithms typically use this to initialize their state and send
     * initial messages to neighbors.
     *
     * @param context the node context providing access to messaging and topology
     */
    void onStart(NodeContext context);

    /**
     * Called for each incoming message.
     *
     * Algorithms process the message and may update their state or send
     * further messages in response.
     *
     * @param context the node context providing access to messaging and topology
     * @param message the received message
     */
    void onMessage(NodeContext context, SimulationMessage message);
}

