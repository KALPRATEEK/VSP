package de.haw.vsp.simulation.engine;

import de.haw.vsp.simulation.core.NodeId;
import de.haw.vsp.simulation.core.SimulationMessage;

import java.util.Set;

/**
 * Default implementation of the Node interface.
 *
 * SimulationNode manages the lifecycle of a single node in the distributed simulation.
 * It delegates algorithm-specific behavior to a pluggable NodeAlgorithm instance
 * and provides a NodeContext for algorithm interactions.
 *
 * Lifecycle guarantees:
 * - onStart() is called exactly once before any messages are processed
 * - onMessage() is called for each incoming message
 * - The algorithm receives a consistent NodeContext for all interactions
 */
public class SimulationNode implements Node {

    private final NodeId nodeId;
    private final Set<NodeId> neighbors;
    private final NodeAlgorithm algorithm;
    private final NodeContext nodeContext;
    private boolean started;
    private boolean algorithmStarted;

    /**
     * Creates a new simulation node.
     *
     * @param nodeId    the unique identifier for this node
     * @param neighbors the set of neighboring node IDs (immutable)
     * @param algorithm the algorithm to execute on this node
     * @param nodeContext the context for algorithm interactions
     * @throws IllegalArgumentException if any parameter is null
     */
    public SimulationNode(NodeId nodeId, Set<NodeId> neighbors, NodeAlgorithm algorithm, NodeContext nodeContext) {
        if (nodeId == null) {
            throw new IllegalArgumentException("nodeId must not be null");
        }
        if (neighbors == null) {
            throw new IllegalArgumentException("neighbors must not be null");
        }
        if (algorithm == null) {
            throw new IllegalArgumentException("algorithm must not be null");
        }
        if (nodeContext == null) {
            throw new IllegalArgumentException("nodeContext must not be null");
        }

        this.nodeId = nodeId;
        this.neighbors = Set.copyOf(neighbors); // Defensive copy to ensure immutability
        this.algorithm = algorithm;
        this.nodeContext = nodeContext;
        this.started = false;
        this.algorithmStarted = false;
    }

    @Override
    public void onStart() {
        if (algorithmStarted) {
            throw new IllegalStateException(
                    "onStart() has already been called for node " + nodeId
            );
        }
        started = true;
        algorithmStarted = true;
        algorithm.onStart(nodeContext);
    }

    @Override
    public void onMessage(SimulationMessage message) {
        if (!started) {
            throw new IllegalStateException(
                    "onStart() must be called before onMessage() for node " + nodeId
            );
        }
        if (message == null) {
            throw new IllegalArgumentException("message must not be null");
        }

        algorithm.onMessage(nodeContext, message);
    }

    /**
     * Returns the node's ID.
     *
     * @return the node ID
     */
    public NodeId getNodeId() {
        return nodeId;
    }

    /**
     * Returns the set of neighbor IDs.
     *
     * @return immutable set of neighboring node IDs
     */
    public Set<NodeId> getNeighbors() {
        return neighbors;
    }

    /**
     * Returns whether the node has been started.
     *
     * @return true if onStart() has been called, false otherwise
     */
    public boolean isStarted() {
        return started;
    }

    /**
     * Marks the node as started without calling algorithm.onStart().
     *
     * This is used by the simulation engine to allow nodes to receive messages
     * before their onStart() method is called, avoiding race conditions when
     * nodes send messages during their own onStart() initialization.
     *
     * This method is package-private and should only be called by the simulation engine.
     */
    void markAsStarted() {
        started = true;
    }
}

