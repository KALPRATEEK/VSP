package de.haw.vsp.simulation.engine;

import de.haw.vsp.simulation.core.NodeId;
import de.haw.vsp.simulation.core.SimulationMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for FloodingLeaderElectionAlgorithm.
 *
 * Tests verify:
 * - Correct leader election (max NodeId)
 * - Convergence behavior
 * - Message handling
 * - Topology independence
 */
@DisplayName("FloodingLeaderElectionAlgorithm")
class FloodingLeaderElectionAlgorithmTest {

    @Nested
    @DisplayName("Initialization")
    class Initialization {

        @Test
        @DisplayName("should initialize with converged=false")
        void shouldInitializeWithNotConverged() {
            FloodingLeaderElectionAlgorithm algorithm = new FloodingLeaderElectionAlgorithm();
            assertFalse(algorithm.isConverged());
        }
    }

    @Nested
    @DisplayName("OnStart Behavior")
    class OnStartBehavior {

        @Test
        @DisplayName("should set currentLeaderId to own ID on start")
        void shouldSetCurrentLeaderIdToOwnIdOnStart() {
            NodeId nodeId = new NodeId("node-1");
            Set<NodeId> neighbors = Set.of(new NodeId("node-2"), new NodeId("node-3"));
            MockNodeContext context = new MockNodeContext(nodeId, neighbors);
            FloodingLeaderElectionAlgorithm algorithm = new FloodingLeaderElectionAlgorithm();

            algorithm.onStart(context);

            assertEquals(nodeId, algorithm.getCurrentLeaderId());
        }

        @Test
        @DisplayName("should broadcast own ID to all neighbors on start")
        void shouldBroadcastOwnIdToAllNeighborsOnStart() {
            NodeId nodeId = new NodeId("node-1");
            Set<NodeId> neighbors = Set.of(new NodeId("node-2"), new NodeId("node-3"));
            MockNodeContext context = new MockNodeContext(nodeId, neighbors);
            FloodingLeaderElectionAlgorithm algorithm = new FloodingLeaderElectionAlgorithm();

            algorithm.onStart(context);

            List<SimulationMessage> sentMessages = context.getSentMessages();
            assertEquals(2, sentMessages.size());

            // Verify all neighbors received a message
            Set<NodeId> recipients = new HashSet<>();
            for (SimulationMessage msg : sentMessages) {
                assertEquals("LEADER_ANNOUNCEMENT", msg.messageType());
                assertEquals(nodeId, msg.sender());
                assertEquals(nodeId.value(), msg.payload());
                recipients.add(msg.receiver());
            }
            assertEquals(neighbors, recipients);
        }

        @Test
        @DisplayName("should handle node with no neighbors")
        void shouldHandleNodeWithNoNeighbors() {
            NodeId nodeId = new NodeId("node-1");
            Set<NodeId> neighbors = Set.of();
            MockNodeContext context = new MockNodeContext(nodeId, neighbors);
            FloodingLeaderElectionAlgorithm algorithm = new FloodingLeaderElectionAlgorithm();

            algorithm.onStart(context);

            assertEquals(nodeId, algorithm.getCurrentLeaderId());
            assertTrue(context.getSentMessages().isEmpty());
        }
    }

    @Nested
    @DisplayName("Message Processing")
    class MessageProcessing {

        @Test
        @DisplayName("should update leader when receiving higher ID")
        void shouldUpdateLeaderWhenReceivingHigherId() {
            NodeId nodeId = new NodeId("node-1");
            NodeId higherNodeId = new NodeId("node-9");
            Set<NodeId> neighbors = Set.of(new NodeId("node-2"));
            MockNodeContext context = new MockNodeContext(nodeId, neighbors);
            FloodingLeaderElectionAlgorithm algorithm = new FloodingLeaderElectionAlgorithm();

            algorithm.onStart(context);
            context.clearSentMessages();

            SimulationMessage msg = new SimulationMessage(
                    higherNodeId,
                    nodeId,
                    "LEADER_ANNOUNCEMENT",
                    higherNodeId.value(),
                    null
            );
            algorithm.onMessage(context, msg);

            assertEquals(higherNodeId, algorithm.getCurrentLeaderId());
        }

