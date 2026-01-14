package de.haw.vsp.simulation.engine;

import de.haw.vsp.simulation.core.NodeId;
import de.haw.vsp.simulation.core.SimulationMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SimulationNode.
 *
 * Verifies:
 * - Lifecycle guarantees (onStart called exactly once)
 * - Message delegation to algorithm
 * - State management (node ID, neighbors, started flag)
 * - Error handling for invalid states
 */
@DisplayName("SimulationNode")
class SimulationNodeTest {

    private NodeId nodeId;
    private Set<NodeId> neighbors;
    private TestAlgorithm algorithm;
    private TestNodeContext context;

    @BeforeEach
    void setUp() {
        nodeId = new NodeId("node-1");
        neighbors = Set.of(new NodeId("node-2"), new NodeId("node-3"));
        algorithm = new TestAlgorithm();
        context = new TestNodeContext(nodeId, neighbors);
    }

    @Nested
    @DisplayName("Construction")
    class Construction {

        @Test
        @DisplayName("should create node with valid parameters")
        void shouldCreateNodeWithValidParameters() {
            SimulationNode node = new SimulationNode(nodeId, neighbors, algorithm, context);

            assertNotNull(node);
            assertEquals(nodeId, node.getNodeId());
            assertEquals(neighbors, node.getNeighbors());
            assertFalse(node.isStarted());
        }

