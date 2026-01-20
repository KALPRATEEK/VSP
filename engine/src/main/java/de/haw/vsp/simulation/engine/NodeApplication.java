package de.haw.vsp.simulation.engine;

import de.haw.vsp.simulation.core.*;
import de.haw.vsp.simulation.middleware.MessagingPort;
import de.haw.vsp.simulation.middleware.MessagingPorts;
import de.haw.vsp.simulation.middleware.TransportConfig;
import de.haw.vsp.simulation.middleware.EnvTransportConfigs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.Set;

/**
 * Standalone application for a single simulation node running in a Docker container.
 *
 * <p>Conforms to § 8.10 "Docker-Based Node Isolation Concept":
 * "Each logical node maps to one Docker container."
 *
 * <p>This application:
 * <ul>
 *   <li>Runs as a Spring Boot application in a Docker container</li>
 *   <li>Reads configuration from environment variables</li>
 *   <li>Creates a single SimulationNode with UDP messaging</li>
 *   <li>Exposes REST API for lifecycle control (configure, start, stop)</li>
 *   <li>Publishes events asynchronously to backend</li>
 * </ul>
 *
 * <p>Environment variables:
 * <ul>
 *   <li>NODE_ID: Unique node identifier (e.g., "node-0")</li>
 *   <li>UDP_PORT: UDP port for node-to-node communication (default: 9000)</li>
 *   <li>BACKEND_URL: Backend URL for event publishing (e.g., "http://backend:8080")</li>
 *   <li>SIMULATION_ID: Simulation identifier</li>
 *   <li>NEIGHBORS: Comma-separated list of neighbor node IDs (optional, can be configured via REST)</li>
 * </ul>
 */
@SpringBootApplication
@EnableScheduling
public class NodeApplication {

    private static final Logger LOG = LoggerFactory.getLogger(NodeApplication.class);

    private final String nodeIdStr;
    private final int udpPort;
    private final String backendUrl;
    private final SimulationId simulationId;

    private NodeId nodeId;
    private MessagingPort messagingPort;
    private EventPublisherService eventPublisher;
    private NodeMetricsCollector metricsCollector;
    private SimulationNode node;
    private NodeAlgorithm algorithm;
    private Set<NodeId> neighbors;

    // Local EventBus for node-internal events
    private InMemorySimulationEventBus localEventBus;

    private volatile boolean configured = false;
    private volatile boolean started = false;

    public NodeApplication() {
        // Read environment variables
        this.nodeIdStr = getEnvOrDefault("NODE_ID", "node-0");
        this.udpPort = Integer.parseInt(getEnvOrDefault("UDP_PORT", "9000"));
        this.backendUrl = getEnvOrThrow("BACKEND_URL");
        this.simulationId = new SimulationId(getEnvOrThrow("SIMULATION_ID"));

        LOG.info("NodeApplication initializing: nodeId={}, udpPort={}, backendUrl={}, simulationId={}",
                 nodeIdStr, udpPort, backendUrl, simulationId);
    }

    @PostConstruct
    public void initialize() {
        this.nodeId = new NodeId(nodeIdStr);

        // Initialize LOCAL EventBus for node-internal events
        // This is critical for metrics collection (§ 8.2 Event-Driven Architecture)
        this.localEventBus = new InMemorySimulationEventBus();

        LOG.info("Local EventBus initialized for node {}", nodeId);

        // Initialize event publisher (sends to backend)
        this.eventPublisher = new EventPublisherService(backendUrl, simulationId);
        this.eventPublisher.start();

        // Initialize metrics collector
        this.metricsCollector = new NodeMetricsCollector(nodeId, eventPublisher);

        // CRITICAL: Subscribe metrics collector to local EventBus
        // This ensures metrics are captured from MESSAGE_SENT/RECEIVED events
        localEventBus.subscribe(EventType.MESSAGE_SENT, metricsCollector);
        localEventBus.subscribe(EventType.MESSAGE_RECEIVED, metricsCollector);
        localEventBus.subscribe(EventType.STATE_CHANGED, metricsCollector);

        LOG.info("NodeMetricsCollector subscribed to local EventBus");

        // Create a dual-publishing adapter: publishes to BOTH local bus AND backend
        SimulationEventPublisher dualPublisher = event -> {
            // 1. Publish to local EventBus (for metrics collector)
            localEventBus.publish(event);

            // 2. Forward to backend (for UI/visualization)
            eventPublisher.publish(event);
        };

        // Create UDP MessagingPort with dual publisher
        TransportConfig transportConfig = EnvTransportConfigs.fromEnvironment(System.getenv());
        this.messagingPort = MessagingPorts.udpDocker(nodeId, transportConfig, dualPublisher);

        LOG.info("NodeApplication initialized successfully for node {} with local EventBus", nodeId);
    }