        @Test
        @DisplayName("should broadcast new leader when receiving higher ID")
        void shouldBroadcastNewLeaderWhenReceivingHigherId() {
            NodeId nodeId = new NodeId("node-1");
            NodeId higherNodeId = new NodeId("node-9");
            Set<NodeId> neighbors = Set.of(new NodeId("node-2"), new NodeId("node-3"));
            MockNodeContext context = new MockNodeContext(nodeId, neighbors);
            FloodingLeaderElectionAlgorithm algorithm = new FloodingLeaderElectionAlgorithm();

            algorithm.onStart(context);
            context.clearSentMessages();

            SimulationMessage msg = new SimulationMessage(
                    higherNodeId,
                    nodeId,
                    "LEADER_ANNOUNCEMENT",
                    higherNodeId.value(),
                    null
            );
            algorithm.onMessage(context, msg);

            List<SimulationMessage> sentMessages = context.getSentMessages();
            assertEquals(2, sentMessages.size());

            for (SimulationMessage sentMsg : sentMessages) {
                assertEquals("LEADER_ANNOUNCEMENT", sentMsg.messageType());
                assertEquals(higherNodeId.value(), sentMsg.payload());
            }
        }

        @Test
        @DisplayName("should ignore message with lower ID")
        void shouldIgnoreMessageWithLowerId() {
            NodeId nodeId = new NodeId("node-9");
            NodeId lowerNodeId = new NodeId("node-1");
            Set<NodeId> neighbors = Set.of(new NodeId("node-5"));
            MockNodeContext context = new MockNodeContext(nodeId, neighbors);
            FloodingLeaderElectionAlgorithm algorithm = new FloodingLeaderElectionAlgorithm();

            algorithm.onStart(context);
            context.clearSentMessages();

            SimulationMessage msg = new SimulationMessage(
                    lowerNodeId,
                    nodeId,
                    "LEADER_ANNOUNCEMENT",
                    lowerNodeId.value(),
                    null
            );
            algorithm.onMessage(context, msg);

            // Leader should remain unchanged
            assertEquals(nodeId, algorithm.getCurrentLeaderId());
            // No messages should be sent
            assertTrue(context.getSentMessages().isEmpty());
        }

        @Test
        @DisplayName("should ignore message with equal ID")
        void shouldIgnoreMessageWithEqualId() {
            NodeId nodeId = new NodeId("node-5");
            Set<NodeId> neighbors = Set.of(new NodeId("node-3"));
            MockNodeContext context = new MockNodeContext(nodeId, neighbors);
            FloodingLeaderElectionAlgorithm algorithm = new FloodingLeaderElectionAlgorithm();

            algorithm.onStart(context);
            context.clearSentMessages();

            SimulationMessage msg = new SimulationMessage(
                    new NodeId("node-7"),
                    nodeId,
                    "LEADER_ANNOUNCEMENT",
                    nodeId.value(),
                    null
            );
            algorithm.onMessage(context, msg);

            // Leader should remain unchanged
            assertEquals(nodeId, algorithm.getCurrentLeaderId());
            // No messages should be sent
            assertTrue(context.getSentMessages().isEmpty());
        }

        @Test
        @DisplayName("should ignore unknown message types")
        void shouldIgnoreUnknownMessageTypes() {
            NodeId nodeId = new NodeId("node-1");
            Set<NodeId> neighbors = Set.of(new NodeId("node-2"));
            MockNodeContext context = new MockNodeContext(nodeId, neighbors);
            FloodingLeaderElectionAlgorithm algorithm = new FloodingLeaderElectionAlgorithm();

            algorithm.onStart(context);
            context.clearSentMessages();

            SimulationMessage msg = new SimulationMessage(
                    new NodeId("node-2"),
                    nodeId,
                    "UNKNOWN_MESSAGE_TYPE",
                    "some-payload",
                    null
            );
            algorithm.onMessage(context, msg);

            // Leader should remain unchanged
            assertEquals(nodeId, algorithm.getCurrentLeaderId());
            // No messages should be sent
            assertTrue(context.getSentMessages().isEmpty());
        }

        @Test
        @DisplayName("should handle invalid payload format gracefully")
        void shouldHandleInvalidPayloadFormatGracefully() {
            NodeId nodeId = new NodeId("node-1");
            Set<NodeId> neighbors = Set.of(new NodeId("node-2"));
            MockNodeContext context = new MockNodeContext(nodeId, neighbors);
            FloodingLeaderElectionAlgorithm algorithm = new FloodingLeaderElectionAlgorithm();

            algorithm.onStart(context);
            context.clearSentMessages();

            SimulationMessage msg = new SimulationMessage(
                    new NodeId("node-2"),
                    nodeId,
                    "LEADER_ANNOUNCEMENT",
                    123, // Invalid payload (not a String)
                    null
            );
            algorithm.onMessage(context, msg);

            // Leader should remain unchanged
            assertEquals(nodeId, algorithm.getCurrentLeaderId());
            // No messages should be sent
            assertTrue(context.getSentMessages().isEmpty());
        }
    }

