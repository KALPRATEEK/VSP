package de.haw.vsp.simulation.engine;

import de.haw.vsp.simulation.core.NetworkConfig;
import de.haw.vsp.simulation.core.NodeId;
import de.haw.vsp.simulation.core.SimulationEventPublisher;
import de.haw.vsp.simulation.core.SimulationParameters;
import de.haw.vsp.simulation.middleware.MessagingPort;
import de.haw.vsp.simulation.middleware.inmemory.InMemoryMessagingPort;

import java.util.*;

/**
 * Default implementation of SimulationEngine.
 *
 * This implementation creates and manages simulation nodes according to
 * a network configuration. It supports creating nodes, configuring algorithms,
 * and controlling the simulation lifecycle.
 *
 * According to the API documentation, the engine:
 * - Creates nodes and network structure according to configuration
 * - Configures or replaces the current algorithm
 * - Controls the lifecycle of the simulation (start, pause, resume, stop)
 */
public class DefaultSimulationEngine implements SimulationEngine {

    private final MessagingPort messagingPort;
    private SimulationEventPublisher eventPublisher;
    private Map<NodeId, SimulationNode> nodes;
    private String currentAlgorithmId;
    private SimulationState state;

    /**
     * Creates a new simulation engine with an in-memory messaging port.
     */
    public DefaultSimulationEngine() {
        this(new InMemoryMessagingPort());
    }

    /**
     * Creates a new simulation engine with the specified messaging port.
     *
     * @param messagingPort the messaging port for node communication (must not be null)
     * @throws IllegalArgumentException if messagingPort is null
     */
    public DefaultSimulationEngine(MessagingPort messagingPort) {
        if (messagingPort == null) {
            throw new IllegalArgumentException("messagingPort must not be null");
        }
        this.messagingPort = messagingPort;
        this.nodes = new HashMap<>();
        this.state = SimulationState.UNINITIALIZED;
    }

    @Override
    public void createEngineAndNodes(NetworkConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }

        // Clean up previous state if any
        cleanup();

        // Generate topology
        Map<NodeId, Set<NodeId>> topology = TopologyGenerator.generateTopology(config);

        // Create nodes
        Map<NodeId, SimulationNode> newNodes = new HashMap<>();
        for (Map.Entry<NodeId, Set<NodeId>> entry : topology.entrySet()) {
            NodeId nodeId = entry.getKey();
            Set<NodeId> neighbors = entry.getValue();

            // Create node context
            SimulationNodeContext nodeContext = new SimulationNodeContext(nodeId, neighbors, messagingPort);

            // Create algorithm instance (default to FloodingLeaderElectionAlgorithm for now)
            // Algorithm will be configured later via configureAlgorithm()
            NodeAlgorithm algorithm = new FloodingLeaderElectionAlgorithm();

            // Create node
            SimulationNode node = new SimulationNode(nodeId, neighbors, algorithm, nodeContext);

            // Register message handler
            final SimulationNode finalNode = node;
            messagingPort.registerHandler(
                    new de.haw.vsp.simulation.middleware.NodeId(nodeId.value()),
                    message -> {
                        // Convert middleware message to core message
                        de.haw.vsp.simulation.core.NodeId senderNodeId =
                                new de.haw.vsp.simulation.core.NodeId(message.sender().value());
                        de.haw.vsp.simulation.core.NodeId receiverNodeId =
                                new de.haw.vsp.simulation.core.NodeId(message.receiver().value());
                        de.haw.vsp.simulation.core.SimulationMessage coreMessage =
                                new de.haw.vsp.simulation.core.SimulationMessage(
                                        senderNodeId,
                                        receiverNodeId,
                                        message.type(),
                                        message.payload() != null ? message.payload().toString() : null
                                );
                        finalNode.onMessage(nodeContext, coreMessage);
                    }
            );

            newNodes.put(nodeId, node);
        }

