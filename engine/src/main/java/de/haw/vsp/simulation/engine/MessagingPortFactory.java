package de.haw.vsp.simulation.engine;

import de.haw.vsp.simulation.core.NodeId;
import de.haw.vsp.simulation.core.SimulationEventPublisher;
import de.haw.vsp.simulation.middleware.MessagingPort;
import de.haw.vsp.simulation.middleware.MessagingPorts;
import de.haw.vsp.simulation.middleware.TransportConfig;
import de.haw.vsp.simulation.middleware.EnvTransportConfigs;

import java.util.logging.Logger;

/**
 * Factory for creating MessagingPort instances based on environment configuration.
 * 
 * Supports two modes:
 * - virtual: In-memory messaging for development and testing
 * - udp-docker: Real UDP networking across Docker containers
 * 
 * Configuration via environment variables:
 * - MW_MODE: "virtual" (default) or "udp-docker"
 * - NODE_ID: Required for udp-docker mode (e.g., "node-0")
 * - UDP_PORT: Port for UDP communication (default: 9000)
 */
public class MessagingPortFactory {
    
    private static final Logger LOGGER = Logger.getLogger(MessagingPortFactory.class.getName());
    
    private static final String ENV_MW_MODE = "MW_MODE";
    private static final String ENV_NODE_ID = "NODE_ID";
    private static final String ENV_UDP_PORT = "UDP_PORT";
    
    private static final String MODE_VIRTUAL = "virtual";
    private static final String MODE_UDP_DOCKER = "udp-docker";
    private static final int DEFAULT_UDP_PORT = 9000;
    
    private MessagingPortFactory() {
        // Utility class
    }
    
    /**
     * Creates a MessagingPort based on environment configuration.
     * 
     * @param eventPublisher optional event publisher for observability
     * @return configured MessagingPort instance
     * @throws IllegalStateException if required configuration is missing
     */
    public static MessagingPort create(SimulationEventPublisher eventPublisher) {
        String mode = System.getenv(ENV_MW_MODE);
        
        // Default to virtual mode if not specified
        if (mode == null || mode.isBlank()) {
            mode = MODE_VIRTUAL;
            LOGGER.info("MW_MODE not set, defaulting to 'virtual' mode");
        }
        
        LOGGER.info("Creating MessagingPort in mode: " + mode);
        
        switch (mode.toLowerCase()) {
            case MODE_VIRTUAL:
                return createVirtualPort(eventPublisher);
                
            case MODE_UDP_DOCKER:
                return createUdpDockerPort(eventPublisher);
                
            default:
                throw new IllegalStateException(
                    "Unknown MW_MODE: " + mode + ". Expected 'virtual' or 'udp-docker'"
                );
        }
    }
    
    /**
     * Creates a MessagingPort without event publisher.
     */
    public static MessagingPort create() {
        return create(null);
    }
    
    /**
     * Creates an in-memory virtual MessagingPort.
     */
    private static MessagingPort createVirtualPort(SimulationEventPublisher eventPublisher) {
        LOGGER.info("Initializing virtual (in-memory) MessagingPort");
        return MessagingPorts.virtual(eventPublisher);
    }
    
    /**
     * Creates a UDP-based MessagingPort for distributed execution.
     * 
     * Requires environment variables:
     * - NODE_ID: The ID of this node (e.g., "node-0")
     * - UDP_PORT: (optional) UDP port for communication, defaults to 9000
     * - HOST_TEMPLATE: (optional) hostname template, defaults to "{ID}" (Docker hostname = NodeId)
     * 
     * Additional optional variables for explicit peer configuration:
     * - PEERS: Comma-separated list of "id:host:port" entries
     * - NODE_COUNT: Number of nodes (for bounded configuration)
     */
    private static MessagingPort createUdpDockerPort(SimulationEventPublisher eventPublisher) {
        // Get required NODE_ID
        String nodeIdStr = System.getenv(ENV_NODE_ID);
        if (nodeIdStr == null || nodeIdStr.isBlank()) {
            throw new IllegalStateException(
                "NODE_ID environment variable is required for udp-docker mode. " +
                "Example: NODE_ID=node-0"
            );
        }
        
        NodeId localNode = new NodeId(nodeIdStr);
        
        LOGGER.info("Initializing UDP MessagingPort for node: " + nodeIdStr);
        
        // Create transport config from environment variables
        // This handles HOST_TEMPLATE, UDP_PORT, PEERS, etc.
        TransportConfig transportConfig = EnvTransportConfigs.fromEnvironment(System.getenv());
        
        return MessagingPorts.udpDocker(localNode, transportConfig, eventPublisher);
    }
    
    /**
     * Returns the current middleware mode.
     */
    public static String getCurrentMode() {
        String mode = System.getenv(ENV_MW_MODE);
        return (mode == null || mode.isBlank()) ? MODE_VIRTUAL : mode.toLowerCase();
    }
    
    /**
     * Returns true if running in virtual mode.
     */
    public static boolean isVirtualMode() {
        return MODE_VIRTUAL.equals(getCurrentMode());
    }
    
    /**
     * Returns true if running in udp-docker mode.
     */
    public static boolean isUdpDockerMode() {
        return MODE_UDP_DOCKER.equals(getCurrentMode());
    }
}
