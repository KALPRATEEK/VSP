package de.haw.vsp.simulation.core;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thread-safe implementation of the SimulationEventBus using the Observer Pattern.
 *
 * This implementation:
 * - Maintains separate listener lists per event type for efficient filtering
 * - Uses thread-safe collections to support concurrent subscribe/unsubscribe/publish
 * - Preserves event order per publisher (events are delivered synchronously)
 * - Handles dynamic subscription changes during event delivery
 *
 * Performance characteristics:
 * - subscribe/unsubscribe: O(1) amortized
 * - publish: O(n) where n is the number of subscribers for the event's type
 *
 * Memory management:
 * - Uses CopyOnWriteArrayList to avoid iterator issues during concurrent modifications
 * - Listeners are held by strong references; callers must explicitly unsubscribe
 *   to avoid memory leaks
 */
public class InMemorySimulationEventBus implements SimulationEventBus {

    /**
     * Map from event type to list of subscribed listeners.
     * CopyOnWriteArrayList ensures thread-safe iteration during publish
     * even if subscribe/unsubscribe is called concurrently.
     */
    private final Map<EventType, CopyOnWriteArrayList<SimulationEventListener>> subscribers;

    /**
     * Creates a new empty event bus.
     */
    public InMemorySimulationEventBus() {
        this.subscribers = new ConcurrentHashMap<>();
    }

    @Override
    public void publish(SimulationEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("event must not be null");
        }

        // Get listeners for this event type
        List<SimulationEventListener> listeners = subscribers.get(event.type());

        if (listeners != null) {
            // Deliver to all subscribed listeners
            // CopyOnWriteArrayList provides a stable snapshot for iteration
            for (SimulationEventListener listener : listeners) {
                try {
                    listener.onEvent(event);
                } catch (Exception e) {
                    // Silently swallow listener exceptions to prevent one misbehaving listener
                    // from affecting others. In a production system, this would be logged,
                    // but the core module should not have logging dependencies.
                    // The exception is caught and ignored to maintain event delivery stability.
                }
            }
        }
    }

    @Override
    public void subscribe(EventType type, SimulationEventListener listener) {
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        if (listener == null) {
            throw new IllegalArgumentException("listener must not be null");
        }

        // Get or create listener list for this event type
        subscribers.computeIfAbsent(type, k -> new CopyOnWriteArrayList<>())
                   .add(listener);
    }

    @Override
    public void unsubscribe(EventType type, SimulationEventListener listener) {
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        if (listener == null) {
            throw new IllegalArgumentException("listener must not be null");
        }

        List<SimulationEventListener> listeners = subscribers.get(type);
        if (listeners != null) {
            listeners.remove(listener);
        }
    }
}
