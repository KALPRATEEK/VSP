package de.haw.vsp.simulation.engine;

import de.haw.vsp.simulation.core.NodeId;
import de.haw.vsp.simulation.core.SimulationParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Set;

/**
 * HTTP client for controlling remote node containers.
 *
 * <p>This client communicates with NodeApplication REST API running in Docker containers.
 * It provides methods to configure, start, and stop nodes remotely.
 *
 * <p>REST endpoints (on node container):
 * <ul>
 *   <li>POST /node/configure - Configure node with topology and algorithm</li>
 *   <li>POST /node/start - Start node execution</li>
 *   <li>POST /node/stop - Stop node execution</li>
 *   <li>GET /node/status - Get node status</li>
 *   <li>GET /node/health - Health check</li>
 * </ul>
 */
public class DistributedNodeClient {

    private static final Logger LOG = LoggerFactory.getLogger(DistributedNodeClient.class);

    private final RestTemplate restTemplate;
    private final String nodeBaseUrl;
    private final NodeId nodeId;

    /**
     * Creates a new distributed node client.
     *
     * @param nodeHost hostname of the node container (e.g., "node-0")
     * @param nodePort HTTP port of the node control API (default: 8080)
     * @throws IllegalArgumentException if nodeHost is null or blank
     */
    public DistributedNodeClient(String nodeHost, int nodePort) {
        if (nodeHost == null || nodeHost.isBlank()) {
            throw new IllegalArgumentException("nodeHost must not be null or blank");
        }
        if (nodePort <= 0 || nodePort > 65535) {
            throw new IllegalArgumentException("nodePort must be between 1 and 65535");
        }

        this.restTemplate = new RestTemplate();
        this.nodeBaseUrl = "http://" + nodeHost + ":" + nodePort;
        this.nodeId = new NodeId(nodeHost); // Assume hostname = nodeId

        LOG.debug("Created DistributedNodeClient for {} at {}", nodeId, nodeBaseUrl);
    }

    /**
     * Configures the node with topology and algorithm.
     *
     * @param nodeId node identifier (must match container)
     * @param neighbors set of neighbor node IDs
     * @param algorithmId algorithm identifier (e.g., "flooding")
     * @throws RuntimeException if configuration fails
     */
    public void configure(NodeId nodeId, Set<NodeId> neighbors, String algorithmId) {
        if (nodeId == null) {
            throw new IllegalArgumentException("nodeId must not be null");
        }
        if (neighbors == null) {
            throw new IllegalArgumentException("neighbors must not be null");
        }
        if (algorithmId == null || algorithmId.isBlank()) {
            throw new IllegalArgumentException("algorithmId must not be null or blank");
        }

        String url = nodeBaseUrl + "/node/configure";
        NodeConfigDto config = new NodeConfigDto(algorithmId, neighbors);

        try {
            LOG.debug("Configuring node {} with algorithm {} and {} neighbors",
                      nodeId, algorithmId, neighbors.size());

            ResponseEntity<String> response = restTemplate.postForEntity(url, config, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                LOG.info("Node {} configured successfully", nodeId);
            } else {
                LOG.warn("Node {} configuration returned status: {}", nodeId, response.getStatusCode());
            }
        } catch (RestClientException e) {
            LOG.error("Failed to configure node {}: {}", nodeId, e.getMessage());
            throw new RuntimeException("Failed to configure node " + nodeId, e);
        }
    }

    /**
     * Starts the node execution.
     *
     * @param parameters simulation parameters
     * @throws RuntimeException if start fails
     */
    public void start(SimulationParameters parameters) {
        if (parameters == null) {
            throw new IllegalArgumentException("parameters must not be null");
        }

        String url = nodeBaseUrl + "/node/start";

        try {
            LOG.debug("Starting node {}", nodeId);

            ResponseEntity<String> response = restTemplate.postForEntity(url, parameters, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                LOG.info("Node {} started successfully", nodeId);
            } else {
                LOG.warn("Node {} start returned status: {}", nodeId, response.getStatusCode());
            }
        } catch (RestClientException e) {
            LOG.error("Failed to start node {}: {}", nodeId, e.getMessage());
            throw new RuntimeException("Failed to start node " + nodeId, e);
        }
    }

    /**
     * Stops the node execution.
     *
     * @throws RuntimeException if stop fails
     */
    public void stop() {
        String url = nodeBaseUrl + "/node/stop";

        try {
            LOG.debug("Stopping node {}", nodeId);

            ResponseEntity<String> response = restTemplate.postForEntity(url, null, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                LOG.info("Node {} stopped successfully", nodeId);
            } else {
                LOG.warn("Node {} stop returned status: {}", nodeId, response.getStatusCode());
            }
        } catch (RestClientException e) {
            LOG.error("Failed to stop node {}: {}", nodeId, e.getMessage());
            // Don't throw exception on stop failure (best-effort cleanup)
        }
    }

    /**
     * Pauses the node execution.
     *
     * <p>Note: Pause functionality not yet implemented in NodeApplication.
     * This method is provided for future compatibility.
     *
     * @throws RuntimeException if pause fails
     */
    public void pause() {
        LOG.warn("Pause not yet implemented in NodeApplication for node {}", nodeId);
        // TODO: Implement when NodeApplication supports pause
    }

    /**
     * Resumes the node execution.
     *
     * <p>Note: Resume functionality not yet implemented in NodeApplication.
     * This method is provided for future compatibility.
     *
     * @throws RuntimeException if resume fails
     */
    public void resume() {
        LOG.warn("Resume not yet implemented in NodeApplication for node {}", nodeId);
        // TODO: Implement when NodeApplication supports resume
    }

    /**
     * Gets the current status of the node.
     *
     * @return node status DTO
     * @throws RuntimeException if status check fails
     */
    public NodeStatusDto getStatus() {
        String url = nodeBaseUrl + "/node/status";

        try {
            ResponseEntity<NodeStatusDto> response = restTemplate.getForEntity(url, NodeStatusDto.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return response.getBody();
            } else {
                LOG.warn("Node {} status check returned status: {}", nodeId, response.getStatusCode());
                return null;
            }
        } catch (RestClientException e) {
            LOG.error("Failed to get status for node {}: {}", nodeId, e.getMessage());
            return null;
        }
    }

    /**
     * Checks if the node is healthy.
     *
     * @return true if node responds to health check, false otherwise
     */
    public boolean isHealthy() {
        String url = nodeBaseUrl + "/node/health";

        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            return response.getStatusCode() == HttpStatus.OK;
        } catch (RestClientException e) {
            LOG.debug("Health check failed for node {}: {}", nodeId, e.getMessage());
            return false;
        }
    }

    /**
     * DTO for node configuration.
     *
     * <p>Matches NodeApplication.NodeConfig record.
     */
    public record NodeConfigDto(
        String algorithmId,
        Set<NodeId> neighbors
    ) {
        public NodeConfigDto {
            if (algorithmId == null || algorithmId.isBlank()) {
                throw new IllegalArgumentException("algorithmId must not be null or blank");
            }
            if (neighbors == null) {
                throw new IllegalArgumentException("neighbors must not be null");
            }
        }
    }

    /**
     * DTO for node status response.
     *
     * <p>Matches NodeApplication.NodeStatus record.
     */
    public record NodeStatusDto(
        String nodeId,
        String configurationState,
        String executionState,
        String algorithm,
        int neighborCount
    ) {}
}