    @Nested
    @DisplayName("Convergence in Different Topologies")
    class ConvergenceInDifferentTopologies {

        @Test
        @DisplayName("should elect max ID in simple line topology")
        void shouldElectMaxIdInLineTopology() {
            // Line: node-1 -- node-2 -- node-3
            NodeId node1 = new NodeId("node-1");
            NodeId node2 = new NodeId("node-2");
            NodeId node3 = new NodeId("node-3");

            Map<NodeId, Set<NodeId>> topology = Map.of(
                    node1, Set.of(node2),
                    node2, Set.of(node1, node3),
                    node3, Set.of(node2)
            );

            Map<NodeId, FloodingLeaderElectionAlgorithm> nodes = simulateNetwork(topology);

            // All nodes should have elected node-3 (max ID)
            for (Map.Entry<NodeId, FloodingLeaderElectionAlgorithm> entry : nodes.entrySet()) {
                assertEquals(node3, entry.getValue().getCurrentLeaderId(),
                        "Node " + entry.getKey() + " should have elected " + node3);
            }
        }

        @Test
        @DisplayName("should elect max ID in ring topology")
        void shouldElectMaxIdInRingTopology() {
            // Ring: node-1 -- node-2 -- node-3 -- node-1
            NodeId node1 = new NodeId("node-1");
            NodeId node2 = new NodeId("node-2");
            NodeId node3 = new NodeId("node-3");

            Map<NodeId, Set<NodeId>> topology = Map.of(
                    node1, Set.of(node2, node3),
                    node2, Set.of(node1, node3),
                    node3, Set.of(node1, node2)
            );

            Map<NodeId, FloodingLeaderElectionAlgorithm> nodes = simulateNetwork(topology);

            // All nodes should have elected node-3 (max ID)
            for (Map.Entry<NodeId, FloodingLeaderElectionAlgorithm> entry : nodes.entrySet()) {
                assertEquals(node3, entry.getValue().getCurrentLeaderId(),
                        "Node " + entry.getKey() + " should have elected " + node3);
            }
        }

        @Test
        @DisplayName("should elect max ID in star topology")
        void shouldElectMaxIdInStarTopology() {
            // Star: node-5 (center) connected to node-1, node-2, node-3, node-4
            NodeId node1 = new NodeId("node-1");
            NodeId node2 = new NodeId("node-2");
            NodeId node3 = new NodeId("node-3");
            NodeId node4 = new NodeId("node-4");
            NodeId node5 = new NodeId("node-5");

            Map<NodeId, Set<NodeId>> topology = Map.of(
                    node1, Set.of(node5),
                    node2, Set.of(node5),
                    node3, Set.of(node5),
                    node4, Set.of(node5),
                    node5, Set.of(node1, node2, node3, node4)
            );

            Map<NodeId, FloodingLeaderElectionAlgorithm> nodes = simulateNetwork(topology);

            // All nodes should have elected node-5 (max ID)
            for (Map.Entry<NodeId, FloodingLeaderElectionAlgorithm> entry : nodes.entrySet()) {
                assertEquals(node5, entry.getValue().getCurrentLeaderId(),
                        "Node " + entry.getKey() + " should have elected " + node5);
            }
        }

        @Test
        @DisplayName("should elect max ID in fully connected topology")
        void shouldElectMaxIdInFullyConnectedTopology() {
            NodeId node1 = new NodeId("node-1");
            NodeId node2 = new NodeId("node-2");
            NodeId node3 = new NodeId("node-3");
            NodeId node4 = new NodeId("node-4");

            Set<NodeId> allNodes = Set.of(node1, node2, node3, node4);
            Map<NodeId, Set<NodeId>> topology = new HashMap<>();
            for (NodeId node : allNodes) {
                Set<NodeId> neighbors = new HashSet<>(allNodes);
                neighbors.remove(node);
                topology.put(node, neighbors);
            }

            Map<NodeId, FloodingLeaderElectionAlgorithm> nodes = simulateNetwork(topology);

            // All nodes should have elected node-4 (max ID)
            for (Map.Entry<NodeId, FloodingLeaderElectionAlgorithm> entry : nodes.entrySet()) {
                assertEquals(node4, entry.getValue().getCurrentLeaderId(),
                        "Node " + entry.getKey() + " should have elected " + node4);
            }
        }

