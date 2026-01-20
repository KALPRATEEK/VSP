package de.haw.vsp.simulation.engine;

import de.haw.vsp.simulation.core.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Distributed implementation of SimulationEngine.
 *
 * Conforms to section 8.10 "Docker-Based Node Isolation Concept":
 * "Each logical node maps to one Docker container."
 *
 * This engine orchestrates simulation execution across distributed Docker containers.
 * Instead of creating nodes locally (like DefaultSimulationEngine), it:
 * - Uses DockerNodeOrchestrator to create/manage containers
 * - Uses DistributedNodeClient to control remote nodes via HTTP
 * - Uses DistributedMetricsAggregator to collect metrics from events
 *
 * Conforms to section 8.1 "Communication Concept":
 * - Asynchronous communication (no blocking RPC)
 * - Best-effort delivery
 * - Nodes are peers (Backend is orchestrator, not broker)
 *
 * Thread-safety: This class is NOT thread-safe. External synchronization required.
 */
public class DistributedSimulationEngine implements SimulationEngine {

    private static final Logger LOG = LoggerFactory.getLogger(DistributedSimulationEngine.class);

    private final DockerNodeOrchestrator orchestrator;
    private final SimulationEventBus eventBus;
    private final DistributedMetricsAggregator metricsAggregator;

    private final Map<NodeId, DistributedNodeClient> nodeClients;
    private Map<NodeId, String> containerIds;
    private Map<NodeId, Set<NodeId>> topology;

    private SimulationId simulationId;
    private NetworkConfig networkConfig;
    private String currentAlgorithmId;
    private SimulationState state;
    private SimulationEventPublisher eventPublisher;

    /**
     * Creates a new distributed simulation engine.
     *
     * @param orchestrator Docker node orchestrator for container management
     * @param eventBus event bus for event aggregation
     * @throws IllegalArgumentException if orchestrator or eventBus is null
     */
    public DistributedSimulationEngine(DockerNodeOrchestrator orchestrator, SimulationEventBus eventBus) {
        if (orchestrator == null) {
            throw new IllegalArgumentException("orchestrator must not be null");
        }
        if (eventBus == null) {
            throw new IllegalArgumentException("eventBus must not be null");
        }

        this.orchestrator = orchestrator;
        this.eventBus = eventBus;
        this.metricsAggregator = new DistributedMetricsAggregator();
        this.nodeClients = new HashMap<>();
        this.state = SimulationState.UNINITIALIZED;

        LOG.info("DistributedSimulationEngine created");
    }

    @Override
    public void createEngineAndNodes(NetworkConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        if (state != SimulationState.UNINITIALIZED) {
            throw new IllegalStateException("Engine already initialized. Current state: " + state);
        }

        LOG.info("Creating distributed simulation with {} nodes", config.nodeCount());

        this.networkConfig = config;
        this.simulationId = SimulationId.generate();
        this.topology = TopologyGenerator.generateTopology(config);

        // Subscribe metrics aggregator to event bus
        eventBus.subscribe(EventType.METRICS_UPDATE, metricsAggregator);
        eventBus.subscribe(EventType.MESSAGE_SENT, metricsAggregator);
        eventBus.subscribe(EventType.LEADER_ELECTED, metricsAggregator);

        state = SimulationState.INITIALIZED;

        LOG.info("Distributed simulation engine initialized for {} nodes (SimulationId: {})",
                 config.nodeCount(), simulationId);
    }

