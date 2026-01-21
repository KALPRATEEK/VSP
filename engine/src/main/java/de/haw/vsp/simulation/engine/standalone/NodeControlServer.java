package de.haw.vsp.simulation.engine.standalone;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import de.haw.vsp.simulation.core.NodeId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

/**
 * Simple HTTP server for receiving control commands from the backend.
 * 
 * Endpoints:
 * - GET /health - Health check
 * - POST /pause - Pause the node
 * - POST /resume - Resume the node
 * - POST /step - Execute one algorithm step
 */
public class NodeControlServer {
    
    private static final Logger LOG = LoggerFactory.getLogger(NodeControlServer.class);
    
    private final HttpServer server;
    private final NodeId nodeId;
    private final ControlCallback callback;
    
    public NodeControlServer(NodeId nodeId, int port, ControlCallback callback) throws IOException {
        this.nodeId = nodeId;
        this.callback = callback;
        
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        this.server.createContext("/health", this::handleHealth);
        this.server.createContext("/pause", this::handlePause);
        this.server.createContext("/resume", this::handleResume);
        this.server.createContext("/step", this::handleStep);
        this.server.setExecutor(Executors.newFixedThreadPool(2));
        
        LOG.info("Node control server initialized on port {} for node {}", port, nodeId);
    }
    
    /**
     * Start the control server.
     */
    public void start() {
        server.start();
        LOG.info("Node control server started for {}", nodeId);
    }
    
    /**
     * Stop the control server.
     */
    public void stop() {
        server.stop(1);
        LOG.info("Node control server stopped for {}", nodeId);
    }
    
    private void handleHealth(HttpExchange exchange) throws IOException {
        String response = "{\"status\":\"healthy\",\"nodeId\":\"" + nodeId.value() + "\"}";
        sendResponse(exchange, 200, response);
    }
    
    private void handlePause(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }
        
        try {
            callback.onPause();
            String response = "{\"status\":\"paused\",\"nodeId\":\"" + nodeId.value() + "\"}";
            sendResponse(exchange, 200, response);
            LOG.info("Node {} paused via control server", nodeId);
        } catch (Exception e) {
            LOG.error("Failed to pause node {}", nodeId, e);
            sendResponse(exchange, 500, "{\"error\":\"Failed to pause\"}");
        }
    }
    
    private void handleResume(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }
        
        try {
            callback.onResume();
            String response = "{\"status\":\"resumed\",\"nodeId\":\"" + nodeId.value() + "\"}";
            sendResponse(exchange, 200, response);
            LOG.info("Node {} resumed via control server", nodeId);
        } catch (Exception e) {
            LOG.error("Failed to resume node {}", nodeId, e);
            sendResponse(exchange, 500, "{\"error\":\"Failed to resume\"}");
        }
    }
    
    private void handleStep(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }
        
        try {
            callback.onStep();
            String response = "{\"status\":\"stepped\",\"nodeId\":\"" + nodeId.value() + "\"}";
            sendResponse(exchange, 200, response);
            LOG.info("Node {} stepped via control server", nodeId);
        } catch (Exception e) {
            LOG.error("Failed to step node {}", nodeId, e);
            sendResponse(exchange, 500, "{\"error\":\"Failed to step\"}");
        }
    }
    
    private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
    
    /**
     * Callback interface for control commands.
     */
    public interface ControlCallback {
        void onPause();
        void onResume();
        void onStep();
    }
}
