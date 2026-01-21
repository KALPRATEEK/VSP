package de.haw.vsp.simulation.engine.standalone;

import de.haw.vsp.simulation.core.NodeId;

/**
 * Configuration for standalone node application.
 * Loaded from environment variables.
 */
public record StandaloneNodeConfig(
    NodeId nodeId,
    String udpMode,
    int udpPort,
    int nodeCount,
    String topology,
    String backendUrl
) {
    
    /**
     * Load configuration from environment variables.
     */
    public static StandaloneNodeConfig fromEnvironment() {
        String nodeIdStr = getEnvOrThrow("NODE_ID");
        String udpMode = getEnvOrDefault("MW_MODE", "udp-docker");
        int udpPort = Integer.parseInt(getEnvOrDefault("UDP_PORT", "9000"));
        int nodeCount = Integer.parseInt(getEnvOrDefault("NODE_COUNT", "5"));
        String topology = getEnvOrDefault("TOPOLOGY", "RING");
        String backendUrl = getEnvOrDefault("BACKEND_URL", "http://vsp-backend:8080");
        
        if (!udpMode.equals("udp-docker")) {
            throw new IllegalArgumentException(
                "Standalone node requires MW_MODE=udp-docker, but got: " + udpMode
            );
        }
        
        return new StandaloneNodeConfig(
            new NodeId(nodeIdStr),
            udpMode,
            udpPort,
            nodeCount,
            topology,
            backendUrl
        );
    }
    
    private static String getEnvOrThrow(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                "Required environment variable not set: " + name
            );
        }
        return value;
    }
    
    private static String getEnvOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return (value == null || value.isBlank()) ? defaultValue : value;
    }
    
    @Override
    public String toString() {
        return String.format(
            "StandaloneNodeConfig{nodeId=%s, mode=%s, port=%d, nodeCount=%d, topology=%s, backend=%s}",
            nodeId, udpMode, udpPort, nodeCount, topology, backendUrl
        );
    }
}
