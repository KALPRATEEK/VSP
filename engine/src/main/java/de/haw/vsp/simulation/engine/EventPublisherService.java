package de.haw.vsp.simulation.engine;

import de.haw.vsp.simulation.core.SimulationEvent;
import de.haw.vsp.simulation.core.SimulationEventPublisher;
import de.haw.vsp.simulation.core.SimulationId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Asynchronous event publisher for distributed nodes.
 *
 * <p>Conforms to § 8.1 (asynchronous, best-effort delivery) and § 8.2 (event-driven architecture).
 *
 * <p>Key properties:
 * <ul>
 *   <li>Non-blocking: Events are added to queue without waiting</li>
 *   <li>Best-effort: Events may be dropped if queue is full (conforms to UDP semantics)</li>
 *   <li>Batch sending: Multiple events sent in single HTTP request for efficiency</li>
 *   <li>Async processing: Background thread handles HTTP communication</li>
 * </ul>
 *
 * <p>Thread-safety: This class is thread-safe and designed for concurrent event publishing.
 */
public class EventPublisherService implements SimulationEventPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(EventPublisherService.class);
    private static final int MAX_QUEUE_SIZE = 10_000;
    private static final int MAX_BATCH_SIZE = 100;
    private static final long POLL_TIMEOUT_MS = 100;

    private final RestTemplate restTemplate;
    private final String backendUrl;
    private final SimulationId simulationId;
    private final BlockingQueue<SimulationEvent> eventQueue;
    private final ExecutorService asyncExecutor;
    private final AtomicBoolean running;

    /**
     * Creates a new event publisher service.
     *
     * @param backendUrl URL of the backend (e.g., "http://backend:8080")
     * @param simulationId the simulation ID for event association
     * @throws IllegalArgumentException if backendUrl or simulationId is null
     */
    public EventPublisherService(String backendUrl, SimulationId simulationId) {
        if (backendUrl == null || backendUrl.isBlank()) {
            throw new IllegalArgumentException("backendUrl must not be null or blank");
        }
        if (simulationId == null) {
            throw new IllegalArgumentException("simulationId must not be null");
        }

        this.backendUrl = backendUrl;
        this.simulationId = simulationId;
        this.restTemplate = new RestTemplate();
        this.eventQueue = new LinkedBlockingQueue<>(MAX_QUEUE_SIZE);
        this.asyncExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "event-publisher");
            t.setDaemon(true);
            return t;
        });
        this.running = new AtomicBoolean(false);
    }

    /**
     * Starts the background event publishing loop.
     *
     * <p>This method must be called before publishing events.
     * It starts a background thread that batches and sends events to the backend.
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            asyncExecutor.submit(this::eventPublishingLoop);
            LOG.info("Event publisher started for simulation {}", simulationId);
        } else {
            LOG.warn("Event publisher already running");
        }
    }

    /**
     * Stops the event publisher and waits for pending events to be sent.
     *
     * <p>This method blocks until all events in the queue are processed
     * or the timeout is reached.
     */
    public void stop() {
        if (running.compareAndSet(true, false)) {
            LOG.info("Stopping event publisher for simulation {}", simulationId);

            asyncExecutor.shutdown();
            try {
                if (!asyncExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    asyncExecutor.shutdownNow();
                    LOG.warn("Event publisher did not terminate gracefully");
                }
            } catch (InterruptedException e) {
                asyncExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }

            LOG.info("Event publisher stopped. {} events remaining in queue", eventQueue.size());
        }
    }

    @Override
    public void publish(SimulationEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("event must not be null");
        }

        if (!running.get()) {
            LOG.warn("Event publisher not running, dropping event: {}", event.type());
            return;
        }

        // Non-blocking: add to queue
        boolean added = eventQueue.offer(event);

        if (!added) {
            // Best-effort: drop if queue full (conforms to § 8.1)
            LOG.warn("Event queue full (size={}), dropping event: {} from node {}",
                     MAX_QUEUE_SIZE, event.type(), event.nodeId());
        }
    }

    /**
     * Background loop that batches and sends events to backend.
     *
     * <p>Conforms to § 8.1: asynchronous, best-effort delivery.
     */
    private void eventPublishingLoop() {
        List<SimulationEvent> batch = new ArrayList<>(MAX_BATCH_SIZE);

        while (running.get() || !eventQueue.isEmpty()) {
            try {
                batch.clear();

                // Wait for first event with timeout
                SimulationEvent first = eventQueue.poll(POLL_TIMEOUT_MS, TimeUnit.MILLISECONDS);

                if (first != null) {
                    batch.add(first);

                    // Drain additional events for batching (up to MAX_BATCH_SIZE)
                    eventQueue.drainTo(batch, MAX_BATCH_SIZE - 1);

                    // Send batch to backend
                    pushBatchToBackend(batch);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOG.info("Event publishing loop interrupted");
                break;
            } catch (Exception e) {
                LOG.error("Unexpected error in event publishing loop", e);
                // Continue publishing (best-effort)
            }
        }

        LOG.info("Event publishing loop terminated");
    }

    /**
     * Sends a batch of events to the backend.
     *
     * <p>Uses HTTP POST to /internal/events endpoint.
     * Expects 202 Accepted response (async processing on backend).
     *
     * @param events batch of events to send
     */
    private void pushBatchToBackend(List<SimulationEvent> events) {
        if (events.isEmpty()) {
            return;
        }

        EventBatch batch = new EventBatch(simulationId, events);
        String url = backendUrl + "/internal/events";

        try {
            restTemplate.postForEntity(url, batch, Void.class);

            LOG.debug("Pushed {} events to backend", events.size());
        } catch (RestClientException e) {
            // Best-effort: log and continue (conforms to § 8.1)
            LOG.warn("Failed to push event batch to backend: {}", e.getMessage());
        } catch (Exception e) {
            LOG.error("Unexpected error pushing events to backend", e);
        }
    }

    /**
     * DTO for event batch transmission.
     *
     * <p>Note: This is a minimal transport wrapper, not a domain DTO.
     * It only exists to group events for HTTP transmission.
     */
    public static record EventBatch(
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
