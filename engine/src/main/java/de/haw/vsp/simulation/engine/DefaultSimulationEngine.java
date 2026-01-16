package de.haw.vsp.simulation.engine;

import de.haw.vsp.simulation.core.EventType;
import de.haw.vsp.simulation.core.MetricsSnapshot;
import de.haw.vsp.simulation.core.NetworkConfig;
import de.haw.vsp.simulation.core.NodeId;
import de.haw.vsp.simulation.core.SimulationEvent;
import de.haw.vsp.simulation.core.SimulationEventPublisher;
import de.haw.vsp.simulation.core.SimulationParameters;
import de.haw.vsp.simulation.middleware.MessagingPort;
import de.haw.vsp.simulation.middleware.inmemory.InMemoryMessagingPort;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

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

    private static final String SYSTEM_NODE_ID = "system";

    private final MessagingPort messagingPort;
    private SimulationEventPublisher eventPublisher;
    private Map<NodeId, SimulationNode> nodes;
    private String currentAlgorithmId;
    private volatile SimulationState state;
    private SimulationParameters simulationParameters;
    
    // Metrics tracking
    private final AtomicLong simulatedTime;
    private final AtomicLong messageCount;
    private final AtomicLong rounds;
    private final AtomicBoolean converged;
    private String leaderId;
    private long startTimeMillis;
    
    // Simulation loop control
    private Thread simulationThread;
    private final AtomicBoolean shouldStop;
    private final AtomicLong currentStep;

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
        this.currentAlgorithmId = null;
        this.simulatedTime = new AtomicLong(0);
        this.messageCount = new AtomicLong(0);
        this.rounds = new AtomicLong(0);
        this.converged = new AtomicBoolean(false);
        this.leaderId = null;
        this.startTimeMillis = 0;
        this.shouldStop = new AtomicBoolean(false);
        this.currentStep = new AtomicLong(0);
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
            // Unregister old handlers first
            for (NodeId nodeId : nodes.keySet()) {
                messagingPort.unregisterHandler(new de.haw.vsp.simulation.middleware.NodeId(nodeId.value()));
            }
            
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

                // Register message handler for the new node
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
        if (currentAlgorithmId == null || currentAlgorithmId.isBlank()) {
            throw new IllegalStateException("Algorithm must be configured before starting simulation. Call configureAlgorithm() first.");
        }

        this.simulationParameters = parameters;
        
        // Reset metrics and start time
        this.simulatedTime.set(0);
        this.messageCount.set(0);
        this.rounds.set(0);
        this.currentStep.set(0);
        this.converged.set(false);
        this.leaderId = null;
        this.shouldStop.set(false);
        this.startTimeMillis = System.currentTimeMillis();

        // Publish start event
        publishEvent(SimulationEvent.withoutPeer(
                System.currentTimeMillis(),
                EventType.STATE_CHANGED,
                SYSTEM_NODE_ID,
                "Simulation started with " + nodes.size() + " nodes, maxSteps=" + parameters.maxSteps()
        ));

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
            // Publish node start event
            publishEvent(SimulationEvent.withoutPeer(
                    System.currentTimeMillis(),
                    EventType.STATE_CHANGED,
                    node.getNodeId().value(),
                    "Node started"
            ));
        }

        this.state = SimulationState.RUNNING;
        
        // Start simulation loop in background thread
        startSimulationLoop();
    }

    @Override
    public void pauseSimulation() {
        if (state != SimulationState.RUNNING) {
            throw new IllegalStateException("Simulation must be running to pause. Current state: " + state);
        }
        this.state = SimulationState.PAUSED;
        
        // Publish pause event
        publishEvent(SimulationEvent.withoutPeer(
                System.currentTimeMillis(),
                EventType.STATE_CHANGED,
                SYSTEM_NODE_ID,
                "Simulation paused"
        ));
    }

    @Override
    public void resumeSimulation() {
        if (state != SimulationState.PAUSED) {
            throw new IllegalStateException("Simulation must be paused to resume. Current state: " + state);
        }
        this.state = SimulationState.RUNNING;
        
        // Publish resume event
        publishEvent(SimulationEvent.withoutPeer(
                System.currentTimeMillis(),
                EventType.STATE_CHANGED,
                SYSTEM_NODE_ID,
                "Simulation resumed"
        ));
        
        // Resume simulation loop
        startSimulationLoop();
    }

    @Override
    public void stopSimulation() {
        // Signal stop
        shouldStop.set(true);
        this.state = SimulationState.STOPPED;
        
        // Wait for simulation thread to finish if running
        if (simulationThread != null && simulationThread.isAlive()) {
            // Interrupt the thread to ensure it stops promptly
            simulationThread.interrupt();
            
            try {
                // Wait for thread to terminate, with timeout
                simulationThread.join(5000); // Wait up to 5 seconds
                
                // If thread is still alive after timeout, proceed anyway
                // The thread is a daemon thread, so it won't prevent JVM shutdown
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                // If interrupted, still try to proceed with cleanup
            }
        }
        
        // Finalize metrics (safe to call even if thread is still running,
        // as it only accesses AtomicLong/AtomicBoolean which are thread-safe)
        finalizeMetrics();
        
        // Publish stop event
        publishEvent(SimulationEvent.withoutPeer(
                System.currentTimeMillis(),
                EventType.STATE_CHANGED,
                SYSTEM_NODE_ID,
                "Simulation stopped after " + rounds.get() + " rounds"
        ));
        
        // Cleanup: unregister handlers and clear nodes
        // This is safe because shouldStop is set and state is STOPPED,
        // so the simulation loop should exit soon if still running
        cleanup();
    }

    @Override
    public void setEventPublisher(SimulationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    /**
     * Starts the simulation loop in a background thread.
     * The loop runs until maxSteps is reached or stopSimulation() is called.
     */
    private void startSimulationLoop() {
        if (simulationThread != null && simulationThread.isAlive()) {
            // Loop already running
            return;
        }
        
        simulationThread = new Thread(() -> {
            boolean shouldContinue = true;
            while (shouldContinue && !shouldStop.get() && currentStep.get() < simulationParameters.maxSteps()) {
                shouldContinue = processSimulationCycle();
            }

            // Simulation finished (either maxSteps reached or stopped)
            handleSimulationCompletion();
        });

        simulationThread.setDaemon(true);
        simulationThread.start();
    }

    /**
     * Processes one simulation cycle based on current state.
     *
     * @return true if simulation should continue, false otherwise
     */
    private boolean processSimulationCycle() {
        if (state == SimulationState.PAUSED) {
            return handlePausedState();
        } else if (state == SimulationState.RUNNING) {
            return handleRunningState();
        } else {
            // State is neither PAUSED nor RUNNING, exit loop
            return false;
        }
    }

    /**
     * Handles simulation behavior when paused.
     *
     * @return true if simulation should continue, false if interrupted
     */
    private boolean handlePausedState() {
        try {
            Thread.sleep(100); // Wait while paused
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Handles simulation behavior when running.
     *
     * @return true if simulation should continue, false if interrupted
     */
    private boolean handleRunningState() {
        // Execute one simulation step
        executeSimulationStep();

        currentStep.incrementAndGet();
        rounds.incrementAndGet();
        simulatedTime.incrementAndGet();

        // Small delay to prevent CPU spinning and allow tests to check state
        try {
            Thread.sleep(Math.max(1, simulationParameters.messageDelayMillis()));
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Handles simulation completion (maxSteps reached or stopped).
     */
    private void handleSimulationCompletion() {
        if (currentStep.get() >= simulationParameters.maxSteps()) {
            // Max steps reached
            publishEvent(SimulationEvent.withoutPeer(
                    System.currentTimeMillis(),
                    EventType.STATE_CHANGED,
                    SYSTEM_NODE_ID,
                    "Simulation reached maxSteps: " + simulationParameters.maxSteps()
            ));
            shouldStop.set(true);
            state = SimulationState.STOPPED;
        }
    }
    
    /**
     * Executes one simulation step.
     * In a real implementation, this would trigger algorithm execution on all nodes.
     * For now, this is a placeholder that allows the simulation to progress.
     */
    private void executeSimulationStep() {
        // In a real implementation, this would:
        // 1. Trigger algorithm execution on all nodes
        // 2. Process pending messages
        // 3. Update metrics
        // For now, we just increment the step counter
        // The actual algorithm execution happens through message passing
    }
    
    /**
     * Finalizes metrics before stopping the simulation.
     */
    private void finalizeMetrics() {
        // Determine leader (simplified - in real implementation, query algorithm state)
        // For now, we'll leave leaderId as null or set it based on algorithm state
        // This should be implemented by querying the algorithm for the elected leader
        
        // Check convergence (simplified)
        // In a real implementation, this would check if all nodes have converged
        converged.set(true); // Simplified - assume converged when stopped
    }
    
    /**
     * Publishes a simulation event if an event publisher is set.
     */
    private void publishEvent(SimulationEvent event) {
        if (eventPublisher != null) {
            try {
                eventPublisher.publish(event);
            } catch (Exception e) {
                // Silently ignore - event publishing should not break simulation
                // In production, this would use proper logging
            }
        }
    }
    
    /**
     * Gets the current metrics snapshot.
     *
     * @return current metrics snapshot
     */
    public MetricsSnapshot getMetrics() {
        long realTimeMillis = System.currentTimeMillis() - startTimeMillis;
        return new MetricsSnapshot(
                simulatedTime.get(),
                realTimeMillis,
                messageCount.get(),
                rounds.get(),
                converged.get(),
                leaderId
        );
    }
    
    /**
     * Increments the message count (called when a message is sent).
     */
    public void incrementMessageCount() {
        messageCount.incrementAndGet();
    }
    
    /**
     * Sets the leader ID (called by algorithm when leader is elected).
     *
     * @param leaderId the leader ID
     */
    public void setLeaderId(String leaderId) {
        this.leaderId = leaderId;
    }
    
    /**
     * Sets the convergence state (called by algorithm when converged).
     *
     * @param converged true if converged, false otherwise
     */
    public void setConverged(boolean converged) {
        this.converged.set(converged);
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

    @Override
    public Map<NodeId, Set<NodeId>> getTopology() {
        Map<NodeId, Set<NodeId>> topology = new HashMap<>();
        for (SimulationNode node : nodes.values()) {
            topology.put(node.getNodeId(), node.getNeighbors());
        }
        return Collections.unmodifiableMap(topology);
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

