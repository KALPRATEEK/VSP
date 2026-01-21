package de.haw.vsp.simulation.control.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Service for managing distributed Docker containers.
 * 
 * This service allows starting/stopping Docker containers for distributed simulations
 * and tracking their status.
 */
@Service
public class DockerContainerService {
    
    private static final Logger LOG = LoggerFactory.getLogger(DockerContainerService.class);
    
    private final Map<String, ContainerInfo> runningContainers = new ConcurrentHashMap<>();
    private final String projectRoot;
    
    public DockerContainerService() {
        // Get project root directory
        // When running in Docker, we mount the project at /workspace (set via PROJECT_ROOT env var)
        String projectRootEnv = System.getenv("PROJECT_ROOT");
        LOG.info("PROJECT_ROOT env var: {}", projectRootEnv);
        this.projectRoot = projectRootEnv != null ? projectRootEnv : new File(".").getAbsolutePath();
        LOG.info("DockerContainerService initialized at: {}", projectRoot);
    }
    
    /**
     * Start distributed containers for a simulation.
     * 
     * @param nodeCount Number of nodes to start
     * @param topology Topology type (RING, LINE, GRID, RANDOM)
     * @param simulationId Simulation ID for tracking
     * @return true if containers were started successfully
     */
    public boolean startDistributedContainers(int nodeCount, String topology, String simulationId) {
        LOG.info("Starting {} distributed containers with {} topology for simulation {}", 
                 nodeCount, topology, simulationId);
        
        try {
            // Generate docker-compose file
            String composeFile = generateDockerCompose(nodeCount, topology, simulationId);
            
            // Stop any existing containers first
            stopAllContainers();
            
            // Start new containers using docker-compose
            // Use /workspace as working directory if it exists (mounted in Docker)
            String workspaceDir = "/workspace";
            File workDir = new File(workspaceDir).exists() ? new File(workspaceDir) : new File(projectRoot);
            
            ProcessBuilder pb = new ProcessBuilder(
                "docker-compose", "-f", composeFile, "up", "-d", "--build"
            );
            pb.directory(workDir);
            pb.redirectErrorStream(true);
            LOG.info("Executing docker-compose in directory: {}", workDir.getAbsolutePath());
            
            Process process = pb.start();
            
            // Read output
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                LOG.info("Docker: {}", line);
            }
            
            boolean success = process.waitFor(5, TimeUnit.MINUTES);
            int exitCode = process.exitValue();
            
            if (success && exitCode == 0) {
                LOG.info("Successfully started {} containers", nodeCount);
                
                // Track containers
                for (int i = 0; i < nodeCount; i++) {
                    String nodeName = "node-" + i;
                    runningContainers.put(nodeName, new ContainerInfo(
                        "vsp-node-" + i,
                        nodeName,
                        "RUNNING",
                        simulationId
                    ));
                }
                
                return true;
            } else {
                LOG.error("Failed to start containers. Exit code: {}", exitCode);
                return false;
            }
            
        } catch (Exception e) {
            LOG.error("Error starting distributed containers", e);
            return false;
        }
    }
    
    /**
     * Stop all running containers.
     */
    public void stopAllContainers() {
        LOG.info("Stopping all distributed containers...");
        
        try {
            // Use /workspace if it exists (mounted in Docker), otherwise use projectRoot
            String workspaceDir = "/workspace";
            File searchDir = new File(workspaceDir).exists() ? new File(workspaceDir) : new File(projectRoot);
            
            // Find all docker-compose files
            File[] composeFiles = searchDir.listFiles(
                (dir, name) -> name.startsWith("docker-compose-") && name.endsWith(".yml")
            );
            
            if (composeFiles != null) {
                for (File composeFile : composeFiles) {
                    ProcessBuilder pb = new ProcessBuilder(
                        "docker-compose", "-f", composeFile.getName(), "down"
                    );
                    pb.directory(searchDir);
                    pb.redirectErrorStream(true);
                    
                    Process process = pb.start();
                    process.waitFor(30, TimeUnit.SECONDS);
                }
            }
            
            runningContainers.clear();
            LOG.info("All containers stopped");
            
        } catch (Exception e) {
            LOG.error("Error stopping containers", e);
        }
    }
    
    /**
     * Get status of running containers.
     */
    public List<ContainerInfo> getContainerStatus() {
        return new ArrayList<>(runningContainers.values());
    }
    
    /**
     * Check if distributed mode is available (Docker is running).
     */
    public boolean isDistributedModeAvailable() {
        try {
            ProcessBuilder pb = new ProcessBuilder("docker", "ps");
            Process process = pb.start();
            boolean success = process.waitFor(5, TimeUnit.SECONDS);
            return success && process.exitValue() == 0;
        } catch (Exception e) {
            LOG.warn("Docker not available: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Generate a docker-compose file for the given configuration.
     */
    private String generateDockerCompose(int nodeCount, String topology, String simulationId) throws Exception {
        String filename = "docker-compose-generated-" + System.currentTimeMillis() + ".yml";
        // Always write to /workspace if it exists (mounted in Docker), otherwise use projectRoot
        String workspaceDir = "/workspace";
        String targetDir = new File(workspaceDir).exists() ? workspaceDir : projectRoot;
        File composeFile = new File(targetDir, filename);
        LOG.info("Generating docker-compose file in: {}", targetDir);
        
        StringBuilder yaml = new StringBuilder();
        yaml.append("version: '3.8'\n\n");
        yaml.append("services:\n");
        
        // Nodes (no backend needed - it's already running!)
        for (int i = 0; i < nodeCount; i++) {
            yaml.append("  node-").append(i).append(":\n");
            yaml.append("    build:\n");
            yaml.append("      context: /workspace\n");
            yaml.append("      dockerfile: Dockerfile.node\n");
            yaml.append("    container_name: vsp-node-").append(i).append("\n");
            yaml.append("    hostname: node-").append(i).append("\n");
            yaml.append("    environment:\n");
            yaml.append("      - MW_MODE=udp-docker\n");
            yaml.append("      - NODE_ID=node-").append(i).append("\n");
            yaml.append("      - UDP_PORT=9000\n");
            yaml.append("      - HOST_TEMPLATE={ID}\n");
            yaml.append("      - NODE_COUNT=").append(nodeCount).append("\n");
            yaml.append("      - TOPOLOGY=").append(topology).append("\n");
            yaml.append("      - SIMULATION_ID=").append(simulationId).append("\n");
            yaml.append("      - BACKEND_URL=http://vsp-backend:8080\n");
            yaml.append("      - CONTROL_PORT=").append(8000 + i).append("\n");
            yaml.append("      - QUEUE_OUT_CAPACITY=4096\n"); // Larger queues for 50+ nodes
            yaml.append("      - QUEUE_IN_CAPACITY=4096\n");
            yaml.append("    networks:\n");
            yaml.append("      - vsp-network\n\n");
        }
        
        // Network - use existing network from main docker-compose
        yaml.append("networks:\n");
        yaml.append("  vsp-network:\n");
        yaml.append("    external: true\n");
        yaml.append("    name: vsp_vsp-network\n");
        
        // Write to file
        try (FileWriter writer = new FileWriter(composeFile)) {
            writer.write(yaml.toString());
        }
        
        LOG.info("Generated docker-compose file: {}", filename);
        return filename;
    }
    
    /**
     * Container information.
     */
    public static class ContainerInfo {
        public final String containerName;
        public final String nodeId;
        public final String status;
        public final String simulationId;
        
        public ContainerInfo(String containerName, String nodeId, String status, String simulationId) {
            this.containerName = containerName;
            this.nodeId = nodeId;
            this.status = status;
            this.simulationId = simulationId;
        }
    }
    
    /**
     * List running containers.
     */
    public List<String> listRunningContainers() {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                "docker", "ps", "--format", "{{.Names}}"
            );
            pb.redirectErrorStream(true);
            
            Process process = pb.start();
            List<String> containerNames = new ArrayList<>();
            
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty()) {
                        containerNames.add(line);
                    }
                }
            }
            
            process.waitFor(10, TimeUnit.SECONDS);
            return containerNames;
            
        } catch (Exception e) {
            LOG.error("Failed to list running containers", e);
            return Collections.emptyList();
        }
    }
}