        this.nodes = newNodes;
        this.state = SimulationState.INITIALIZED;
    }

    @Override
    public void configureAlgorithm(String algorithmId) {
        if (algorithmId == null || algorithmId.isBlank()) {
            throw new IllegalArgumentException("algorithmId must not be null or blank");
        }

        // For now, we only support FloodingLeaderElectionAlgorithm
        // In a full implementation, this would create algorithm instances based on algorithmId
        if (!"flooding-leader-election".equals(algorithmId)) {
            throw new IllegalArgumentException("Unsupported algorithm: " + algorithmId);
        }

        this.currentAlgorithmId = algorithmId;

        // Recreate nodes with the new algorithm if already created
        if (!nodes.isEmpty()) {
            // Get current topology
            Map<NodeId, Set<NodeId>> topology = new HashMap<>();
            for (SimulationNode node : nodes.values()) {
                topology.put(node.getNodeId(), node.getNeighbors());
            }

            // Recreate nodes with new algorithm
            Map<NodeId, SimulationNode> newNodes = new HashMap<>();
            for (Map.Entry<NodeId, Set<NodeId>> entry : topology.entrySet()) {
                NodeId nodeId = entry.getKey();
                Set<NodeId> neighbors = entry.getValue();

                SimulationNodeContext nodeContext = new SimulationNodeContext(nodeId, neighbors, messagingPort);
                NodeAlgorithm algorithm = new FloodingLeaderElectionAlgorithm();
                SimulationNode node = new SimulationNode(nodeId, neighbors, algorithm, nodeContext);

                newNodes.put(nodeId, node);
            }

            this.nodes = newNodes;
        }
    }

    @Override
    public void startSimulation(SimulationParameters parameters) {
        if (parameters == null) {
            throw new IllegalArgumentException("parameters must not be null");
        }
        if (state != SimulationState.INITIALIZED) {
            throw new IllegalStateException("Simulation must be initialized before starting. Current state: " + state);
        }

        // Start all nodes
        // Important: When a node's onStart() sends messages via InMemoryMessagingPort,
        // they are immediately delivered synchronously. To prevent IllegalStateException
        // when messages arrive at nodes that haven't started yet, we mark all nodes
        // as started before calling onStart() on any of them.
        // This ensures that when Node A sends a message to Node B during A's onStart(),
        // Node B can process the message even though B's onStart() hasn't been called yet.
        
        // Phase 1: Mark all nodes as started (allows them to receive messages)
        for (SimulationNode node : nodes.values()) {
            node.markAsStarted();
        }
        
        // Phase 2: Call onStart() on all nodes (they can now send and receive messages)
        for (SimulationNode node : nodes.values()) {
            node.onStart();
        }

        this.state = SimulationState.RUNNING;
    }

    @Override
    public void pauseSimulation() {
        if (state != SimulationState.RUNNING) {
            throw new IllegalStateException("Simulation must be running to pause. Current state: " + state);
        }
        this.state = SimulationState.PAUSED;
    }

    @Override
    public void resumeSimulation() {
        if (state != SimulationState.PAUSED) {
            throw new IllegalStateException("Simulation must be paused to resume. Current state: " + state);
        }
        this.state = SimulationState.RUNNING;
    }

    @Override
    public void stopSimulation() {
        cleanup();
        this.state = SimulationState.STOPPED;
    }

    @Override
    public void setEventPublisher(SimulationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    /**
     * Cleans up all nodes and resets the engine state.
     */
    private void cleanup() {
        // Unregister all handlers
        for (NodeId nodeId : nodes.keySet()) {
            messagingPort.unregisterHandler(new de.haw.vsp.simulation.middleware.NodeId(nodeId.value()));
        }

        // Clear nodes
        nodes.clear();
        this.currentAlgorithmId = null;
    }

    /**
     * Returns the current simulation state.
     *
     * @return the current state
     */
    public SimulationState getState() {
        return state;
    }

    /**
     * Returns the number of nodes in the simulation.
     *
     * @return the number of nodes
     */
    public int getNodeCount() {
        return nodes.size();
    }

    /**
     * Enumeration of simulation states.
     */
    public enum SimulationState {
        UNINITIALIZED,
        INITIALIZED,
        RUNNING,
        PAUSED,
        STOPPED
    }
}
