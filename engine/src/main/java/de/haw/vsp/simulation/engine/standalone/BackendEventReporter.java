package de.haw.vsp.simulation.engine.standalone;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.haw.vsp.simulation.core.NodeId;
import de.haw.vsp.simulation.core.SimulationMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.concurrent.*;

/**
 * Reports node events to the backend via HTTP REST API.
 * 
 * Sends:
 * - Node state changes (leader election results)
 * - Message events (sent/received)
 * - Metrics (message count, etc.)
 */
public class BackendEventReporter {
    
    private static final Logger LOG = LoggerFactory.getLogger(BackendEventReporter.class);
    
    private final String backendUrl;
    private final String simulationId;
    private final NodeId nodeId;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    
    private final Queue<Map<String, Object>> eventQueue = new ConcurrentLinkedQueue<>();
    private volatile String currentLeader = null;
    private volatile int messagesSent = 0;
    private volatile int messagesReceived = 0;
    
    public BackendEventReporter(String backendUrl, String simulationId, NodeId nodeId) {
        this.backendUrl = backendUrl;
        this.simulationId = simulationId;
        this.nodeId = nodeId;
        
        // Start periodic event reporting (every 2 seconds)
        scheduler.scheduleAtFixedRate(this::flushEvents, 2, 2, TimeUnit.SECONDS);
        
        LOG.info("BackendEventReporter initialized for node {} (simulation: {})", nodeId, simulationId);
    }
    
    /**
     * Report that this node has elected a new leader.
     */
    public void reportLeaderChange(String leaderId) {
        if (!Objects.equals(currentLeader, leaderId)) {
            currentLeader = leaderId;
            Map<String, Object> event = new HashMap<>();
            event.put("type", "LEADER_CHANGE");
            event.put("nodeId", nodeId.value());
            event.put("leaderId", leaderId);
            event.put("timestamp", System.currentTimeMillis());
            eventQueue.offer(event);
            LOG.debug("Node {} reported leader change: {}", nodeId, leaderId);
        }
    }
    
    /**
     * Report a message sent event.
     */
    public void reportMessageSent(SimulationMessage message) {
        messagesSent++;
        Map<String, Object> event = new HashMap<>();
        event.put("type", "MESSAGE_SENT");
        event.put("nodeId", nodeId.value());
        event.put("receiver", message.receiver().value());
        event.put("messageType", message.messageType().toString());
        event.put("timestamp", System.currentTimeMillis());
        eventQueue.offer(event);
    }
    
    /**
     * Report a message received event.
     */
    public void reportMessageReceived(SimulationMessage message) {
        messagesReceived++;
        Map<String, Object> event = new HashMap<>();
        event.put("type", "MESSAGE_RECEIVED");
        event.put("nodeId", nodeId.value());
        event.put("sender", message.sender().value());
        event.put("messageType", message.messageType().toString());
        event.put("timestamp", System.currentTimeMillis());
        eventQueue.offer(event);
    }
    
    /**
     * Flush all queued events to the backend.
     */
    private void flushEvents() {
        if (eventQueue.isEmpty() && messagesSent == 0 && messagesReceived == 0) {
            return; // Nothing to report
        }
        
        try {
            // Collect all events
            List<Map<String, Object>> events = new ArrayList<>();
            while (!eventQueue.isEmpty()) {
                Map<String, Object> event = eventQueue.poll();
                if (event != null) {
                    events.add(event);
                }
            }
            
            // Create status update
            Map<String, Object> statusUpdate = new HashMap<>();
            statusUpdate.put("simulationId", simulationId);
            statusUpdate.put("nodeId", nodeId.value());
            statusUpdate.put("currentLeader", currentLeader);
            statusUpdate.put("messagesSent", messagesSent);
            statusUpdate.put("messagesReceived", messagesReceived);
            statusUpdate.put("events", events);
            statusUpdate.put("timestamp", System.currentTimeMillis());
            
            // Send to backend
            sendToBackend(statusUpdate);
            
        } catch (Exception e) {
            LOG.warn("Failed to flush events to backend: {}", e.getMessage());
        }
    }
    
    /**
     * Send data to backend via HTTP POST.
     */
    private void sendToBackend(Map<String, Object> data) {
        try {
            URL url = new URL(backendUrl + "/api/distributed/events");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            
            String jsonData = objectMapper.writeValueAsString(data);
            
            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonData.getBytes("UTF-8"));
                os.flush();
            }
            
            int responseCode = conn.getResponseCode();
            if (responseCode == 200 || responseCode == 201 || responseCode == 204) {
                LOG.trace("Successfully sent {} events to backend", data.get("events") != null ? ((List<?>)data.get("events")).size() : 0);
            } else {
                LOG.warn("Backend returned status code: {}", responseCode);
            }
            
            conn.disconnect();
            
        } catch (Exception e) {
            LOG.trace("Could not send events to backend (backend might not be ready yet): {}", e.getMessage());
        }
    }
    
    /**
     * Shutdown the reporter.
     */
    public void shutdown() {
        flushEvents(); // Final flush
        scheduler.shutdown();
        LOG.info("BackendEventReporter shut down for node {}", nodeId);
    }
}
