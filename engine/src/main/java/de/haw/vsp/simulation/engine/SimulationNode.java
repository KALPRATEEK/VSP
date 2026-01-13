package de.haw.vsp.simulation.engine;

import de.haw.vsp.simulation.core.NodeId;
import de.haw.vsp.simulation.core.SimulationMessage;

import java.util.ArrayDeque;
import java.util.Queue;
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
    private boolean algorithmInitialized;
    private final Queue<SimulationMessage> pendingMessages;

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
        this.algorithmInitialized = false;
        this.pendingMessages = new ArrayDeque<>();
    }

    @Override
    public void onStart() {
        if (algorithmInitialized) {
            throw new IllegalStateException(
                    "onStart() has already been called for node " + nodeId
            );
        }
        if (!started) {
            started = true;
        }
        
        // Initialize the algorithm
        algorithm.onStart(nodeContext);
        algorithmInitialized = true;
        
        // Process all pending messages that arrived before initialization
        while (!pendingMessages.isEmpty()) {
            SimulationMessage message = pendingMessages.poll();
            algorithm.onMessage(nodeContext, message);
        }
    }

    /**
     * Marks this node as started without calling the algorithm's onStart().
     * This is used during simulation initialization to allow nodes to receive
     * messages before their onStart() is called, preventing race conditions
     * when InMemoryMessagingPort delivers messages synchronously.
     * 
     * After calling this method, onStart() must still be called to initialize
     * the algorithm.
     */
    void markAsStarted() {
        if (started) {
            throw new IllegalStateException(
                    "Node " + nodeId + " is already marked as started"
            );
        }
        started = true;
    }

    @Override
    public void onMessage(NodeContext context, SimulationMessage message) {
        if (!started) {
            throw new IllegalStateException(
                    "onStart() must be called before onMessage() for node " + nodeId
            );
        }
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }
        if (message == null) {
            throw new IllegalArgumentException("message must not be null");
        }

        // If algorithm is not yet initialized, buffer the message for later processing
        if (!algorithmInitialized) {
            pendingMessages.offer(message);
            return;
        }

        // Algorithm is initialized, process message immediately
        algorithm.onMessage(context, message);
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
}

