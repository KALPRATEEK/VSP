package de.haw.vsp.simulation.engine;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import de.haw.vsp.simulation.core.NetworkConfig;
import de.haw.vsp.simulation.core.NodeId;
import de.haw.vsp.simulation.core.SimulationId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Orchestrates Docker containers for distributed simulation nodes.
 *
 * <p>Conforms to § 8.10 "Docker-Based Node Isolation Concept":
 * "Each logical node maps to one Docker container."
 *
 * <p>This orchestrator:
 * <ul>
 *   <li>Dynamically creates Docker containers based on NetworkConfig</li>
 *   <li>Supports variable number of nodes (not fixed in docker-compose)</li>
 *   <li>Manages container lifecycle (start, stop, remove)</li>
 *   <li>Ensures Docker network exists for container communication</li>
 *   <li>Container names include SimulationId for isolation</li>
 * </ul>
 *
 * <p>Environment variables passed to each container:
 * <ul>
 *   <li>NODE_ID: Unique node identifier (e.g., "node-0")</li>
 *   <li>UDP_PORT: UDP port for node-to-node communication (9000)</li>
 *   <li>BACKEND_URL: Backend URL for event publishing</li>
 *   <li>SIMULATION_ID: Simulation identifier</li>
 *   <li>NEIGHBORS: Comma-separated list of neighbor node IDs</li>
 * </ul>
 *
 * NOTE: This class should NOT be a Spring @Service or @Component.
 * It must only be instantiated in the control module, never in node containers.
 */
public class DockerNodeOrchestrator {

    private static final Logger LOG = LoggerFactory.getLogger(DockerNodeOrchestrator.class);

    private final DockerClient dockerClient;
    private final String nodeImageName;
    private String networkName;  // Non-final to allow adjustment if compose-generated name is found
    private final String backendUrl;

    /**
     * Creates a new Docker node orchestrator.
     *
     * <p>Reads configuration from environment variables:
     * <ul>
     *   <li>NODE_IMAGE: Docker image for nodes (default: "vsp-node:latest")</li>
     *   <li>DOCKER_NETWORK: Docker network name (default: "vsp-network")</li>
     *   <li>BACKEND_URL: Backend URL for nodes (default: "http://host.docker.internal:8080")</li>
     * </ul>
     */
    public DockerNodeOrchestrator() {
        DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
        ApacheDockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
            .dockerHost(config.getDockerHost())
            .build();
        this.dockerClient = DockerClientImpl.getInstance(config, httpClient);
        this.nodeImageName = getEnvOrDefault("NODE_IMAGE", "vsp-node:latest");
        this.networkName = getEnvOrDefault("DOCKER_NETWORK", "vsp-network");
        this.backendUrl = getEnvOrDefault("BACKEND_URL", "http://host.docker.internal:8080");

        ensureNetworkExists();

        LOG.info("DockerNodeOrchestrator initialized: image={}, network={}, backendUrl={}",
                 nodeImageName, networkName, backendUrl);
    }

    /**
     * Ensures the Docker network exists for container communication.
     *
     * <p>Creates a bridge network if it doesn't exist.
     * <p>Searches for the network by exact name match or checks if it's a compose-generated name.
     * <p>If a similar network is found (e.g., docker-compose prefixed name), updates networkName to use it.
     */
    private void ensureNetworkExists() {
        try {
            // List all networks to find exact or similar matches
            List<Network> allNetworks = dockerClient.listNetworksCmd().exec();

            LOG.debug("Searching for network '{}' among {} existing networks", networkName, allNetworks.size());

            // First try exact match
            Network exactMatch = allNetworks.stream()
                .filter(n -> networkName.equals(n.getName()))
                .findFirst()
                .orElse(null);

            if (exactMatch != null) {
                LOG.info("Docker network already exists: {} (ID: {})",
                         networkName, exactMatch.getId().substring(0, 12));
                return;
            }

            // Check if network with similar name exists (docker-compose adds prefix like "vsp_vsp-network")
            Network similarMatch = allNetworks.stream()
                .filter(n -> n.getName() != null &&
                            (n.getName().endsWith(networkName) ||
                             n.getName().endsWith("_" + networkName)))
                .findFirst()
                .orElse(null);

            if (similarMatch != null) {
                LOG.warn("Network '{}' not found, but found similar network: '{}'. Using it.",
                         networkName, similarMatch.getName());
                // Update networkName to use the actual network name from docker-compose
                this.networkName = similarMatch.getName();
                LOG.info("Adjusted network name to '{}'", this.networkName);
                return;
            }

            // Network doesn't exist, create it
            LOG.info("Creating Docker network: {}", networkName);
            dockerClient.createNetworkCmd()
                .withName(networkName)
                .withDriver("bridge")
                .exec();
            LOG.info("Docker network created: {}", networkName);

        } catch (Exception e) {
            LOG.error("Failed to ensure Docker network exists. Available networks:", e);
            // Log all available networks for debugging
            try {
                List<Network> networks = dockerClient.listNetworksCmd().exec();
                networks.forEach(n -> LOG.error("  - {} (ID: {})", n.getName(), n.getId()));
            } catch (Exception ex) {
                LOG.error("Could not list networks for debugging", ex);
            }
            throw new RuntimeException("Docker network setup failed: " + e.getMessage(), e);
        }
    }

