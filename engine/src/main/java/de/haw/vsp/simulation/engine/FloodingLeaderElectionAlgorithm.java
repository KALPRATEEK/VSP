package de.haw.vsp.simulation.engine;

import de.haw.vsp.simulation.core.NodeId;
import de.haw.vsp.simulation.core.SimulationMessage;

/**
 * Flooding-based leader election algorithm.
 *
 * <p>This algorithm elects a leader using a flooding approach where each node
 * broadcasts its current leader candidate to all neighbors. The node with the
 * maximum NodeId is elected as the leader.
 *
 * <p><b>Algorithm behavior:</b>
 * <ul>
 *   <li>Each node starts with its own ID as currentLeaderId</li>
 *   <li>On start: broadcast own ID to all neighbors</li>
 *   <li>On receiving a message with higher ID: update currentLeaderId, broadcast new leader, mark as not converged</li>
 *   <li>On receiving a message with lower or equal ID: ignore (already converged to higher or equal leader)</li>
 * </ul>
 *
 * <p><b>Convergence:</b>
 * <ul>
 *   <li>Algorithm converges when no node updates its leader anymore</li>
 *   <li>In a connected graph: leaderId = max(NodeId) across all nodes</li>
 *   <li>Exactly one leader is elected</li>
 * </ul>
 */
public class FloodingLeaderElectionAlgorithm implements NodeAlgorithm {

    private static final String MESSAGE_TYPE_LEADER_ANNOUNCEMENT = "LEADER_ANNOUNCEMENT";

    private NodeId currentLeaderId;
    private boolean converged;

    /**
     * Creates a new flooding leader election algorithm instance.
     */
    public FloodingLeaderElectionAlgorithm() {
        this.converged = false;
    }

    @Override
    public void onStart(NodeContext context) {
        // Initialize leader to own ID
        this.currentLeaderId = context.self();

        // Broadcast own ID to all neighbors
        broadcastLeader(context, currentLeaderId);
    }

    @Override
    public void onMessage(NodeContext context, SimulationMessage message) {
        // Note: This method is only called after onStart() has been called,
        // so currentLeaderId is guaranteed to be non-null. Messages arriving
        // before initialization are buffered by SimulationNode and processed
        // after onStart() completes.

        if (!MESSAGE_TYPE_LEADER_ANNOUNCEMENT.equals(message.messageType())) {
            // Ignore unknown message types
            return;
        }

        if (!(message.payload() instanceof String)) {
            // Invalid payload format
            return;
        }

        NodeId announcedLeaderId = new NodeId((String) message.payload());

        // Initialize current leader if not yet set (should not happen, but defensive)
        if (currentLeaderId == null) {
            currentLeaderId = context.self();
        }

        // Compare announced leader with current leader
        if (announcedLeaderId.compareTo(currentLeaderId) > 0) {
            // Found a better leader - update and propagate
            currentLeaderId = announcedLeaderId;
            converged = false;
            broadcastLeader(context, currentLeaderId);
        }
        // If announced leader is lower or equal, ignore (already have better or equal leader)
    }

    /**
     * Broadcasts the given leader ID to all neighbors.
     *
     * @param context  the node context
     * @param leaderId the leader ID to broadcast
     */
    private void broadcastLeader(NodeContext context, NodeId leaderId) {
        // Send individual messages to each neighbor
        for (NodeId neighbor : context.neighbors()) {
            context.send(
                    neighbor,
                    new SimulationMessage(
                            context.self(),
                            neighbor,
                            MESSAGE_TYPE_LEADER_ANNOUNCEMENT,
                            leaderId.value()
                    )
            );
        }
    }

    /**
     * Returns the current leader ID.
     *
     * @return the current leader ID
     */
    public NodeId getCurrentLeaderId() {
        return currentLeaderId;
    }

    /**
     * Returns whether the algorithm has converged.
     *
     * @return true if converged, false otherwise
     */
    public boolean isConverged() {
        return converged;
    }
}

