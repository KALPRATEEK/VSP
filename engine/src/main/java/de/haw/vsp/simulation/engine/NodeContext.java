package de.haw.vsp.simulation.engine;

import de.haw.vsp.simulation.core.NodeId;
import de.haw.vsp.simulation.core.SimulationMessage;

import java.util.Set;

/**
 * Context provided to algorithms for interacting with the simulation environment.
 *
 * NodeContext abstracts the simulation environment and provides algorithms with:
 * - Node identity (self)
 * - Network topology information (neighbors)
 * - Messaging capabilities (send, broadcast)
 *
 * Algorithms must use this context for all interactions with other nodes.
 * Transport details (UDP, in-memory, etc.) are completely hidden.
 */
public interface NodeContext {

    /**
     * Returns the ID of the current node.
     *
     * @return the node's own ID
     */
    NodeId self();

    /**
     * Returns the set of neighbor node IDs.
     *
     * The neighbor set is determined by the network topology and remains
     * immutable during a simulation run.
     *
     * @return immutable set of neighboring node IDs
     */
    Set<NodeId> neighbors();

    /**
     * Sends a message to a specific target node.
     *
     * @param target  the ID of the target node
     * @param message the message to send
     */
    void send(NodeId target, SimulationMessage message);

    /**
     * Broadcasts a message to multiple target nodes.
     *
     * @param targets the set of target node IDs
     * @param message the message to broadcast
     */
    void broadcast(Set<NodeId> targets, SimulationMessage message);
}

