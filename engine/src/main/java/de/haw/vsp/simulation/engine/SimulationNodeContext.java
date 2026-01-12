package de.haw.vsp.simulation.engine;

import de.haw.vsp.simulation.core.NodeId;
import de.haw.vsp.simulation.core.SimulationMessage;
import de.haw.vsp.simulation.middleware.MessagingPort;

import java.util.Set;

/**
 * Default implementation of NodeContext.
 *
 * SimulationNodeContext provides algorithms with access to:
 * - Node identity (self)
 * - Network topology (neighbors)
 * - Messaging capabilities (send, broadcast)
 *
 * This implementation wraps a MessagingPort to abstract transport details.
 * Algorithms remain completely unaware of UDP, sockets, or Docker networking.
 *
 * Immutability:
 * - Node identity and neighbor set are immutable after construction
 * - Messaging operations are non-blocking from the algorithm's perspective
 */
public class SimulationNodeContext implements NodeContext {

    private final NodeId nodeId;
    private final Set<NodeId> neighbors;
    private final MessagingPort messagingPort;

    /**
     * Creates a new simulation node context.
     *
     * @param nodeId        the ID of the current node
     * @param neighbors     the set of neighboring node IDs (immutable)
     * @param messagingPort the messaging port for communication
     * @throws IllegalArgumentException if any parameter is null
     */
    public SimulationNodeContext(NodeId nodeId, Set<NodeId> neighbors, MessagingPort messagingPort) {
        if (nodeId == null) {
            throw new IllegalArgumentException("nodeId must not be null");
        }
        if (neighbors == null) {
            throw new IllegalArgumentException("neighbors must not be null");
        }
        if (messagingPort == null) {
            throw new IllegalArgumentException("messagingPort must not be null");
        }

        this.nodeId = nodeId;
        this.neighbors = Set.copyOf(neighbors);
        this.messagingPort = messagingPort;
    }

    @Override
    public NodeId self() {
        return nodeId;
    }

    @Override
    public Set<NodeId> neighbors() {
        return neighbors;
    }

    @Override
    public void send(NodeId target, SimulationMessage message) {
        if (target == null) {
            throw new IllegalArgumentException("target must not be null");
        }
        if (message == null) {
            throw new IllegalArgumentException("message must not be null");
        }
        messagingPort.send(target, message);
    }

    @Override
    public void broadcast(Set<NodeId> targets, SimulationMessage message) {
        if (targets == null) {
            throw new IllegalArgumentException("targets must not be null");
        }
        if (message == null) {
            throw new IllegalArgumentException("message must not be null");
        }
        messagingPort.broadcast(targets, message);
    }
}

