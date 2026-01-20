package de.haw.vsp.simulation.engine.standalone;

import de.haw.vsp.simulation.core.*;
import de.haw.vsp.simulation.engine.*;
import de.haw.vsp.simulation.middleware.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Standalone application that runs a single simulation node in distributed mode.
 * 
 * This application:
 * - Runs ONE node per container/process
 * - Uses UDP for real distributed communication
 * - Registers with the backend for coordination
 * - Executes the distributed algorithm autonomously
 * 
 * Environment variables required:
 * - NODE_ID: Unique node identifier (e.g., node-0)
 * - MW_MODE: Must be "udp-docker"
 * - UDP_PORT: UDP port for communication (default: 9000)
 * - NODE_COUNT: Total number of nodes in the simulation
 * - BACKEND_URL: Backend API URL (default: http://vsp-backend:8080)
 */
public class StandaloneNodeApplication {
    
    private static final Logger LOG = LoggerFactory.getLogger(StandaloneNodeApplication.class);
    
    private final NodeId nodeId;
    private final MessagingPort messagingPort;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicBoolean paused = new AtomicBoolean(false);
    private final BackendEventReporter eventReporter;
    private final NodeControlServer controlServer;
    
    private SimulationNode node;
    private SimulationNodeContext nodeContext;
    
    public StandaloneNodeApplication(StandaloneNodeConfig config) {
        this.nodeId = config.nodeId();
        
        // Create UDP-based messaging port with dynamic configuration
        TransportConfig transportConfig = EnvTransportConfigs.fromEnvironment(System.getenv());
        this.messagingPort = MessagingPorts.udpDocker(config.nodeId(), transportConfig);
        
        // Initialize event reporter for backend communication
        String simulationId = System.getenv().getOrDefault("SIMULATION_ID", "distributed");
        this.eventReporter = new BackendEventReporter(config.backendUrl(), simulationId, config.nodeId());
        
        // Initialize control server for receiving commands
        int controlPort = 8000 + extractNodeNumber(config.nodeId());
        try {
            this.controlServer = new NodeControlServer(nodeId, controlPort, new NodeControlServer.ControlCallback() {
                @Override
                public void onPause() {
                    paused.set(true);
                    LOG.info("Node {} paused", nodeId);
                }
                
                @Override
                public void onResume() {
                    paused.set(false);
                    LOG.info("Node {} resumed", nodeId);
                }
                
                @Override
                public void onStep() {
                    if (node != null && paused.get()) {
                        // Execute one step while paused
                        LOG.info("Node {} executing one step", nodeId);
                    }
                }
            });
            controlServer.start();
        } catch (Exception e) {
            throw new RuntimeException("Failed to start control server on port " + controlPort, e);
        }
        
        LOG.info("Standalone node initialized: {}", nodeId);
        LOG.info("UDP Mode: {}, Port: {}", config.udpMode(), config.udpPort());
        LOG.info("Control Server: Port {}", controlPort);
    }
    
    /**
     * Extract node number from NodeId (e.g., "node-5" -> 5).
     */
    private static int extractNodeNumber(NodeId nodeId) {
        String value = nodeId.value();
        int dashIndex = value.lastIndexOf('-');
        if (dashIndex >= 0 && dashIndex < value.length() - 1) {
            try {
                return Integer.parseInt(value.substring(dashIndex + 1));
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }
    
    /**
     * Initialize the node with network topology.
     * This is called after receiving topology information from the backend.
     */
    public void initializeNode(Set<NodeId> neighbors, NodeAlgorithm algorithm) {
        LOG.info("Initializing node {} with {} neighbors", nodeId, neighbors.size());
        
        // Create node context with event reporting and store it
        this.nodeContext = new SimulationNodeContext(
            nodeId,
            neighbors,
            messagingPort,
            msg -> {
                LOG.debug("Message sent from {}", nodeId);
                eventReporter.reportMessageSent(msg);
            }
        );
        
        // Create the simulation node
        this.node = new SimulationNode(nodeId, neighbors, algorithm, nodeContext);
        
        // Start the node BEFORE registering handler to avoid race condition
        LOG.info("Starting node {}", nodeId);
        node.onStart();
        LOG.info("Node {} started and ready to receive messages", nodeId);
        
        // Register message handler AFTER onStart()
        messagingPort.registerHandler(nodeId, message -> {
            if (node != null) {
                eventReporter.reportMessageReceived(message);
                
                // Extract leader from LEADER_ANNOUNCEMENT messages
                if ("LEADER_ANNOUNCEMENT".equals(message.messageType().toString())) {
                    String leaderId = message.payload() != null ? message.payload().toString() : null;
                    if (leaderId != null) {
                        eventReporter.reportLeaderChange(leaderId);
                    }
                }
                
                node.onMessage(message);
            }
        });
        
        LOG.info("Node {} initialized successfully", nodeId);
    }
    
    /**
     * Start the node's algorithm execution.
     * Note: onStart() is now called in initializeNode() to avoid race conditions.
     */
    public void startNode() {
        // Node is already started in initializeNode()
        LOG.info("Node {} is active (already started during initialization)", nodeId);
    }
    
    /**
     * Run the standalone node application.
     */
    public void run() {
        LOG.info("Standalone node {} is running...", nodeId);
        LOG.info("Leader Election Algorithm is active and processing messages...");
        
        // Keep the application running and periodically re-broadcast leader to ensure convergence
        // Use exponential backoff: fast initially, slower over time
        int stepCount = 0;
        try {
            while (running.get()) {
                Thread.sleep(1000);
                
                // Only execute steps if not paused
                if (!paused.get()) {
                    // Exponential backoff schedule for re-broadcasts:
                    // 0-30s: every 1 second (fast initial convergence)
                    // 30-60s: every 3 seconds
                    // 60-120s: every 5 seconds
                    // 120s+: every 10 seconds (stable state)
                    int interval;
                    if (stepCount < 30) {
                        interval = 1;  // First 30 seconds: aggressive
                    } else if (stepCount < 60) {
                        interval = 3;  // 30-60s: moderate
                    } else if (stepCount < 120) {
                        interval = 5;  // 60-120s: relaxed
                    } else {
                        interval = 10; // After 120s: stable
                    }
                    
                    // Re-broadcast current leader at calculated interval
                    if (stepCount % interval == 0 && node != null) {
                        LOG.debug("Step {} (interval={}s) - Re-broadcasting current leader for node {}", 
                                 stepCount, interval, nodeId);
                        FloodingLeaderElectionAlgorithm algorithm = (FloodingLeaderElectionAlgorithm) node.getAlgorithm();
                        if (algorithm != null && algorithm.getCurrentLeaderId() != null) {
                            // Re-broadcast current leader to all neighbors
                            for (NodeId neighbor : node.getNeighbors()) {
                                nodeContext.send(neighbor, new SimulationMessage(
                                    nodeId,
                                    neighbor,
                                    "LEADER_ANNOUNCEMENT",
                                    algorithm.getCurrentLeaderId().value(),
                                    null
                                ));
                            }
                        }
                    }
                    stepCount++;
                } else {
                    LOG.trace("Node {} is paused, waiting...", nodeId);
                }
            }
        } catch (InterruptedException e) {
            LOG.info("Node {} interrupted", nodeId);
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Shutdown the node gracefully.
     */
    public void shutdown() {
        LOG.info("Shutting down node {}", nodeId);
        running.set(false);
        
        if (controlServer != null) {
            controlServer.stop();
        }
        
        if (messagingPort != null) {
            messagingPort.unregisterHandler(nodeId);
        }
        
        if (eventReporter != null) {
            eventReporter.shutdown();
        }
        
        LOG.info("Node {} shutdown complete", nodeId);
    }
    
    /**
     * Main entry point for standalone node application.
     */
    public static void main(String[] args) {
        LOG.info("=================================================");
        LOG.info("  Standalone Distributed Simulation Node");
        LOG.info("=================================================");
        
        try {
            // Load configuration from environment
            StandaloneNodeConfig config = StandaloneNodeConfig.fromEnvironment();
            LOG.info("Configuration loaded: {}", config);
            
            // Create and run the application
            StandaloneNodeApplication app = new StandaloneNodeApplication(config);
            
            // Register shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                LOG.info("Shutdown signal received");
                app.shutdown();
            }));
            
            // For now, we initialize with a simple setup
            // In a full implementation, this would wait for backend coordination
            LOG.info("Node is ready and waiting for coordination...");
            
            // Create a simple topology for testing (all nodes in a ring)
            Set<NodeId> neighbors = calculateNeighbors(config);
            
            // Use flooding algorithm
            NodeAlgorithm algorithm = new FloodingLeaderElectionAlgorithm();
            
            // Initialize and start
            app.initializeNode(neighbors, algorithm);
            
            // Wait a bit for all nodes to be ready
            Thread.sleep(3000);
            
            // Start the node
            app.startNode();
            
            // Run indefinitely
            app.run();
            
        } catch (Exception e) {
            LOG.error("Fatal error in standalone node", e);
            System.exit(1);
        }
    }
    
    /**
     * Calculate neighbors based on node ID, total node count, and topology type.
     */
    private static Set<NodeId> calculateNeighbors(StandaloneNodeConfig config) {
        String topology = config.topology().toUpperCase();
        int nodeCount = config.nodeCount();
        int currentNodeNum = extractNodeNumber(config.nodeId());
        
        Set<NodeId> neighbors = switch (topology) {
            case "RING" -> calculateRingNeighbors(currentNodeNum, nodeCount);
            case "LINE" -> calculateLineNeighbors(currentNodeNum, nodeCount);
            case "GRID" -> calculateGridNeighbors(currentNodeNum, nodeCount);
            case "RANDOM" -> calculateRandomNeighbors(currentNodeNum, nodeCount);
            default -> {
                LOG.warn("Unknown topology '{}', using RING as default", topology);
                yield calculateRingNeighbors(currentNodeNum, nodeCount);
            }
        };
        
        LOG.info("Calculated {} neighbors for node-{} (topology={}): {}", 
                 neighbors.size(), currentNodeNum, topology, neighbors);
        
        return neighbors;
    }
    
    /**
     * Calculate neighbors for RING topology.
     * Each node has exactly 2 neighbors (previous and next, with wraparound).
     */
    private static Set<NodeId> calculateRingNeighbors(int nodeNum, int nodeCount) {
        Set<NodeId> neighbors = new HashSet<>();
        
        int prevNode = (nodeNum - 1 + nodeCount) % nodeCount;
        int nextNode = (nodeNum + 1) % nodeCount;
        
        neighbors.add(new NodeId("node-" + prevNode));
        neighbors.add(new NodeId("node-" + nextNode));
        
        return neighbors;
    }
    
    /**
     * Calculate neighbors for LINE topology.
     * Each node has 1 or 2 neighbors (endpoints have 1, others have 2).
     */
    private static Set<NodeId> calculateLineNeighbors(int nodeNum, int nodeCount) {
        Set<NodeId> neighbors = new HashSet<>();
        
        if (nodeNum > 0) {
            neighbors.add(new NodeId("node-" + (nodeNum - 1)));
        }
        if (nodeNum < nodeCount - 1) {
            neighbors.add(new NodeId("node-" + (nodeNum + 1)));
        }
        
        return neighbors;
    }
    
    /**
     * Calculate neighbors for GRID topology.
     * Each node has up to 4 neighbors (north, south, east, west).
     */
    private static Set<NodeId> calculateGridNeighbors(int nodeNum, int nodeCount) {
        Set<NodeId> neighbors = new HashSet<>();
        
        // Calculate grid dimensions (as square as possible)
        int rows = (int) Math.sqrt(nodeCount);
        int cols = (nodeCount + rows - 1) / rows;
        
        int row = nodeNum / cols;
        int col = nodeNum % cols;
        
        // North neighbor
        if (row > 0) {
            int northIdx = (row - 1) * cols + col;
            if (northIdx < nodeCount) {
                neighbors.add(new NodeId("node-" + northIdx));
            }
        }
        
        // South neighbor
        if (row < rows - 1) {
            int southIdx = (row + 1) * cols + col;
            if (southIdx < nodeCount) {
                neighbors.add(new NodeId("node-" + southIdx));
            }
        }
        
        // West neighbor
        if (col > 0) {
            neighbors.add(new NodeId("node-" + (nodeNum - 1)));
        }
        
        // East neighbor
        if (col < cols - 1 && nodeNum + 1 < nodeCount) {
            neighbors.add(new NodeId("node-" + (nodeNum + 1)));
        }
        
        return neighbors;
    }
    
    /**
     * Calculate neighbors for RANDOM topology.
     * Each node connects to a random subset of other nodes.
     */
    private static Set<NodeId> calculateRandomNeighbors(int nodeNum, int nodeCount) {
        Set<NodeId> neighbors = new HashSet<>();
        Random random = new Random(nodeNum); // Seed with node number for deterministic results
        
        // Each node connects to approximately sqrt(n) random neighbors
        int targetNeighborCount = Math.max(2, (int) Math.sqrt(nodeCount));
        
        for (int i = 0; i < targetNeighborCount; i++) {
            int randomNode;
            do {
                randomNode = random.nextInt(nodeCount);
            } while (randomNode == nodeNum || neighbors.contains(new NodeId("node-" + randomNode)));
            
            neighbors.add(new NodeId("node-" + randomNode));
        }
        
        return neighbors;
    }
    
}