    /**
     * Deploys Docker containers for all nodes in the network configuration.
     *
     * <p>Creates one container per node with appropriate environment variables.
     *
     * @param config network configuration
     * @param simulationId simulation identifier
     * @return map of NodeId to Docker container ID
     * @throws RuntimeException if container creation fails
     */
    public Map<NodeId, String> deployNodes(NetworkConfig config, SimulationId simulationId) {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        if (simulationId == null) {
            throw new IllegalArgumentException("simulationId must not be null");
        }

        LOG.info("Deploying {} nodes for simulation {}", config.nodeCount(), simulationId);

        Map<NodeId, Set<NodeId>> topology = TopologyGenerator.generateTopology(config);
        Map<NodeId, String> containerIds = new HashMap<>();

        for (Map.Entry<NodeId, Set<NodeId>> entry : topology.entrySet()) {
            NodeId nodeId = entry.getKey();
            Set<NodeId> neighbors = entry.getValue();

            try {
                String containerId = startNodeContainer(nodeId, neighbors, config, simulationId);
                containerIds.put(nodeId, containerId);
            } catch (Exception e) {
                LOG.error("Failed to start container for node {}", nodeId, e);
                // Cleanup already started containers
                stopAllNodes(simulationId);
                throw new RuntimeException("Failed to deploy node " + nodeId, e);
            }
        }

        LOG.info("Successfully deployed {} node containers for simulation {}",
                 containerIds.size(), simulationId);

        return containerIds;
    }

    /**
     * Starts a Docker container for a single node.
     *
     * @param nodeId node identifier
     * @param neighbors set of neighbor node IDs
     * @param config network configuration (used for total node count)
     * @param simulationId simulation identifier
     * @return Docker container ID
     */
    private String startNodeContainer(NodeId nodeId, Set<NodeId> neighbors, NetworkConfig config, SimulationId simulationId) {
        String containerName = String.format(
            "vsp-node-%s-%s",
            nodeId.value(),
            simulationId.value().substring(0, 8)
        );

        String neighborsStr = neighbors.stream()
            .map(NodeId::value)
            .collect(Collectors.joining(","));

        // Create container (without network - we'll connect it separately with alias)
        CreateContainerResponse container = dockerClient.createContainerCmd(nodeImageName)
            .withName(containerName)
            .withHostName(nodeId.value()) // DNS hostname = node-0, node-1, ...
            .withEnv(
                "NODE_ID=" + nodeId.value(),
                "UDP_PORT=9000",
                "BACKEND_URL=" + backendUrl,
                "SIMULATION_ID=" + simulationId.value(),
                "NEIGHBORS=" + neighborsStr,
                "MW_MODE=udp-docker",
                "HOST_TEMPLATE={ID}",
                "NODE_COUNT=" + config.nodeCount()  // FIX: Use total node count from config
            )
            .withExposedPorts(
                ExposedPort.udp(9000),  // UDP for node-to-node
                ExposedPort.tcp(8080)   // HTTP for control API
            )
            .exec();

        // Connect container to network with alias (BEFORE starting)
        // This allows DNS resolution via NodeId (e.g., 'node-0')
        dockerClient.connectToNetworkCmd()
            .withContainerId(container.getId())
            .withNetworkId(networkName)
            .withContainerNetwork(new com.github.dockerjava.api.model.ContainerNetwork()
                .withAliases(nodeId.value()))
            .exec();

        // Start container
        dockerClient.startContainerCmd(container.getId()).exec();

        LOG.info("Started node container: {} (ID: {}, network alias: {})",
                 containerName, container.getId().substring(0, 12), nodeId.value());

        return container.getId();
    }