        @Test
        @DisplayName("should handle single node topology")
        void shouldHandleSingleNodeTopology() {
            NodeId node1 = new NodeId("node-1");
            Map<NodeId, Set<NodeId>> topology = Map.of(node1, Set.of());

            Map<NodeId, FloodingLeaderElectionAlgorithm> nodes = simulateNetwork(topology);

            // Single node should elect itself
            assertEquals(node1, nodes.get(node1).getCurrentLeaderId());
        }
    }

    @Nested
    @DisplayName("Convergence Properties")
    class ConvergenceProperties {

        @Test
        @DisplayName("should converge to max ID across all nodes")
        void shouldConvergeToMaxIdAcrossAllNodes() {
            // Create various node IDs
            List<String> nodeIds = Arrays.asList(
                    "node-03", "node-07", "node-01", "node-09", "node-05"
            );

            // Create a connected graph (ring)
            Map<NodeId, Set<NodeId>> topology = new HashMap<>();
            for (int i = 0; i < nodeIds.size(); i++) {
                NodeId current = new NodeId(nodeIds.get(i));
                NodeId next = new NodeId(nodeIds.get((i + 1) % nodeIds.size()));
                NodeId prev = new NodeId(nodeIds.get((i - 1 + nodeIds.size()) % nodeIds.size()));
                topology.put(current, Set.of(prev, next));
            }

            Map<NodeId, FloodingLeaderElectionAlgorithm> nodes = simulateNetwork(topology);

            // Find max ID
            NodeId maxId = topology.keySet().stream().max(Comparator.naturalOrder()).orElseThrow();

            // All nodes should have elected the max ID
            for (Map.Entry<NodeId, FloodingLeaderElectionAlgorithm> entry : nodes.entrySet()) {
                assertEquals(maxId, entry.getValue().getCurrentLeaderId(),
                        "Node " + entry.getKey() + " should have elected " + maxId);
            }
        }

        @Test
        @DisplayName("should converge in finite number of rounds")
        void shouldConvergeInFiniteNumberOfRounds() {
            // Line: node-1 -- node-2 -- node-3 -- node-4 -- node-5
            NodeId node1 = new NodeId("node-1");
            NodeId node2 = new NodeId("node-2");
            NodeId node3 = new NodeId("node-3");
            NodeId node4 = new NodeId("node-4");
            NodeId node5 = new NodeId("node-5");

            Map<NodeId, Set<NodeId>> topology = Map.of(
                    node1, Set.of(node2),
                    node2, Set.of(node1, node3),
                    node3, Set.of(node2, node4),
                    node4, Set.of(node3, node5),
                    node5, Set.of(node4)
            );

            int rounds = simulateNetworkWithRoundCount(topology);

            // In a line of 5 nodes, convergence should happen within diameter (4) rounds
            // Allow some buffer for message propagation
            assertTrue(rounds <= 10, "Should converge within 10 rounds, but took " + rounds);
        }
    }

    // ============= Helper Methods =============

