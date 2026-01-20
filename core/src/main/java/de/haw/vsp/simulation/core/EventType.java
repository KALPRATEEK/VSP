package de.haw.vsp.simulation.core;

/**
 * Enumeration of all event types that can occur during simulation execution.
 *
 * These types are used for:
 * - Event filtering and subscription in the SimulationEventBus
 * - Categorization of SimulationEvent instances
 * - Routing events to appropriate consumers (visualization, metrics, logging)
 *
 * Event types are derived from the documented event-driven architecture:
 * - MESSAGE_SENT: A node sent a message to another node
 * - MESSAGE_RECEIVED: A node received a message from another node
 * - STATE_CHANGED: A node changed its internal state
 * - LEADER_ELECTED: A leader was elected in the algorithm
 * - ERROR: An error occurred during simulation execution
 * - METRICS_UPDATE: Periodic metrics update from a node (distributed mode)
 */
public enum EventType {
    MESSAGE_SENT,
    MESSAGE_RECEIVED,
    STATE_CHANGED,
    LEADER_ELECTED,
    ERROR,
    METRICS_UPDATE
}
