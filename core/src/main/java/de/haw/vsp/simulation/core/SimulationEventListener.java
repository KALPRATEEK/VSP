package de.haw.vsp.simulation.core;

/**
 * Listener interface for simulation events.
 *
 * Components that need to observe simulation behavior (visualization, metrics,
 * logging) implement this interface and register with the SimulationEventBus
 * to receive events of interest.
 *
 * Listeners are called synchronously during event publication. Implementations
 * should process events quickly to avoid blocking the event bus.
 *
 * Design rationale:
 * - Transparency: Enables observation of simulation behavior without coupling
 * - Openness: New listeners can be added without modifying existing components
 * - Observer Pattern: Clean separation between event producers and consumers
 */
@FunctionalInterface
public interface SimulationEventListener {

    /**
     * Called when a subscribed event is published.
     *
     * Listeners receive only events of types they subscribed to via
     * {@link SimulationEventBus#subscribe(EventType, SimulationEventListener)}.
     *
     * Implementations should:
     * - Process events quickly to avoid blocking
     * - Not throw exceptions (catch and log internally)
     * - Not assume any specific threading model
     *
     * @param event the simulation event (never null)
     */
    void onEvent(SimulationEvent event);
}