        @Test
        @DisplayName("should reject null nodeId")
        void shouldRejectNullNodeId() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new SimulationNode(null, neighbors, algorithm, context)
            );
            assertTrue(exception.getMessage().contains("nodeId must not be null"));
        }

        @Test
        @DisplayName("should reject null neighbors")
        void shouldRejectNullNeighbors() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new SimulationNode(nodeId, null, algorithm, context)
            );
            assertTrue(exception.getMessage().contains("neighbors must not be null"));
        }

        @Test
        @DisplayName("should reject null algorithm")
        void shouldRejectNullAlgorithm() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new SimulationNode(nodeId, neighbors, null, context)
            );
            assertTrue(exception.getMessage().contains("algorithm must not be null"));
        }

        @Test
        @DisplayName("should reject null context")
        void shouldRejectNullContext() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new SimulationNode(nodeId, neighbors, algorithm, null)
            );
            assertTrue(exception.getMessage().contains("nodeContext must not be null"));
        }

        @Test
        @DisplayName("should create immutable copy of neighbors")
        void shouldCreateImmutableCopyOfNeighbors() {
            Set<NodeId> inputNeighbors = Set.of(new NodeId("node-2"));
            SimulationNode node = new SimulationNode(nodeId, inputNeighbors, algorithm, context);

            Set<NodeId> retrievedNeighbors = node.getNeighbors();
            // Verify the neighbors are equal
            assertEquals(inputNeighbors, retrievedNeighbors);
            // Verify immutability - should throw UnsupportedOperationException
            assertThrows(UnsupportedOperationException.class,
                    () -> retrievedNeighbors.add(new NodeId("node-4")));
        }
    }

    @Nested
    @DisplayName("Lifecycle")
    class Lifecycle {

        @Test
        @DisplayName("should call algorithm onStart exactly once")
        void shouldCallAlgorithmOnStartExactlyOnce() {
            SimulationNode node = new SimulationNode(nodeId, neighbors, algorithm, context);

            assertFalse(node.isStarted());
            assertEquals(0, algorithm.onStartCalls);

            node.onStart();

            assertTrue(node.isStarted());
            assertEquals(1, algorithm.onStartCalls);
        }

        @Test
        @DisplayName("should pass correct context to algorithm onStart")
        void shouldPassCorrectContextToAlgorithmOnStart() {
            SimulationNode node = new SimulationNode(nodeId, neighbors, algorithm, context);

            node.onStart();

            assertNotNull(algorithm.lastContext);
            assertEquals(context, algorithm.lastContext);
        }

        @Test
        @DisplayName("should reject duplicate onStart calls")
        void shouldRejectDuplicateOnStartCalls() {
            SimulationNode node = new SimulationNode(nodeId, neighbors, algorithm, context);
            node.onStart();

            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> node.onStart()
            );
            assertTrue(exception.getMessage().contains("onStart() has already been called"));
            assertTrue(exception.getMessage().contains(nodeId.toString()));
        }

        @Test
        @DisplayName("should reject onMessage before onStart")
        void shouldRejectOnMessageBeforeOnStart() {
            SimulationNode node = new SimulationNode(nodeId, neighbors, algorithm, context);
            SimulationMessage message = new SimulationMessage(
                    new NodeId("node-2"),
                    nodeId,
                    "TEST",
                    null,
                    null
            );

            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> node.onMessage(context, message)
            );
            assertTrue(exception.getMessage().contains("onStart() must be called before onMessage()"));
            assertTrue(exception.getMessage().contains(nodeId.toString()));
        }
    }

    @Nested
    @DisplayName("Message Handling")
    class MessageHandling {

        @Test
        @DisplayName("should delegate message to algorithm")
        void shouldDelegateMessageToAlgorithm() {
            SimulationNode node = new SimulationNode(nodeId, neighbors, algorithm, context);
            node.onStart();

            SimulationMessage message = new SimulationMessage(
                    new NodeId("node-2"),
                    nodeId,
                    "TEST",
                    "payload",
                    null
            );

            node.onMessage(context, message);

            assertEquals(1, algorithm.onMessageCalls);
            assertEquals(message, algorithm.lastMessage);
            assertEquals(context, algorithm.lastContext);
        }

        @Test
        @DisplayName("should handle multiple messages")
        void shouldHandleMultipleMessages() {
            SimulationNode node = new SimulationNode(nodeId, neighbors, algorithm, context);
            node.onStart();

            SimulationMessage message1 = new SimulationMessage(
                    new NodeId("node-2"),
                    nodeId,
                    "TEST1",
                    "payload1",
                    null
            );
            SimulationMessage message2 = new SimulationMessage(
                    new NodeId("node-3"),
                    nodeId,
                    "TEST2",
                    "payload2",
                    null
            );

            node.onMessage(context, message1);
            node.onMessage(context, message2);

            assertEquals(2, algorithm.onMessageCalls);
            assertEquals(message2, algorithm.lastMessage);
        }

        @Test
        @DisplayName("should reject null context in onMessage")
        void shouldRejectNullContextInOnMessage() {
            SimulationNode node = new SimulationNode(nodeId, neighbors, algorithm, context);
            node.onStart();

            SimulationMessage message = new SimulationMessage(
                    new NodeId("node-2"),
                    nodeId,
                    "TEST",
                    null,
                    null
            );

            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> node.onMessage(null, message)
            );
            assertTrue(exception.getMessage().contains("context must not be null"));
        }

        @Test
        @DisplayName("should reject null message in onMessage")
        void shouldRejectNullMessageInOnMessage() {
            SimulationNode node = new SimulationNode(nodeId, neighbors, algorithm, context);
            node.onStart();

            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> node.onMessage(context, null)
            );
            assertTrue(exception.getMessage().contains("message must not be null"));
        }
    }

    @Nested
    @DisplayName("State Management")
    class StateManagement {

        @Test
        @DisplayName("should maintain correct nodeId")
        void shouldMaintainCorrectNodeId() {
            SimulationNode node = new SimulationNode(nodeId, neighbors, algorithm, context);

            assertEquals(nodeId, node.getNodeId());
        }

        @Test
        @DisplayName("should maintain immutable neighbors")
        void shouldMaintainImmutableNeighbors() {
            SimulationNode node = new SimulationNode(nodeId, neighbors, algorithm, context);

            Set<NodeId> retrievedNeighbors = node.getNeighbors();
            assertEquals(neighbors, retrievedNeighbors);

            // Verify immutability
            assertThrows(UnsupportedOperationException.class,
                    () -> retrievedNeighbors.add(new NodeId("node-4")));
        }

        @Test
        @DisplayName("should track started state correctly")
        void shouldTrackStartedStateCorrectly() {
            SimulationNode node = new SimulationNode(nodeId, neighbors, algorithm, context);

            assertFalse(node.isStarted());

            node.onStart();

            assertTrue(node.isStarted());
        }
    }

    /**
     * Test implementation of NodeAlgorithm that records method calls.
     */
    private static class TestAlgorithm implements NodeAlgorithm {
        int onStartCalls = 0;
        int onMessageCalls = 0;
        NodeContext lastContext = null;
        SimulationMessage lastMessage = null;

        @Override
        public void onStart(NodeContext context) {
            onStartCalls++;
            lastContext = context;
        }

        @Override
        public void onMessage(NodeContext context, SimulationMessage message) {
            onMessageCalls++;
            lastContext = context;
            lastMessage = message;
        }
    }

    /**
     * Test implementation of NodeContext for testing purposes.
     */
    private static class TestNodeContext implements NodeContext {
        private final NodeId nodeId;
        private final Set<NodeId> neighbors;
        private final List<SimulationMessage> sentMessages = new ArrayList<>();

        TestNodeContext(NodeId nodeId, Set<NodeId> neighbors) {
            this.nodeId = nodeId;
            this.neighbors = neighbors;
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
                sentMessages.add(message);
            }
        }
    }
}

