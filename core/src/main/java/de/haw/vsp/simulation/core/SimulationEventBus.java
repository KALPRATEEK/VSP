package de.haw.vsp.simulation.core;

/**
 * Central event bus for the simulation system using the Observer Pattern.
 *
 * The event bus extends SimulationEventPublisher with subscription capabilities,
 * allowing components to register interest in specific event types.
 *
 * Key responsibilities:
 * - Accept event publications from the simulation engine
 * - Fan out events to all listeners subscribed to the event's type
 * - Preserve event order per publisher
 * - Support dynamic subscription/unsubscription
 *
 * Thread-safety:
 * This interface is designed for concurrent use. Implementations must safely
 * handle concurrent subscribe/unsubscribe/publish operations.
 *
 * Design rationale:
 * - Transparency: All significant simulation events become observable
 * - Openness: New listeners (e.g., visualization tools) can subscribe without
 *   modifying the core
 * - Scalability: Event fan-out supports multiple observers efficiently
 * - Distribution Transparency: Event consumers don't care where events originated
 */
public interface SimulationEventBus extends SimulationEventPublisher {

    /**
     * Subscribes a listener to receive events of the specified type.
     *
     * The listener will receive all future events of the given type via
     * {@link SimulationEventListener#onEvent(SimulationEvent)} until it
     * unsubscribes.
     *
     * Multiple listeners can subscribe to the same event type. The same
     * listener instance can subscribe multiple times (once per event type).
     *
     * Event delivery:
     * - Events are delivered in publication order per publisher
     * - Events are delivered synchronously during publish()
     * - Slow listeners may impact overall event delivery performance
     *
     * @param type the event type to subscribe to (must not be null)
     * @param listener the listener to notify (must not be null)
     * @throws IllegalArgumentException if type or listener is null
     */
    void subscribe(EventType type, SimulationEventListener listener);

    /**
     * Unsubscribes a listener from events of the specified type.
     *
     * After this call returns, the listener will no longer receive events
     * of the given type. If the listener was not subscribed to this event
     * type, this method has no effect.
     *
     * Unsubscription takes effect immediately:
     * - Events published after unsubscribe() returns will not be delivered
     * - Events being delivered during unsubscribe() may or may not be received
     *
     * @param type the event type to unsubscribe from (must not be null)
     * @param listener the listener to remove (must not be null)
     * @throws IllegalArgumentException if type or listener is null
     */
    void unsubscribe(EventType type, SimulationEventListener listener);
}

