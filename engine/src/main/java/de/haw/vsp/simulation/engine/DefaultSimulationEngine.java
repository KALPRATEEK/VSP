package de.haw.vsp.simulation.engine;

import de.haw.vsp.simulation.core.EventType;
import de.haw.vsp.simulation.core.MetricsSnapshot;
import de.haw.vsp.simulation.core.NetworkConfig;
import de.haw.vsp.simulation.core.NodeId;
import de.haw.vsp.simulation.core.SimulationEvent;
import de.haw.vsp.simulation.core.SimulationEventPublisher;
import de.haw.vsp.simulation.core.SimulationParameters;
import de.haw.vsp.simulation.middleware.MessagingPort;
import de.haw.vsp.simulation.middleware.EventPublisherAware;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger LOG = LoggerFactory.getLogger(DefaultSimulationEngine.class);
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
     * Creates a new simulation engine with a messaging port based on environment configuration.
     * 
     * The messaging port mode is determined by the MW_MODE environment variable:
     * - "virtual" (default): In-memory messaging for development and testing
     * - "udp-docker": Real UDP networking across Docker containers
     */
    public DefaultSimulationEngine() {
        this(MessagingPortFactory.create());
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

            // Create node context with message count callback
            SimulationNodeContext nodeContext = new SimulationNodeContext(
                nodeId, 
                neighbors, 
                messagingPort,
                msg -> incrementMessageCount()
            );

            // Create algorithm instance (default to FloodingLeaderElectionAlgorithm for now)
            // Algorithm will be configured later via configureAlgorithm()
            NodeAlgorithm algorithm = new FloodingLeaderElectionAlgorithm();

            // Create node
            SimulationNode node = new SimulationNode(nodeId, neighbors, algorithm, nodeContext);

            // Register message handler (middleware MessageHandler expects only SimulationMessage)
            final SimulationNode finalNode = node;
            messagingPort.registerHandler(nodeId, finalNode::onMessage);

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
                messagingPort.unregisterHandler(nodeId);
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

                SimulationNodeContext nodeContext = new SimulationNodeContext(
                    nodeId, 
                    neighbors, 
                    messagingPort,
                    msg -> incrementMessageCount()
                );
                NodeAlgorithm algorithm = new FloodingLeaderElectionAlgorithm();
                SimulationNode node = new SimulationNode(nodeId, neighbors, algorithm, nodeContext);

                // Register message handler for the new node (middleware MessageHandler expects only SimulationMessage)
                final SimulationNode finalNode = node;
                messagingPort.registerHandler(nodeId, finalNode::onMessage);

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
        // Note: Messaging is asynchronous; messages triggered by node.onStart() may arrive quickly.
        // To prevent IllegalStateException when a node receives messages before its onStart() ran,
        // we mark all nodes as started before calling onStart() on any of them.


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

        // If we are using the shared in-memory middleware, forward the publisher
        // so message events/errors can be observed via the UI.
        if (messagingPort instanceof EventPublisherAware aware) {
            aware.setEventPublisher(eventPublisher);
        }

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
     * The actual algorithm execution happens through message passing,
     * but we use this step to periodically check convergence and update metrics.
     */
    private void executeSimulationStep() {
        // The actual algorithm execution happens asynchronously through message passing.
        // This method is called periodically to:
        // 1. Check for convergence
        // 2. Update leader information
        // 3. Publish events
        
        // Check convergence every 10 steps to avoid excessive computation
        if (currentStep.get() % 10 == 0) {
            checkConvergence();
        }
    }
    
    /**
     * Checks if the algorithm has converged and updates metrics accordingly.
     * Publishes a LEADER_ELECTED event if a leader is detected for the first time.
     */
    private void checkConvergence() {
        if (nodes.isEmpty()) {
            return;
        }
        
        // Collect leader IDs from all nodes
        Set<String> leaderIds = new HashSet<>();
        for (SimulationNode node : nodes.values()) {
            NodeAlgorithm algorithm = getNodeAlgorithm(node);
            if (algorithm instanceof FloodingLeaderElectionAlgorithm) {
                FloodingLeaderElectionAlgorithm floodingAlgorithm = (FloodingLeaderElectionAlgorithm) algorithm;
                NodeId currentLeader = floodingAlgorithm.getCurrentLeaderId();
                if (currentLeader != null) {
                    leaderIds.add(currentLeader.value());
                }
            }
        }
        
        // Check for convergence: all nodes must agree on the same leader
        if (leaderIds.size() == 1) {
            String newLeaderId = leaderIds.iterator().next();
            
            // SC6: Validate that the elected leader has the maximum NodeId
            String maxNodeId = nodes.keySet().stream()
                .map(NodeId::value)
                .max(String::compareTo)
                .orElse(null);
            
            if (maxNodeId != null && !newLeaderId.equals(maxNodeId)) {
                // WARNING: Elected leader is NOT the node with maximum ID!
                String warningMsg = String.format(
                    "⚠️ SC6 VIOLATION: Elected leader '%s' is NOT the maximum NodeId! Expected: '%s'",
                    newLeaderId, maxNodeId
                );
                LOG.warn(warningMsg);
                publishEvent(SimulationEvent.withoutPeer(
                        System.currentTimeMillis(),
                        EventType.STATE_CHANGED,
                        SYSTEM_NODE_ID,
                        warningMsg
                ));
            }
            
            // Check if this is a new leader (first detection)
            boolean isNewLeader = !converged.get() || !newLeaderId.equals(this.leaderId);
            
            // Update convergence state
            this.leaderId = newLeaderId;
            this.converged.set(true);
            
            // Publish event only on first leader detection
            if (isNewLeader) {
                publishEvent(SimulationEvent.withoutPeer(
                        System.currentTimeMillis(),
                        EventType.LEADER_ELECTED,
                        this.leaderId,
                        "Leader elected: " + this.leaderId
                ));
            }
        } else if (!leaderIds.isEmpty() && leaderIds.size() > 1) {
            // Multiple different leaders - not yet converged
            // Reset convergence if we were previously converged
            if (converged.get()) {
                this.converged.set(false);
                this.leaderId = null;
            }
        }
    }
    
    /**
     * Finalizes metrics before stopping the simulation.
     * Determines the elected leader and checks for convergence.
     */
    private void finalizeMetrics() {
        if (nodes.isEmpty()) {
            converged.set(false);
            leaderId = null;
            return;
        }
        
        // Collect leader IDs from all nodes
        Set<String> leaderIds = new HashSet<>();
        for (SimulationNode node : nodes.values()) {
            NodeAlgorithm algorithm = getNodeAlgorithm(node);
            if (algorithm instanceof FloodingLeaderElectionAlgorithm) {
                FloodingLeaderElectionAlgorithm floodingAlgorithm = (FloodingLeaderElectionAlgorithm) algorithm;
                NodeId currentLeader = floodingAlgorithm.getCurrentLeaderId();
                if (currentLeader != null) {
                    leaderIds.add(currentLeader.value());
                }
            }
        }
        
        // Check for convergence: all nodes must agree on the same leader
        if (leaderIds.size() == 1) {
            // All nodes agree on one leader - converged
            this.leaderId = leaderIds.iterator().next();
            this.converged.set(true);
            
            // Publish leader elected event
            publishEvent(SimulationEvent.withoutPeer(
                    System.currentTimeMillis(),
                    EventType.LEADER_ELECTED,
                    this.leaderId,
                    "Leader elected: " + this.leaderId
            ));
        } else if (leaderIds.isEmpty()) {
            // No nodes have elected a leader yet
            this.leaderId = null;
            this.converged.set(false);
        } else {
            // Multiple different leaders - not yet converged
            this.leaderId = null;
            this.converged.set(false);
        }
    }
    
    /**
     * Extracts the NodeAlgorithm from a SimulationNode using reflection.
     * This is needed because SimulationNode doesn't expose the algorithm directly.
     *
     * @param node the simulation node
     * @return the node's algorithm, or null if extraction fails
     */
    private NodeAlgorithm getNodeAlgorithm(SimulationNode node) {
        try {
            java.lang.reflect.Field algorithmField = SimulationNode.class.getDeclaredField("algorithm");
            algorithmField.setAccessible(true);
            return (NodeAlgorithm) algorithmField.get(node);
        } catch (Exception e) {
            // If reflection fails, return null
            return null;
        }
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
            messagingPort.unregisterHandler(nodeId);
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