    /**
     * Stops and removes all node containers for a simulation.
     *
     * @param simulationId simulation identifier
     */
    public void stopAllNodes(SimulationId simulationId) {
        if (simulationId == null) {
            throw new IllegalArgumentException("simulationId must not be null");
        }

        String simIdPrefix = simulationId.value().substring(0, 8);
        String namePattern = "vsp-node-.*-" + simIdPrefix;

        LOG.info("Stopping all nodes for simulation {}", simulationId);

        try {
            List<Container> containers = dockerClient.listContainersCmd()
                .withShowAll(true)
                .exec();

            List<Container> matchingContainers = containers.stream()
                .filter(c -> c.getNames().length > 0 && c.getNames()[0].matches("/" + namePattern))
                .toList();

            for (Container container : matchingContainers) {
                try {
                    LOG.info("Stopping container: {} ({})",
                             container.getNames()[0], container.getId().substring(0, 12));

                    // Stop container with timeout
                    dockerClient.stopContainerCmd(container.getId())
                        .withTimeout(10)
                        .exec();

                    // Remove container
                    dockerClient.removeContainerCmd(container.getId())
                        .withForce(true)
                        .exec();

                    LOG.info("Removed container: {}", container.getNames()[0]);
                } catch (Exception e) {
                    LOG.warn("Failed to stop/remove container {}: {}",
                             container.getNames()[0], e.getMessage());
                }
            }

            LOG.info("Stopped {} containers for simulation {}",
                     matchingContainers.size(), simulationId);
        } catch (Exception e) {
            LOG.error("Failed to stop containers for simulation {}", simulationId, e);
        }
    }

    /**
     * Waits for all node containers to be running and ready.
     *
     * <p>This method checks two conditions:
     * <ul>
     *   <li>Container is in "running" state</li>
     *   <li>Spring Boot application inside container responds to health check</li>
     * </ul>
     *
     * @param containerIds map of NodeId to container ID
     * @param timeout maximum wait time
     * @return true if all containers are ready, false if timeout
     */
    public boolean waitForNodesReady(Map<NodeId, String> containerIds, Duration timeout) {
        if (containerIds == null || containerIds.isEmpty()) {
            return true;
        }

        LOG.info("Waiting for {} nodes to be ready (timeout: {}s)",
                 containerIds.size(), timeout.getSeconds());

        long deadline = System.currentTimeMillis() + timeout.toMillis();

        for (Map.Entry<NodeId, String> entry : containerIds.entrySet()) {
            NodeId nodeId = entry.getKey();
            String containerId = entry.getValue();

            // Wait for container to be running
            while (System.currentTimeMillis() < deadline) {
                try {
                    InspectContainerResponse info = dockerClient
                        .inspectContainerCmd(containerId)
                        .exec();

                    if (Boolean.TRUE.equals(info.getState().getRunning())) {
                        LOG.debug("Node {} container is running", nodeId);
                        break;
                    }

                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    LOG.warn("Interrupted while waiting for node {}", nodeId);
                    return false;
                } catch (Exception e) {
                    LOG.warn("Error checking node {} container status: {}", nodeId, e.getMessage());
                }
            }

            if (System.currentTimeMillis() >= deadline) {
                LOG.error("Timeout waiting for node {} container to start", nodeId);
                return false;
            }

            // Wait for Spring Boot application to be ready (health endpoint responding)
            String healthUrl = String.format("http://%s:8080/actuator/health", nodeId.value());
            LOG.debug("Checking health endpoint for node {}: {}", nodeId, healthUrl);

            while (System.currentTimeMillis() < deadline) {
                try {
                    java.net.http.HttpClient httpClient = java.net.http.HttpClient.newHttpClient();
                    java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                        .uri(java.net.URI.create(healthUrl))
                        .timeout(Duration.ofSeconds(2))
                        .GET()
                        .build();

                    java.net.http.HttpResponse<String> response = httpClient.send(
                        request,
                        java.net.http.HttpResponse.BodyHandlers.ofString()
                    );

                    if (response.statusCode() == 200) {
                        LOG.info("Node {} is healthy and ready", nodeId);
                        break;
                    }

                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    LOG.warn("Interrupted while waiting for node {} health check", nodeId);
                    return false;
                } catch (Exception e) {
                    // Expected during startup - application not ready yet
                    LOG.debug("Health check for node {} not yet available: {}", nodeId, e.getMessage());
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                }
            }

            if (System.currentTimeMillis() >= deadline) {
                LOG.error("Timeout waiting for node {} application to be ready", nodeId);
                return false;
            }
        }

        LOG.info("All {} nodes are ready", containerIds.size());
        return true;
    }

    /**
     * Gets environment variable or default value.
     *
     * @param key environment variable name
     * @param defaultValue default value if not set
     * @return environment variable value or default
     */
    private String getEnvOrDefault(String key, String defaultValue) {
        String value = System.getenv(key);
        return value != null && !value.isBlank() ? value : defaultValue;
    }
}
