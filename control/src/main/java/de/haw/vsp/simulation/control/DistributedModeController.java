package de.haw.vsp.simulation.control;

import de.haw.vsp.simulation.control.service.DockerContainerService;
import de.haw.vsp.simulation.control.service.DistributedEventAggregator;
import de.haw.vsp.simulation.control.service.DistributedEventAggregator.NodeStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for managing distributed mode with Docker containers.
 * 
 * This allows the frontend to:
 * - Start distributed containers dynamically
 * - Check status of running containers
 * - Stop distributed mode
 */
@RestController
@RequestMapping("/api/distributed")
@CrossOrigin(origins = "*")
public class DistributedModeController {
    
    private static final Logger LOG = LoggerFactory.getLogger(DistributedModeController.class);
    
    @Autowired
    private DockerContainerService dockerService;
    
    @Autowired
    private DistributedEventAggregator eventAggregator;
    
    /**
     * Check if distributed mode is available.
     * 
     * GET /api/distributed/available
     */
    @GetMapping("/available")
    public ResponseEntity<Map<String, Object>> checkAvailability() {
        boolean available = dockerService.isDistributedModeAvailable();
        
        Map<String, Object> response = new HashMap<>();
        response.put("available", available);
        response.put("message", available 
            ? "Docker is running, distributed mode available" 
            : "Docker is not available");
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Start distributed containers.
     * 
     * POST /api/distributed/start
     * Body: { "nodeCount": 10, "topology": "RING" }
     */
    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> startDistributedMode(
            @RequestBody DistributedModeRequest request) {
        
        LOG.info("Starting distributed mode: {} nodes, {} topology", 
                 request.nodeCount, request.topology);
        
        if (request.nodeCount < 1 || request.nodeCount > 200) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", "Node count must be between 1 and 200"
            ));
        }
        
        String topology = request.topology != null ? request.topology : "RING";
        String simulationId = "dist-" + System.currentTimeMillis();
        
        boolean success = dockerService.startDistributedContainers(
            request.nodeCount, 
            topology, 
            simulationId
        );
        
        if (success) {
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Started " + request.nodeCount + " distributed containers",
                "simulationId", simulationId,
                "nodeCount", request.nodeCount,
                "topology", topology
            ));
        } else {
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", "Failed to start containers. Check backend logs."
            ));
        }
    }
    
    /**
     * Get status of distributed containers.
     * 
     * GET /api/distributed/status
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        List<DockerContainerService.ContainerInfo> containers = dockerService.getContainerStatus();
        
        return ResponseEntity.ok(Map.of(
            "running", !containers.isEmpty(),
            "containerCount", containers.size(),
            "containers", containers
        ));
    }
    
    /**
     * Stop all distributed containers.
     * 
     * POST /api/distributed/stop
     */
    @PostMapping("/stop")
    public ResponseEntity<Map<String, Object>> stopDistributedMode() {
        LOG.info("Stopping distributed mode");
        
        dockerService.stopAllContainers();
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "All distributed containers stopped"
        ));
    }
    
    /**
     * Receive events from distributed nodes.
     * 
     * POST /api/distributed/events
     */
    @PostMapping("/events")
    public ResponseEntity<Void> receiveEvents(@RequestBody Map<String, Object> update) {
        try {
            eventAggregator.processNodeUpdate(update);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            LOG.warn("Failed to process node update: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * Get aggregated metrics for a simulation.
     * 
     * GET /api/distributed/{simulationId}/metrics
     */
    @GetMapping("/{simulationId}/metrics")
    public ResponseEntity<Map<String, Object>> getMetrics(@PathVariable("simulationId") String simulationId) {
        try {
            Map<String, Object> metrics = eventAggregator.getMetrics(simulationId);
            Map<String, Object> nodeStatus = new HashMap<>();
            eventAggregator.getNodeStatus(simulationId).forEach((nodeId, status) -> {
                Map<String, Object> statusMap = new HashMap<>();
                statusMap.put("currentLeader", status.currentLeader());
                statusMap.put("messagesSent", status.messagesSent());
                statusMap.put("messagesReceived", status.messagesReceived());
                statusMap.put("lastUpdate", status.lastUpdateTimestamp());
                nodeStatus.put(nodeId, statusMap);
            });
            metrics.put("nodes", nodeStatus);
            return ResponseEntity.ok(metrics);
        } catch (Exception e) {
            LOG.error("Failed to get metrics for simulation {}", simulationId, e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get recent events for a simulation.
     * 
     * GET /api/distributed/{simulationId}/events?limit=100
     */
    @GetMapping("/{simulationId}/events")
    public ResponseEntity<List<Map<String, Object>>> getEvents(
            @PathVariable("simulationId") String simulationId,
            @RequestParam(defaultValue = "100") int limit) {
        try {
            List<Map<String, Object>> events = eventAggregator.getEvents(simulationId, limit);
            return ResponseEntity.ok(events);
        } catch (Exception e) {
            LOG.error("Failed to get events for simulation {}", simulationId, e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get visualization snapshot for a simulation.
     * 
     * GET /api/distributed/{simulationId}/visualization
     */
    @GetMapping("/{simulationId}/visualization")
    public ResponseEntity<Map<String, Object>> getVisualization(@PathVariable("simulationId") String simulationId) {
        try {
            Map<String, NodeStatus> nodeStatus = eventAggregator.getNodeStatus(simulationId);
            Map<String, Object> metrics = eventAggregator.getMetrics(simulationId);
            
            // Build node data for visualization
            List<Map<String, Object>> nodes = new ArrayList<>();
            nodeStatus.forEach((nodeIdStr, status) -> {
                Map<String, Object> node = new HashMap<>();
                node.put("id", nodeIdStr);
                node.put("currentLeader", status.currentLeader());
                node.put("messagesSent", status.messagesSent());
                node.put("messagesReceived", status.messagesReceived());
                node.put("isLeader", nodeIdStr.equals(metrics.get("consensusLeader")));
                nodes.add(node);
            });
            
            // Build edges from topology (assume RING for now - can be enhanced)
            List<Map<String, Object>> edges = new ArrayList<>();
            int nodeCount = nodes.size();
            for (int i = 0; i < nodeCount; i++) {
                Map<String, Object> edge = new HashMap<>();
                edge.put("from", "node-" + i);
                edge.put("to", "node-" + ((i + 1) % nodeCount)); // Ring topology
                edges.add(edge);
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("nodes", nodes);
            response.put("edges", edges);
            response.put("metrics", metrics);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            LOG.error("Failed to get visualization for simulation {}", simulationId, e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Pause all nodes in a distributed simulation.
     * 
     * POST /api/distributed/{simulationId}/pause
     */
    @PostMapping("/{simulationId}/pause")
    public ResponseEntity<Map<String, Object>> pauseSimulation(@PathVariable("simulationId") String simulationId) {
        try {
            int nodeCount = getNodeCountForSimulation(simulationId);
            int successCount = sendControlCommandToAllNodes(nodeCount, "pause");
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("nodesAffected", successCount);
            response.put("message", "Paused " + successCount + " nodes");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            LOG.error("Failed to pause simulation {}", simulationId, e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
    
    /**
     * Resume all nodes in a distributed simulation.
     * 
     * POST /api/distributed/{simulationId}/resume
     */
    @PostMapping("/{simulationId}/resume")
    public ResponseEntity<Map<String, Object>> resumeSimulation(@PathVariable("simulationId") String simulationId) {
        try {
            int nodeCount = getNodeCountForSimulation(simulationId);
            int successCount = sendControlCommandToAllNodes(nodeCount, "resume");
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("nodesAffected", successCount);
            response.put("message", "Resumed " + successCount + " nodes");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            LOG.error("Failed to resume simulation {}", simulationId, e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
    
    /**
     * Send a control command to all nodes.
     */
    private int sendControlCommandToAllNodes(int nodeCount, String command) {
        int successCount = 0;
        for (int i = 0; i < nodeCount; i++) {
            try {
                String nodeHost = "vsp-node-" + i;
                int controlPort = 8000 + i;
                String url = "http://" + nodeHost + ":" + controlPort + "/" + command;
                
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) 
                    new java.net.URL(url).openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(2000);
                conn.setReadTimeout(2000);
                
                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    successCount++;
                }
                conn.disconnect();
            } catch (Exception e) {
                LOG.warn("Failed to send {} command to node-{}: {}", command, i, e.getMessage());
            }
        }
        return successCount;
    }
    
    /**
     * Get node count for a simulation based on running containers.
     */
    private int getNodeCountForSimulation(String simulationId) {
        // Extract node count from docker ps
        try {
            java.util.List<String> containerList = dockerService.listRunningContainers();
            long nodeCount = containerList.stream()
                .filter(name -> name.startsWith("vsp-node-"))
                .count();
            return (int) nodeCount;
        } catch (Exception e) {
            LOG.warn("Could not determine node count, using default 10", e);
            return 10;
        }
    }
    
    /**
     * Request body for starting distributed mode.
     */
    public static class DistributedModeRequest {
        public int nodeCount;
        public String topology;
    }
}
