package de.haw.vsp.simulation.core;

/**
 * Interface for publishing simulation events.
 *
 * SimulationEventPublisher abstracts the event channel from the simulation core
 * to the Observation & Analysis layer. Typical implementations will fan out events
 * to visualization, metrics, and logging.
 *
 * According to the API documentation, this interface provides a simple publish
 * method that allows the simulation engine to emit events without knowing
 * the specific observers.
 */
public interface SimulationEventPublisher {
    /**
     * Publishes a simulation event.
     *
     * The event will be distributed to all registered observers (visualization,
     * metrics, logging, etc.) according to the implementation.
     *
     * @param event the simulation event to publish (must not be null)
     * @throws IllegalArgumentException if event is null
     */
    void publish(SimulationEvent event);
}
