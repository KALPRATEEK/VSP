package de.haw.vsp.simulation.core;

/**
 * Listener interface for receiving live simulation events.
 *
 * VisualizationListener defines the callback interface for components that want
 * to react to live simulation events (e.g. visualization, live metrics, UI models).
 *
 * Used in combination with an internal event bus and optional WebSocket adapters
 * to propagate events to the browser.
 */
public interface VisualizationListener {
    /**
     * Called when a new simulation event occurs.
     *
     * @param event the simulation event (must not be null)
     */
    void onEvent(SimulationEvent event);
}