    /**
     * Simulates a network with the given topology until convergence.
     *
     * @param topology map of node IDs to their neighbors
     * @return map of node IDs to their algorithms after convergence
     */
    private Map<NodeId, FloodingLeaderElectionAlgorithm> simulateNetwork(Map<NodeId, Set<NodeId>> topology) {
        Map<NodeId, FloodingLeaderElectionAlgorithm> algorithms = new HashMap<>();
        Map<NodeId, MockNodeContext> contexts = new HashMap<>();

        // Initialize all nodes
        for (Map.Entry<NodeId, Set<NodeId>> entry : topology.entrySet()) {
            NodeId nodeId = entry.getKey();
            Set<NodeId> neighbors = entry.getValue();

            FloodingLeaderElectionAlgorithm algorithm = new FloodingLeaderElectionAlgorithm();
            MockNodeContext context = new MockNodeContext(nodeId, neighbors);

            algorithms.put(nodeId, algorithm);
            contexts.put(nodeId, context);

            algorithm.onStart(context);
        }

        // Simulate message delivery until convergence
        int maxRounds = 100; // Prevent infinite loops
        for (int round = 0; round < maxRounds; round++) {
            // Collect all messages from this round
            Map<NodeId, List<SimulationMessage>> messagesPerNode = new HashMap<>();
            for (NodeId nodeId : topology.keySet()) {
                MockNodeContext context = contexts.get(nodeId);
                messagesPerNode.put(nodeId, new ArrayList<>(context.getSentMessages()));
                context.clearSentMessages();
            }

            // If no messages were sent, we've converged
            boolean anyMessages = messagesPerNode.values().stream()
                    .anyMatch(msgs -> !msgs.isEmpty());
            if (!anyMessages) {
                break;
            }

            // Deliver all messages
            for (Map.Entry<NodeId, List<SimulationMessage>> entry : messagesPerNode.entrySet()) {
                for (SimulationMessage msg : entry.getValue()) {
                    NodeId receiver = msg.receiver();
                    FloodingLeaderElectionAlgorithm receiverAlgorithm = algorithms.get(receiver);
                    MockNodeContext receiverContext = contexts.get(receiver);
                    if (receiverAlgorithm != null && receiverContext != null) {
                        receiverAlgorithm.onMessage(receiverContext, msg);
                    }
                }
            }
        }

        return algorithms;
    }

    /**
     * Simulates a network and returns the number of rounds until convergence.
     */
    private int simulateNetworkWithRoundCount(Map<NodeId, Set<NodeId>> topology) {
        Map<NodeId, FloodingLeaderElectionAlgorithm> algorithms = new HashMap<>();
        Map<NodeId, MockNodeContext> contexts = new HashMap<>();

        // Initialize all nodes
        for (Map.Entry<NodeId, Set<NodeId>> entry : topology.entrySet()) {
            NodeId nodeId = entry.getKey();
            Set<NodeId> neighbors = entry.getValue();

            FloodingLeaderElectionAlgorithm algorithm = new FloodingLeaderElectionAlgorithm();
            MockNodeContext context = new MockNodeContext(nodeId, neighbors);

            algorithms.put(nodeId, algorithm);
            contexts.put(nodeId, context);

            algorithm.onStart(context);
        }

        // Simulate message delivery until convergence
        int maxRounds = 100;
        int round;
        for (round = 0; round < maxRounds; round++) {
            // Collect all messages from this round
            Map<NodeId, List<SimulationMessage>> messagesPerNode = new HashMap<>();
            for (NodeId nodeId : topology.keySet()) {
                MockNodeContext context = contexts.get(nodeId);
                messagesPerNode.put(nodeId, new ArrayList<>(context.getSentMessages()));
                context.clearSentMessages();
            }

            // If no messages were sent, we've converged
            boolean anyMessages = messagesPerNode.values().stream()
                    .anyMatch(msgs -> !msgs.isEmpty());
            if (!anyMessages) {
                break;
            }

            // Deliver all messages
            for (Map.Entry<NodeId, List<SimulationMessage>> entry : messagesPerNode.entrySet()) {
                for (SimulationMessage msg : entry.getValue()) {
                    NodeId receiver = msg.receiver();
                    FloodingLeaderElectionAlgorithm receiverAlgorithm = algorithms.get(receiver);
                    MockNodeContext receiverContext = contexts.get(receiver);
                    if (receiverAlgorithm != null && receiverContext != null) {
                        receiverAlgorithm.onMessage(receiverContext, msg);
                    }
                }
            }
        }

        return round;
    }

    // ============= Mock NodeContext =============

    /**
     * Mock implementation of NodeContext for testing.
     */
    private static class MockNodeContext implements NodeContext {
        private final NodeId nodeId;
        private final Set<NodeId> neighbors;
        private final List<SimulationMessage> sentMessages = new ArrayList<>();

        public MockNodeContext(NodeId nodeId, Set<NodeId> neighbors) {
            this.nodeId = nodeId;
            this.neighbors = Set.copyOf(neighbors);
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
            sentMessages.add(message);
        }

        @Override
        public void broadcast(Set<NodeId> targets, SimulationMessage message) {
            for (NodeId target : targets) {
                sentMessages.add(new SimulationMessage(
                        message.sender(),
                        target,
                        message.messageType(),
                        message.payload(),
                        null
                ));
            }
        }

        public List<SimulationMessage> getSentMessages() {
            return new ArrayList<>(sentMessages);
        }

        public void clearSentMessages() {
            sentMessages.clear();
        }
    }
}

