package de.haw.vsp.simulation.engine;

import de.haw.vsp.simulation.core.NodeId;
import de.haw.vsp.simulation.core.SimulationMessage;
import de.haw.vsp.simulation.middleware.MessagingPort;

import java.util.Set;
import java.util.stream.Collectors;

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

        // Convert core types to middleware types
        de.haw.vsp.simulation.middleware.NodeId middlewareTarget = toMiddlewareNodeId(target);
        de.haw.vsp.simulation.middleware.SimulationMessage middlewareMessage = toMiddlewareMessage(message);

        messagingPort.send(middlewareTarget, middlewareMessage);
    }

    @Override
    public void broadcast(Set<NodeId> targets, SimulationMessage message) {
        if (targets == null) {
            throw new IllegalArgumentException("targets must not be null");
        }
        if (message == null) {
            throw new IllegalArgumentException("message must not be null");
        }

        // Convert core types to middleware types
        Set<de.haw.vsp.simulation.middleware.NodeId> middlewareTargets = targets.stream()
                .map(this::toMiddlewareNodeId)
                .collect(Collectors.toSet());
        de.haw.vsp.simulation.middleware.SimulationMessage middlewareMessage = toMiddlewareMessage(message);

        messagingPort.broadcast(middlewareTargets, middlewareMessage);
    }

    /**
     * Converts core NodeId to middleware NodeId.
     */
    private de.haw.vsp.simulation.middleware.NodeId toMiddlewareNodeId(NodeId coreNodeId) {
        return new de.haw.vsp.simulation.middleware.NodeId(coreNodeId.value());
    }

    /**
     * Converts core SimulationMessage to middleware SimulationMessage.
     */
    private de.haw.vsp.simulation.middleware.SimulationMessage toMiddlewareMessage(SimulationMessage coreMessage) {
        // Generate a unique message ID
        String messageId = java.util.UUID.randomUUID().toString();

        // Convert payload to JsonNode if needed
        com.fasterxml.jackson.databind.JsonNode jsonPayload = null;
        if (coreMessage.payload() != null) {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            jsonPayload = mapper.valueToTree(coreMessage.payload());
        }

        return new de.haw.vsp.simulation.middleware.SimulationMessage(
                messageId,
                toMiddlewareNodeId(coreMessage.sender()),
                toMiddlewareNodeId(coreMessage.receiver()),
                coreMessage.messageType(),
                jsonPayload,
                System.currentTimeMillis()
        );
    }
}

