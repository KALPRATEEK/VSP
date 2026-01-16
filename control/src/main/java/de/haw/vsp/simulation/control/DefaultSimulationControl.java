package de.haw.vsp.simulation.control;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import de.haw.vsp.simulation.core.*;
import de.haw.vsp.simulation.engine.DefaultSimulationEngine;
import de.haw.vsp.simulation.engine.SimulationEngine;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Default implementation of SimulationControl.
 *
 * Manages multiple simulation instances and provides a facade for UI interactions.
 * Each simulation is identified by a unique SimulationId.
 */
public class DefaultSimulationControl implements SimulationControl {

    private final Map<SimulationId, SimulationEngine> simulations;
    private final Map<SimulationId, List<SimulationEvent>> eventStreams;
    private final Map<SimulationId, String> leaderIds;
    private final Map<SimulationId, SimulationEventBus> eventBuses;
    private final Map<SimulationId, NetworkConfig> networkConfigs;
    private final Map<SimulationId, String> algorithmIds;
    private final Map<SimulationId, SimulationParameters> simulationParameters;
    private final AtomicLong simulationCounter;

    /**
     * Creates a new simulation control instance.
     */
    public DefaultSimulationControl() {
        this.simulations = new ConcurrentHashMap<>();
        this.eventStreams = new ConcurrentHashMap<>();
        this.leaderIds = new ConcurrentHashMap<>();
        this.eventBuses = new ConcurrentHashMap<>();
        this.networkConfigs = new ConcurrentHashMap<>();
        this.algorithmIds = new ConcurrentHashMap<>();
        this.simulationParameters = new ConcurrentHashMap<>();
        this.simulationCounter = new AtomicLong(0);
    }

    @Override
    public SimulationId initializeNetwork(NetworkConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }

        SimulationId simulationId = new SimulationId("sim-" + simulationCounter.incrementAndGet());
        SimulationEngine engine = new DefaultSimulationEngine();
        engine.createEngineAndNodes(config);

        // Set up event publisher to capture events
        InMemorySimulationEventBus eventBus = new InMemorySimulationEventBus();
        engine.setEventPublisher(eventBus);

        // Subscribe to events to build event stream
        eventBus.subscribe(EventType.LEADER_ELECTED, event -> {
            // Extract leader ID from event if possible
            String leaderId = extractLeaderIdFromEvent(event);
            if (leaderId != null) {
                leaderIds.put(simulationId, leaderId);
            }
            // Also add to event stream
            // Use CopyOnWriteArrayList for thread-safe concurrent writes and safe iteration
            eventStreams.computeIfAbsent(simulationId, k -> new CopyOnWriteArrayList<>()).add(event);
        });

        // Subscribe to all other events to build event stream
        for (EventType eventType : EventType.values()) {
            if (eventType != EventType.LEADER_ELECTED) {
                eventBus.subscribe(eventType, event -> {
                    // Use CopyOnWriteArrayList for thread-safe concurrent writes and safe iteration
                    eventStreams.computeIfAbsent(simulationId, k -> new CopyOnWriteArrayList<>()).add(event);
                });
            }
        }

        // Note: eventStreams is initialized lazily via computeIfAbsent in event listeners
        // No need to put an empty list here, as it would overwrite any events published
        // between listener registration and this point, causing a race condition.
        
        // IMPORTANT: Add eventBus BEFORE adding simulation to prevent race condition:
        // If another thread calls registerVisualizationListener between these operations,
        // it would find the simulation in 'simulations' but not in 'eventBuses', causing
        // an IllegalStateException. By adding eventBus first, registerVisualizationListener
        // will either find both (if called after both operations) or neither (if called
        // before both operations), which is the correct behavior.
        eventBuses.put(simulationId, eventBus);
        networkConfigs.put(simulationId, config);
        simulations.put(simulationId, engine);

