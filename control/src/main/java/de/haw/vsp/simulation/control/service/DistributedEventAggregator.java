package de.haw.vsp.simulation.control.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Aggregates events from distributed simulation nodes.
 * 
 * Collects:
 * - Node status updates
 * - Message events (sent/received)
 * - Leader election results
 * - Metrics
 */
@Service
public class DistributedEventAggregator {
    
    private static final Logger LOG = LoggerFactory.getLogger(DistributedEventAggregator.class);
    
    // Map: simulationId -> NodeStatus
    private final Map<String, Map<String, NodeStatus>> nodeStatusBySimulation = new ConcurrentHashMap<>();
    
    // Map: simulationId -> Events
    private final Map<String, List<Map<String, Object>>> eventsBySimulation = new ConcurrentHashMap<>();
    
    // Max events to keep per simulation
    private static final int MAX_EVENTS = 1000;
    
    /**
     * Process a status update from a node.
     */
    public void processNodeUpdate(Map<String, Object> update) {
        String simulationId = (String) update.get("simulationId");
        String nodeId = (String) update.get("nodeId");
        
        if (simulationId == null || nodeId == null) {
            LOG.warn("Received invalid node update (missing simulationId or nodeId)");
            return;
        }
        
        // Update node status
        nodeStatusBySimulation
            .computeIfAbsent(simulationId, k -> new ConcurrentHashMap<>())
            .put(nodeId, new NodeStatus(
                nodeId,
                (String) update.get("currentLeader"),
                (Integer) update.getOrDefault("messagesSent", 0),
                (Integer) update.getOrDefault("messagesReceived", 0),
                (Long) update.getOrDefault("timestamp", System.currentTimeMillis())
            ));
        
        // Store events
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> events = (List<Map<String, Object>>) update.get("events");
        if (events != null && !events.isEmpty()) {
            List<Map<String, Object>> simEvents = eventsBySimulation
                .computeIfAbsent(simulationId, k -> new CopyOnWriteArrayList<>());
            
            simEvents.addAll(events);
            
            // Limit event history
            if (simEvents.size() > MAX_EVENTS) {
                simEvents.subList(0, simEvents.size() - MAX_EVENTS).clear();
            }
            
            LOG.debug("Received {} events from node {} (simulation: {})", events.size(), nodeId, simulationId);
        }
    }
    
    /**
     * Get current status of all nodes for a simulation.
     */
    public Map<String, NodeStatus> getNodeStatus(String simulationId) {
        return new HashMap<>(nodeStatusBySimulation.getOrDefault(simulationId, Collections.emptyMap()));
    }
    
    /**
     * Get recent events for a simulation.
     */
    public List<Map<String, Object>> getEvents(String simulationId, int limit) {
        List<Map<String, Object>> events = eventsBySimulation.getOrDefault(simulationId, Collections.emptyList());
        int fromIndex = Math.max(0, events.size() - limit);
        return new ArrayList<>(events.subList(fromIndex, events.size()));
    }
    
    /**
     * Get all events for a simulation.
     */
    public List<Map<String, Object>> getAllEvents(String simulationId) {
        return new ArrayList<>(eventsBySimulation.getOrDefault(simulationId, Collections.emptyList()));
    }
    
    /**
     * Get aggregated metrics for a simulation.
     */
    public Map<String, Object> getMetrics(String simulationId) {
        Map<String, NodeStatus> nodes = nodeStatusBySimulation.getOrDefault(simulationId, Collections.emptyMap());
        
        int totalMessagesSent = nodes.values().stream()
            .mapToInt(NodeStatus::messagesSent)
            .sum();
        
        int totalMessagesReceived = nodes.values().stream()
            .mapToInt(NodeStatus::messagesReceived)
            .sum();
        
        // Count leader consensus
        Map<String, Long> leaderVotes = nodes.values().stream()
            .map(NodeStatus::currentLeader)
            .filter(Objects::nonNull)
            .collect(java.util.stream.Collectors.groupingBy(
                leader -> leader,
                java.util.stream.Collectors.counting()
            ));
        
        String consensusLeader = leaderVotes.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(null);
        
        boolean hasConsensus = consensusLeader != null && 
            leaderVotes.get(consensusLeader) == nodes.size();
        
        // SC6: Validate that the elected leader has the maximum NodeId
        String sc6Warning = null;
        if (hasConsensus && consensusLeader != null && !nodes.isEmpty()) {
            String maxNodeId = nodes.keySet().stream()
                .max(String::compareTo)
                .orElse(null);
            
            if (maxNodeId != null && !consensusLeader.equals(maxNodeId)) {
                sc6Warning = String.format(
                    "⚠️ SC6 VIOLATION: Elected leader '%s' is NOT the maximum NodeId! Expected: '%s'",
                    consensusLeader, maxNodeId
                );
                LOG.warn(sc6Warning);
            }
        }
        
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("nodeCount", nodes.size());
        metrics.put("totalMessagesSent", totalMessagesSent);
        metrics.put("totalMessagesReceived", totalMessagesReceived);
        metrics.put("consensusLeader", consensusLeader);
        metrics.put("hasConsensus", hasConsensus);
        metrics.put("leaderVotes", leaderVotes);
        metrics.put("nodes", nodes);
        if (sc6Warning != null) {
            metrics.put("sc6Warning", sc6Warning);
        }
        
        return metrics;
    }
    
    /**
     * Clear all data for a simulation.
     */
    public void clearSimulation(String simulationId) {
        nodeStatusBySimulation.remove(simulationId);
        eventsBySimulation.remove(simulationId);
        LOG.info("Cleared aggregated data for simulation: {}", simulationId);
    }
    
    /**
     * Node status data class.
     */
    public record NodeStatus(
        String nodeId,
        String currentLeader,
        int messagesSent,
        int messagesReceived,
        long lastUpdateTimestamp
    ) {}
}
