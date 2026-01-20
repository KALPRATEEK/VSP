package de.haw.vsp.simulation.control;

import de.haw.vsp.simulation.core.SimulationEvent;
import de.haw.vsp.simulation.core.SimulationEventBus;
import de.haw.vsp.simulation.core.SimulationId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * REST controller for receiving events from distributed nodes.
 *
 * <p>Conforms to § 8.2 "Event-Driven Architecture":
 * Events from remote nodes are published to the central SimulationEventBus.
 *
 * <p>Conforms to § 8.1 "Communication Concept":
 * - Asynchronous processing (returns 202 Accepted immediately)
 * - No ordering guarantee across nodes (events processed in arrival order)
 * - Best-effort: errors logged but don't block nodes
 *
 * <p>This controller:
 * <ul>
 *   <li>Accepts event batches via POST /internal/events</li>
 *   <li>Returns 202 Accepted immediately (async processing)</li>
 *   <li>Publishes events to SimulationEventBus asynchronously</li>
 *   <li>Handles concurrent submissions from multiple nodes</li>
 * </ul>
 */
@RestController
@RequestMapping("/internal/events")
public class EventAggregationController {

    private static final Logger LOG = LoggerFactory.getLogger(EventAggregationController.class);

    private final Map<SimulationId, SimulationEventBus> eventBuses;
    private final ExecutorService eventProcessingExecutor;

    /**
     * Creates a new event aggregation controller.
     *
     * @param eventBuses map of simulation IDs to their event buses
     */
    public EventAggregationController(Map<SimulationId, SimulationEventBus> eventBuses) {
        if (eventBuses == null) {
            throw new IllegalArgumentException("eventBuses must not be null");
        }

        this.eventBuses = eventBuses;
        this.eventProcessingExecutor = Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "event-aggregation");
            t.setDaemon(true);
            return t;
        });

        LOG.info("EventAggregationController initialized");
    }

    /**
     * Receives a batch of events from a remote node.
     *
     * <p>Processing is asynchronous - this method returns immediately with 202 Accepted.
     * Events are published to the EventBus in a background thread.
     *
     * <p>Conforms to § 8.1: "Messages may be delayed or reordered"
     * - No ordering guarantee across different nodes
     * - Events from same node are processed in order (within batch)
     *
     * @param batch event batch from node
     * @return 202 Accepted (async processing)
     */
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED) // 202 Accepted (async)
    public void receiveEvents(@RequestBody EventBatch batch) {
        if (batch == null) {
            LOG.warn("Received null event batch");
            return;
        }

        LOG.debug("Received {} events from simulation {}",
                  batch.events().size(), batch.simulationId());

        // Asynchronous processing - does NOT block node
        eventProcessingExecutor.submit(() -> processEventBatch(batch));
    }

    /**
     * Processes an event batch asynchronously.
     *
     * @param batch event batch to process
     */
    private void processEventBatch(EventBatch batch) {
        SimulationEventBus eventBus = eventBuses.get(batch.simulationId());

        if (eventBus == null) {
            LOG.warn("Received events for unknown simulation: {}", batch.simulationId());
            return;
        }

        // Publish events in arrival order (no ordering guarantee across nodes)
        int published = 0;
        int failed = 0;

        for (SimulationEvent event : batch.events()) {
            try {
                eventBus.publish(event);
                published++;
            } catch (Exception e) {
                LOG.error("Failed to publish event: type={}, nodeId={}",
                          event.type(), event.nodeId(), e);
                failed++;
            }
        }

        if (failed > 0) {
            LOG.warn("Published {}/{} events for simulation {} ({} failed)",
                     published, batch.events().size(), batch.simulationId(), failed);
        } else {
            LOG.debug("Published {} events for simulation {}",
                      published, batch.simulationId());
        }
    }

    /**
     * Health check endpoint for event aggregation service.
     *
     * @return OK if service is running
     */
    @GetMapping("/health")
    @ResponseStatus(HttpStatus.OK)
    public String health() {
        return "Event aggregation service is running";
    }

    /**
     * DTO for event batch transmission from nodes to backend.
     *
     * <p>Note: This is a minimal transport wrapper for HTTP transmission.
     */
    public record EventBatch(
        SimulationId simulationId,
        List<SimulationEvent> events
    ) {
        public EventBatch {
            if (simulationId == null) {
                throw new IllegalArgumentException("simulationId must not be null");
            }
            if (events == null) {
                throw new IllegalArgumentException("events must not be null");
            }
        }
    }
}