    @PreDestroy
    public void shutdown() {
        LOG.info("Shutting down NodeApplication for node {}", nodeId);

        if (eventPublisher != null) {
            eventPublisher.stop();
        }

        if (messagingPort != null) {
            // MessagingPort cleanup (if needed)
        }

        LOG.info("NodeApplication shutdown complete");
    }

    @Bean
    public NodeMetricsCollector nodeMetricsCollector() {
        return metricsCollector;
    }

    private String getEnvOrDefault(String key, String defaultValue) {
        String value = System.getenv(key);
        return value != null && !value.isBlank() ? value : defaultValue;
    }

    private String getEnvOrThrow(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Required environment variable not set: " + key);
        }
        return value;
    }

    public static void main(String[] args) {
        SpringApplication.run(NodeApplication.class, args);
    }

    /**
     * Callback invoked when the leader changes in FloodingLeaderElectionAlgorithm.
     *
     * Publishes a LEADER_ELECTED event to both local EventBus and backend.
     * Conforms to § 8.2 "Event-Driven Architecture".
     *
     * @param newLeader the new leader node ID
     */
    private void onLeaderChanged(NodeId newLeader) {
        LOG.info("Node {} detected new leader: {}", nodeId, newLeader);

        // Create LEADER_ELECTED event
        SimulationEvent leaderEvent = SimulationEvent.withoutPeer(
            System.currentTimeMillis(),
            EventType.LEADER_ELECTED,
            newLeader.value(),
            "Leader elected: " + newLeader.value()
        );

        // Publish to local EventBus (for local metrics)
        if (localEventBus != null) {
            localEventBus.publish(leaderEvent);
        }

        // Publish to backend (for UI)
        if (eventPublisher != null) {
            eventPublisher.publish(leaderEvent);
        }
    }

    /**
     * REST controller for node lifecycle management.
     */
    @RestController
    @RequestMapping("/node")
    class NodeController {

        /**
         * Configures the node with topology and algorithm.
         *
         * <p>This must be called before starting the node.
         *
         * @param config node configuration
         * @return 200 OK if successful
         */
        @PostMapping("/configure")
        public ResponseEntity<String> configure(@RequestBody NodeConfig config) {
            LOG.info("Received configure request for node {}: algorithm={}, neighbors={}",
                     nodeId, config.algorithmId(), config.neighbors());

            try {
                // Create algorithm instance
                algorithm = createAlgorithm(config.algorithmId());

                // If algorithm is FloodingLeaderElectionAlgorithm, set leader change callback
                if (algorithm instanceof FloodingLeaderElectionAlgorithm flea) {
                    flea.setOnLeaderChanged(NodeApplication.this::onLeaderChanged);
                    LOG.info("Leader change callback registered for node {}", nodeId);
                }

                neighbors = config.neighbors();

                // Create node context
                NodeContext context = new SimulationNodeContext(
                    nodeId,
                    neighbors,
                    messagingPort
                );

                // Create simulation node
                node = new SimulationNode(
                    nodeId,
                    neighbors,
                    algorithm,
                    context
                );

                // Register message handler for incoming messages
                messagingPort.registerHandler(nodeId, node::onMessage);

                // Mark node as started to allow receiving messages
                // (algorithm.onStart() will be called later via /start endpoint)
                node.markAsStarted();

                configured = true;

                LOG.info("Node {} configured successfully with algorithm {} and {} neighbors",
                         nodeId, config.algorithmId(), neighbors.size());

                return ResponseEntity.ok("Node configured successfully");
            } catch (Exception e) {
                LOG.error("Failed to configure node {}", nodeId, e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Configuration failed: " + e.getMessage());
            }
        }

        /**
         * Starts the node algorithm execution.
         *
         * @param parameters simulation parameters
         * @return 200 OK if successful
         */
        @PostMapping("/start")
        public ResponseEntity<String> start(@RequestBody SimulationParameters parameters) {
            LOG.info("Received start request for node {}", nodeId);

            if (!configured) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Node not configured. Call /configure first.");
            }

            if (started) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Node already started");
            }

            try {
                // Start the node (triggers algorithm.onStart())
                node.onStart();
                started = true;

                LOG.info("Node {} started successfully", nodeId);

                return ResponseEntity.ok("Node started successfully");
            } catch (Exception e) {
                LOG.error("Failed to start node {}", nodeId, e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Start failed: " + e.getMessage());
            }
        }

        /**
         * Stops the node execution.
         *
         * @return 200 OK if successful
         */
        @PostMapping("/stop")
        public ResponseEntity<String> stop() {
            LOG.info("Received stop request for node {}", nodeId);

            if (!started) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Node not started");
            }

            try {
                started = false;

                // Unregister message handler
                if (messagingPort != null && nodeId != null) {
                    messagingPort.unregisterHandler(nodeId);
                }

                // Flush remaining events
                if (eventPublisher != null) {
                    eventPublisher.stop();
                }

                LOG.info("Node {} stopped successfully", nodeId);

                return ResponseEntity.ok("Node stopped successfully");
            } catch (Exception e) {
                LOG.error("Failed to stop node {}", nodeId, e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Stop failed: " + e.getMessage());
            }
        }

        /**
         * Returns the current status of the node.
         *
         * @return node status
         */
        @GetMapping("/status")
        public ResponseEntity<NodeStatus> getStatus() {
            return ResponseEntity.ok(new NodeStatus(
                nodeId.value(),
                configured ? "CONFIGURED" : "UNINITIALIZED",
                started ? "RUNNING" : "STOPPED",
                algorithm != null ? algorithm.getClass().getSimpleName() : null,
                neighbors != null ? neighbors.size() : 0
            ));
        }

        /**
         * Health check endpoint.
         *
         * @return 200 OK if node is healthy
         */
        @GetMapping("/health")
        public ResponseEntity<String> health() {
            return ResponseEntity.ok("OK");
        }
    }

    /**
     * Creates an algorithm instance based on algorithm ID.
     *
     * @param algorithmId the algorithm identifier
     * @return algorithm instance
     * @throws IllegalArgumentException if algorithm ID is unknown
     */
    private NodeAlgorithm createAlgorithm(String algorithmId) {
        return switch (algorithmId) {
            case "flooding", "flooding-leader-election" -> new FloodingLeaderElectionAlgorithm();
            default -> throw new IllegalArgumentException("Unknown algorithm: " + algorithmId);
        };
    }

    /**
     * DTO for node configuration.
     */
    public record NodeConfig(
        String algorithmId,
        Set<NodeId> neighbors
    ) {
        public NodeConfig {
            if (algorithmId == null || algorithmId.isBlank()) {
                throw new IllegalArgumentException("algorithmId must not be null or blank");
            }
            if (neighbors == null) {
                throw new IllegalArgumentException("neighbors must not be null");
            }
        }
    }

    /**
     * DTO for node status.
     */
    public record NodeStatus(
        String nodeId,
        String configurationState,
        String executionState,
        String algorithm,
        int neighborCount
    ) {}
}