    @Override
    public void configureAlgorithm(String algorithmId) {
        if (algorithmId == null || algorithmId.isBlank()) {
            throw new IllegalArgumentException("algorithmId must not be null or blank");
        }
        if (state != SimulationState.INITIALIZED) {
            throw new IllegalStateException("Engine not initialized. Current state: " + state);
        }

        LOG.info("Configuring algorithm '{}' for distributed simulation", algorithmId);

        this.currentAlgorithmId = algorithmId;

        try {
            // Deploy Docker containers with pre-configured algorithm
            this.containerIds = orchestrator.deployNodes(networkConfig, simulationId);

            // Wait for containers to be ready (both Docker container and Spring Boot application)
            // Increased timeout to 60s to allow for Spring Boot startup time
            boolean ready = orchestrator.waitForNodesReady(containerIds, Duration.ofSeconds(60));
            if (!ready) {
                throw new RuntimeException("Timeout waiting for node containers to be ready");
            }

            // Create HTTP clients and configure each node
            for (Map.Entry<NodeId, Set<NodeId>> entry : topology.entrySet()) {
                NodeId nodeId = entry.getKey();
                Set<NodeId> neighbors = entry.getValue();

                // Node containers listen on port 8080 for control API
                String hostname = nodeId.value();
                int port = 8080;

                DistributedNodeClient client = new DistributedNodeClient(hostname, port);

                // Send configuration to node
                client.configure(nodeId, neighbors, algorithmId);

                nodeClients.put(nodeId, client);
            }

            state = SimulationState.CONFIGURED;

            LOG.info("Algorithm '{}' configured on {} distributed nodes",
                     algorithmId, nodeClients.size());
        } catch (Exception e) {
            LOG.error("Failed to configure algorithm '{}'", algorithmId, e);

            // Cleanup on failure
            if (containerIds != null) {
                orchestrator.stopAllNodes(simulationId);
            }

            state = SimulationState.UNINITIALIZED;
            throw new RuntimeException("Failed to configure distributed algorithm", e);
        }
    }

    @Override
    public void startSimulation(SimulationParameters parameters) {
        if (parameters == null) {
            throw new IllegalArgumentException("parameters must not be null");
        }
        if (state != SimulationState.CONFIGURED) {
            throw new IllegalStateException("Algorithm not configured. Current state: " + state);
        }

        LOG.info("Starting distributed simulation with {} nodes", nodeClients.size());

        try {
            // Mark simulation start time for metrics
            metricsAggregator.markSimulationStart();

            // IMPORTANT: 2-phase start to prevent race conditions
            // Similar to DefaultSimulationEngine, we must ensure ALL nodes are ready
            // to receive messages BEFORE any node starts sending.
            //
            // In distributed mode:
            // - Phase 1 (already done): markAsStarted() was called in NodeApplication.configure()
            // - Phase 2 (here): Start all nodes SIMULTANEOUSLY (not sequentially!)
            //
            // If we start nodes sequentially (node-0, then node-1, ...):
            // - node-0 calls onStart() and immediately sends messages
            // - node-4 might not have called onStart() yet
            // - Race condition!
            //
            // Solution: Start all nodes in parallel using CompletableFuture

            LOG.debug("Starting all {} nodes simultaneously to prevent race conditions", nodeClients.size());

            List<CompletableFuture<Void>> startFutures = new ArrayList<>();

            for (Map.Entry<NodeId, DistributedNodeClient> entry : nodeClients.entrySet()) {
                NodeId nodeId = entry.getKey();
                DistributedNodeClient client = entry.getValue();

                // Start each node in parallel
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        client.start(parameters);
                        LOG.info("Node {} started successfully", nodeId);
                    } catch (Exception e) {
                        LOG.error("Failed to start node {}", nodeId, e);
                        throw new RuntimeException("Failed to start node " + nodeId, e);
                    }
                });

