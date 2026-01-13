package de.haw.vsp.simulation.core;

/**
 * Publisher interface for simulation events.
 *
 * This interface abstracts the event channel from the simulation core
 * to the Observation & Analysis layer. Implementations typically fan out
 * events to visualization, metrics, and logging components.
 *
 * The publisher is called by the SimulationEngine during execution to
 * emit events about:
 * - Messages sent/received between nodes
 * - State changes in nodes
 * - Algorithm-specific events (e.g., leader election)
 * - System events (start, stop, errors)
 *
 * Design rationale:
 * - Transparency: State changes and message flows become observable
 * - Openness: Additional observers can be added without modifying the core
 * - Scalability: Event handling can be scaled independently of the simulation engine
 *
 * This interface is extended by SimulationEventBus to add subscription capabilities.
 */
public interface SimulationEventPublisher {

    /**
     * Publishes a simulation event to all registered observers.
     *
     * Events are delivered in publication order to ensure consistent
     * observation of the simulation state.
     *
     * Publishing must not block simulation execution. Implementations
     * should handle fan-out efficiently.
     *
     * @param event the event to publish (must not be null)
     * @throws IllegalArgumentException if event is null
     */
    void publish(SimulationEvent event);
}