        return simulationId;
    }

    @Override
    public void selectAlgorithm(SimulationId simulationId, String algorithmId) {
        if (simulationId == null) {
            throw new IllegalArgumentException("simulationId must not be null");
        }
        if (algorithmId == null || algorithmId.isBlank()) {
            throw new IllegalArgumentException("algorithmId must not be null or blank");
        }

        SimulationEngine engine = simulations.get(simulationId);
        if (engine == null) {
            throw new IllegalStateException("Simulation not found: " + simulationId);
        }

        engine.configureAlgorithm(algorithmId);
        algorithmIds.put(simulationId, algorithmId);
    }

    @Override
    public void startSimulation(SimulationId simulationId, SimulationParameters parameters) {
        if (simulationId == null) {
            throw new IllegalArgumentException("simulationId must not be null");
        }
        if (parameters == null) {
            throw new IllegalArgumentException("parameters must not be null");
        }

        SimulationEngine engine = simulations.get(simulationId);
        if (engine == null) {
            throw new IllegalStateException("Simulation not found: " + simulationId);
        }

        engine.startSimulation(parameters);
        simulationParameters.put(simulationId, parameters);
    }

    @Override
    public void pauseSimulation(SimulationId simulationId) {
        if (simulationId == null) {
            throw new IllegalArgumentException("simulationId must not be null");
        }

        SimulationEngine engine = simulations.get(simulationId);
        if (engine == null) {
            throw new IllegalStateException("Simulation not found: " + simulationId);
        }

        engine.pauseSimulation();
    }

    @Override
    public void resumeSimulation(SimulationId simulationId) {
        if (simulationId == null) {
            throw new IllegalArgumentException("simulationId must not be null");
        }

        SimulationEngine engine = simulations.get(simulationId);
        if (engine == null) {
            throw new IllegalStateException("Simulation not found: " + simulationId);
        }

        engine.resumeSimulation();
    }

    @Override
    public void stopSimulation(SimulationId simulationId) {
        if (simulationId == null) {
            throw new IllegalArgumentException("simulationId must not be null");
        }

        SimulationEngine engine = simulations.get(simulationId);
        if (engine == null) {
            throw new IllegalStateException("Simulation not found: " + simulationId);
        }

        engine.stopSimulation();
        simulations.remove(simulationId);
        eventStreams.remove(simulationId);
        leaderIds.remove(simulationId);
        eventBuses.remove(simulationId);
        networkConfigs.remove(simulationId);
        algorithmIds.remove(simulationId);
        simulationParameters.remove(simulationId);
    }

    @Override
    public VisualizationSnapshot getCurrentVisualization(SimulationId simulationId) {
        if (simulationId == null) {
            throw new IllegalArgumentException("simulationId must not be null");
        }

        SimulationEngine engine = simulations.get(simulationId);
        if (engine == null) {
            throw new IllegalStateException("Simulation not found: " + simulationId);
        }

        // Get topology from engine
        Map<NodeId, Set<NodeId>> topology = engine.getTopology();

        // Build node states from topology and event stream
        List<VisualizationSnapshot.NodeState> nodeStates = new ArrayList<>();
        String currentLeaderId = leaderIds.get(simulationId);
        List<SimulationEvent> events = eventStreams.getOrDefault(simulationId, Collections.emptyList());

        // Determine node states from events and topology
        Set<String> startedNodes = new HashSet<>();
        for (SimulationEvent event : events) {
            if (event.type() == EventType.STATE_CHANGED && 
                event.payloadSummary() != null &&
                event.payloadSummary().contains("Node started")) {
                startedNodes.add(event.nodeId());
            }
        }

        // Create node states
        for (Map.Entry<NodeId, Set<NodeId>> entry : topology.entrySet()) {
            NodeId nodeId = entry.getKey();
            String nodeIdString = nodeId.value();
            String state = startedNodes.contains(nodeIdString) ? "RUNNING" : "INITIALIZED";
            boolean isLeader = currentLeaderId != null && currentLeaderId.equals(nodeIdString);

            nodeStates.add(new VisualizationSnapshot.NodeState(
                    nodeIdString,
                    state,
                    isLeader
            ));
        }

        // Build topology map (String -> Set<String>)
        Map<String, Set<String>> topologyMap = topology.entrySet().stream()
                .collect(Collectors.toMap(
                        e -> e.getKey().value(),
                        e -> e.getValue().stream()
                                .map(NodeId::value)
                                .collect(Collectors.toSet())
                ));

        return new VisualizationSnapshot(
                Collections.unmodifiableList(nodeStates),
                Collections.unmodifiableMap(topologyMap),
                System.currentTimeMillis()
        );
    }

    @Override
    public void registerVisualizationListener(SimulationId simulationId, VisualizationListener listener) {
        if (simulationId == null) {
            throw new IllegalArgumentException("simulationId must not be null");
        }
        if (listener == null) {
            throw new IllegalArgumentException("listener must not be null");
        }

        SimulationEventBus eventBus = eventBuses.get(simulationId);
        if (eventBus == null) {
            throw new IllegalStateException("Simulation not found: " + simulationId);
        }

        // Subscribe listener to all relevant event types for visualization
        // Visualization listeners typically want to see all events
        for (EventType eventType : EventType.values()) {
            // Convert VisualizationListener to SimulationEventListener
            // Since both interfaces have the same signature, we can use a lambda
            eventBus.subscribe(eventType, listener::onEvent);
        }
    }

    @Override
    public MetricsSnapshot getMetrics(SimulationId simulationId) {
        if (simulationId == null) {
            throw new IllegalArgumentException("simulationId must not be null");
        }

        SimulationEngine engine = simulations.get(simulationId);
        if (engine == null) {
            throw new IllegalStateException("Simulation not found: " + simulationId);
        }

        // Get metrics from engine
        MetricsSnapshot engineMetrics = engine.getMetrics();
        
        // Override leaderId with the one from event stream if available
        // This ensures the metrics reflect the actual state from events
        String leaderIdFromEvents = leaderIds.get(simulationId);
        if (leaderIdFromEvents != null && !leaderIdFromEvents.equals(engineMetrics.leaderId())) {
            // Create new snapshot with leader ID from events
            return new MetricsSnapshot(
                    engineMetrics.simulatedTime(),
                    engineMetrics.realTimeMillis(),
                    engineMetrics.messageCount(),
                    engineMetrics.rounds(),
                    engineMetrics.converged(),
                    leaderIdFromEvents
            );
        }
        
        return engineMetrics;
    }

    @Override
    public SimulationId loadConfig(SimulationConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }

        // Load configuration: equivalent to initializeNetwork + selectAlgorithm
        // Step 1: Initialize network (equivalent to initializeNetwork)
        SimulationId simulationId = initializeNetwork(config.networkConfig());

        // Step 2: Select algorithm (equivalent to selectAlgorithm)
        selectAlgorithm(simulationId, config.algorithmId());

        // Step 3: Store simulation parameters (for later use when starting simulation)
        simulationParameters.put(simulationId, config.defaultParameters());

        return simulationId;
    }

    @Override
    public SimulationConfig getCurrentConfig(SimulationId simulationId) {
        if (simulationId == null) {
            throw new IllegalArgumentException("simulationId must not be null");
        }

        SimulationEngine engine = simulations.get(simulationId);
        if (engine == null) {
            throw new IllegalStateException("Simulation not found: " + simulationId);
        }

        // Get stored configuration components
        NetworkConfig networkConfig = networkConfigs.get(simulationId);
        if (networkConfig == null) {
            throw new IllegalStateException("Network configuration not found for simulation: " + simulationId);
        }

        // Get algorithm ID (may be null if not yet selected)
        String algorithmId = algorithmIds.get(simulationId);
        if (algorithmId == null || algorithmId.isBlank()) {
            // Use default algorithm if not yet selected
            algorithmId = "flooding-leader-election";
        }

        // Get simulation parameters (may be null if simulation not yet started)
        SimulationParameters parameters = simulationParameters.get(simulationId);
        if (parameters == null) {
            // Use default parameters if simulation not yet started
            parameters = new SimulationParameters(1L, 100, 0);
        }

        return new SimulationConfig(networkConfig, algorithmId, parameters);
    }

    @Override
    public byte[] exportRunData(SimulationId simulationId, String format) {
        if (simulationId == null) {
            throw new IllegalArgumentException("simulationId must not be null");
        }
        if (format == null || format.isBlank()) {
            throw new IllegalArgumentException("format must not be null or blank");
        }

        SimulationEngine engine = simulations.get(simulationId);
        if (engine == null) {
            throw new IllegalStateException("Simulation not found: " + simulationId);
        }

        // Get event stream and metrics
        List<SimulationEvent> events = eventStreams.getOrDefault(simulationId, Collections.emptyList());
        MetricsSnapshot metrics = getMetrics(simulationId);

        // Export based on format
        String formatUpper = format.toUpperCase().trim();
        return switch (formatUpper) {
            case "JSON" -> exportAsJson(events, metrics);
            case "CSV" -> exportAsCsv(events, metrics);
            default -> throw new IllegalArgumentException("Unsupported export format: " + format + ". Supported formats: JSON, CSV");
        };
    }

    /**
     * Exports run data as JSON.
     *
     * @param events  list of simulation events
     * @param metrics current metrics snapshot
     * @return JSON bytes
     */
    private byte[] exportAsJson(List<SimulationEvent> events, MetricsSnapshot metrics) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.enable(SerializationFeature.INDENT_OUTPUT);

            Map<String, Object> exportData = new LinkedHashMap<>();
            exportData.put("events", events);
            exportData.put("metrics", metrics);

            String json = objectMapper.writeValueAsString(exportData);
            return json.getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed to export as JSON: " + e.getMessage(), e);
        }
    }

    /**
     * Exports run data as CSV.
     *
     * @param events  list of simulation events
     * @param metrics current metrics snapshot
     * @return CSV bytes
     */
    private byte[] exportAsCsv(List<SimulationEvent> events, MetricsSnapshot metrics) {
        StringBuilder csv = new StringBuilder();

        // CSV Header for Events
        csv.append("=== EVENTS ===\n");
        csv.append("timestamp,type,nodeId,peerId,payloadSummary\n");

        // CSV Events
        for (SimulationEvent event : events) {
            csv.append(event.timestamp()).append(",");
            csv.append(escapeCsvField(event.type().name())).append(",");
            csv.append(escapeCsvField(event.nodeId())).append(",");
            csv.append(escapeCsvField(event.peerId())).append(",");
            csv.append(escapeCsvField(event.payloadSummary())).append("\n");
        }

        // CSV Header for Metrics
        csv.append("\n=== METRICS ===\n");
        csv.append("simulatedTime,realTimeMillis,messageCount,rounds,converged,leaderId\n");

        // CSV Metrics
        csv.append(metrics.simulatedTime()).append(",");
        csv.append(metrics.realTimeMillis()).append(",");
        csv.append(metrics.messageCount()).append(",");
        csv.append(metrics.rounds()).append(",");
        csv.append(metrics.converged()).append(",");
        csv.append(escapeCsvField(metrics.leaderId())).append("\n");

        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Escapes a CSV field value.
     *
     * @param field the field value (may be null)
     * @return escaped CSV field
     */
    private String escapeCsvField(String field) {
        if (field == null) {
            return "";
        }
        // If field contains comma, quote, or newline, wrap in quotes and escape quotes
        if (field.contains(",") || field.contains("\"") || field.contains("\n")) {
            return "\"" + field.replace("\"", "\"\"") + "\"";
        }
        return field;
    }

    @Override
    public List<String> getLogs(SimulationId simulationId, String filter) {
        if (simulationId == null) {
            throw new IllegalArgumentException("simulationId must not be null");
        }

        SimulationEngine engine = simulations.get(simulationId);
        if (engine == null) {
            throw new IllegalStateException("Simulation not found: " + simulationId);
        }

        // Get event stream
        List<SimulationEvent> events = eventStreams.getOrDefault(simulationId, Collections.emptyList());

        // Convert events to log entries, sorted by timestamp
        List<String> logs = events.stream()
                .sorted(Comparator.comparingLong(SimulationEvent::timestamp))
                .filter(event -> matchesFilter(event, filter))
                .map(this::formatLogEntry)
                .collect(Collectors.toList());

        return Collections.unmodifiableList(logs);
    }

    /**
     * Checks if an event matches the given filter.
     *
     * @param event the event to check
     * @param filter the filter string (may be null)
     * @return true if event matches filter, false otherwise
     */
    private boolean matchesFilter(SimulationEvent event, String filter) {
        if (filter == null || filter.isBlank()) {
            return true; // No filter means match all
        }

        String filterLower = filter.toLowerCase().trim();

        // Check event type
        if (event.type().name().toLowerCase().contains(filterLower)) {
            return true;
        }

        // Check node ID
        if (event.nodeId().toLowerCase().contains(filterLower)) {
            return true;
        }

        // Check peer ID
        if (event.peerId() != null && event.peerId().toLowerCase().contains(filterLower)) {
            return true;
        }

        // Check payload summary
        if (event.payloadSummary().toLowerCase().contains(filterLower)) {
            return true;
        }

        return false;
    }

    /**
     * Formats a simulation event as a log entry string.
     *
     * Format: "[timestamp] [type] nodeId: payloadSummary"
     * For events with peerId: "[timestamp] [type] nodeId -> peerId: payloadSummary"
     *
     * @param event the event to format
     * @return formatted log entry string
     */
    private String formatLogEntry(SimulationEvent event) {
        StringBuilder log = new StringBuilder();
        log.append("[").append(event.timestamp()).append("] ");
        log.append("[").append(event.type().name()).append("] ");
        log.append(event.nodeId());
        
        if (event.peerId() != null) {
            log.append(" -> ").append(event.peerId());
        }
        
        log.append(": ").append(event.payloadSummary());
        return log.toString();
    }

    /**
     * Extracts leader ID from a LEADER_ELECTED event.
     *
     * @param event the event
     * @return leader ID if found, null otherwise
     */
    private String extractLeaderIdFromEvent(SimulationEvent event) {
        if (event != null && event.type() == EventType.LEADER_ELECTED) {
            // Leader is typically the nodeId that generated the event
            return event.nodeId();
        }
        return null;
    }
}