                startFutures.add(future);
            }

            // Wait for ALL nodes to start
            try {
                CompletableFuture.allOf(
                    startFutures.toArray(new CompletableFuture[0])
                ).join();
            } catch (CompletionException e) {
                Throwable cause = e.getCause();
                LOG.error("One or more nodes failed to start", cause);
                throw new RuntimeException("Failed to start all nodes", cause);
            }

            LOG.info("All {} nodes started successfully", nodeClients.size());

            state = SimulationState.RUNNING;

            // Publish simulation started event
            if (eventPublisher != null) {
                SimulationEvent startEvent = SimulationEvent.withoutPeer(
                    System.currentTimeMillis(),
                    EventType.STATE_CHANGED,
                    "system",
                    "Simulation started with " + nodeClients.size() + " distributed nodes"
                );
                eventPublisher.publish(startEvent);
            }

            LOG.info("Distributed simulation started successfully");
        } catch (Exception e) {
            LOG.error("Failed to start distributed simulation", e);
            state = SimulationState.CONFIGURED;
            throw new RuntimeException("Failed to start distributed simulation", e);
        }
    }

    @Override
    public void pauseSimulation() {
        if (state != SimulationState.RUNNING) {
            throw new IllegalStateException("Simulation not running. Current state: " + state);
        }

        LOG.info("Pausing distributed simulation");

        // Send pause command to all nodes
        for (Map.Entry<NodeId, DistributedNodeClient> entry : nodeClients.entrySet()) {
            try {
                entry.getValue().pause();
            } catch (Exception e) {
                LOG.warn("Failed to pause node {}: {}", entry.getKey(), e.getMessage());
            }
        }

        state = SimulationState.PAUSED;
        LOG.info("Distributed simulation paused");
    }

    @Override
    public void resumeSimulation() {
        if (state != SimulationState.PAUSED) {
            throw new IllegalStateException("Simulation not paused. Current state: " + state);
        }

        LOG.info("Resuming distributed simulation");

        // Send resume command to all nodes
        for (Map.Entry<NodeId, DistributedNodeClient> entry : nodeClients.entrySet()) {
            try {
                entry.getValue().resume();
            } catch (Exception e) {
                LOG.warn("Failed to resume node {}: {}", entry.getKey(), e.getMessage());
            }
        }

        state = SimulationState.RUNNING;
        LOG.info("Distributed simulation resumed");
    }

    @Override
    public void stopSimulation() {
        if (state != SimulationState.RUNNING && state != SimulationState.PAUSED) {
            LOG.warn("Stop called but simulation not running. Current state: {}", state);
        }

        LOG.info("Stopping distributed simulation");

        try {
            // Mark simulation end time for metrics
            metricsAggregator.markSimulationEnd();

            // Send stop command to all nodes (best-effort)
            for (Map.Entry<NodeId, DistributedNodeClient> entry : nodeClients.entrySet()) {
                try {
                    entry.getValue().stop();
                    LOG.debug("Stopped node {}", entry.getKey());
                } catch (Exception e) {
                    LOG.warn("Failed to stop node {}: {}", entry.getKey(), e.getMessage());
                }
            }

            // Stop and remove Docker containers
            orchestrator.stopAllNodes(simulationId);

            state = SimulationState.STOPPED;

            // Publish simulation stopped event
            if (eventPublisher != null) {
                SimulationEvent stopEvent = SimulationEvent.withoutPeer(
                    System.currentTimeMillis(),
                    EventType.STATE_CHANGED,
                    "system",
                    "Simulation stopped"
                );
                eventPublisher.publish(stopEvent);
            }

            LOG.info("Distributed simulation stopped and containers removed");
        } catch (Exception e) {
            LOG.error("Error during simulation stop", e);
            state = SimulationState.STOPPED;
        }
    }

    @Override
    public void setEventPublisher(SimulationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
        LOG.debug("Event publisher set for distributed simulation engine");
    }

    @Override
    public MetricsSnapshot getMetrics() {
        // Metrics aggregated from events (no polling!)
        // Conforms to section 8.8: "MetricsCollector subscribes to the EventBus"
        return metricsAggregator.getSnapshot();
    }

    @Override
    public Map<NodeId, Set<NodeId>> getTopology() {
        if (topology == null) {
            return Map.of();
        }
        return Map.copyOf(topology);
    }

    @Override
    public int getNodeCount() {
        return topology != null ? topology.size() : 0;
    }

    /**
     * Gets the current simulation state.
     *
     * @return current simulation state
     */
    public SimulationState getState() {
        return state;
    }

    /**
     * Gets the current algorithm ID.
     *
     * @return algorithm ID, or null if not configured
     */
    public String getCurrentAlgorithmId() {
        return currentAlgorithmId;
    }

    /**
     * Gets the simulation ID.
     *
     * @return simulation ID, or null if not initialized
     */
    public SimulationId getSimulationId() {
        return simulationId;
    }
}
