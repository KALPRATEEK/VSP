package de.haw.vsp.simulation.engine;

import de.haw.vsp.simulation.core.NodeId;
import de.haw.vsp.simulation.core.SimulationMessage;
import de.haw.vsp.simulation.middleware.MessageHandler;
import de.haw.vsp.simulation.middleware.MessagingPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SimulationNodeContext.
 *
 * Verifies:
 * - Abstraction of transport details
 * - Immutability of node identity and neighbors
 * - Non-blocking messaging operations
 * - Correct delegation to MessagingPort
 */
@DisplayName("SimulationNodeContext")
class SimulationNodeContextTest {

    private NodeId nodeId;
    private Set<NodeId> neighbors;
    private TestMessagingPort messagingPort;
    private SimulationNodeContext context;

    @BeforeEach
    void setUp() {
        nodeId = new NodeId("node-1");
        neighbors = new HashSet<>();
        neighbors.add(new NodeId("node-2"));
        neighbors.add(new NodeId("node-3"));
        messagingPort = new TestMessagingPort();
        context = new SimulationNodeContext(nodeId, neighbors, messagingPort);
    }

    @Nested
    @DisplayName("Construction")
    class Construction {

        @Test
        @DisplayName("should create context with valid parameters")
        void shouldCreateContextWithValidParameters() {
            SimulationNodeContext ctx = new SimulationNodeContext(nodeId, neighbors, messagingPort);

            assertNotNull(ctx);
            assertEquals(nodeId, ctx.self());
            assertEquals(neighbors, ctx.neighbors());
        }

        @Test
        @DisplayName("should reject null nodeId")
        void shouldRejectNullNodeId() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new SimulationNodeContext(null, neighbors, messagingPort)
            );
            assertTrue(exception.getMessage().contains("nodeId must not be null"));
        }

        @Test
        @DisplayName("should reject null neighbors")
        void shouldRejectNullNeighbors() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new SimulationNodeContext(nodeId, null, messagingPort)
            );
            assertTrue(exception.getMessage().contains("neighbors must not be null"));
        }

        @Test
        @DisplayName("should reject null messagingPort")
        void shouldRejectNullMessagingPort() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new SimulationNodeContext(nodeId, neighbors, null)
            );
            assertTrue(exception.getMessage().contains("messagingPort must not be null"));
        }

        @Test
        @DisplayName("should accept empty neighbor set")
        void shouldAcceptEmptyNeighborSet() {
            Set<NodeId> emptyNeighbors = Set.of();
            SimulationNodeContext ctx = new SimulationNodeContext(nodeId, emptyNeighbors, messagingPort);

            assertNotNull(ctx);
            assertTrue(ctx.neighbors().isEmpty());
        }
    }

    @Nested
    @DisplayName("Node Identity")
    class NodeIdentity {

        @Test
        @DisplayName("should return correct node ID")
        void shouldReturnCorrectNodeId() {
            assertEquals(nodeId, context.self());
        }

        @Test
        @DisplayName("should return same node ID on multiple calls")
        void shouldReturnSameNodeIdOnMultipleCalls() {
            NodeId first = context.self();
            NodeId second = context.self();

            assertSame(first, second);
            assertEquals(nodeId, first);
        }
    }

    @Nested
    @DisplayName("Neighbor Management")
    class NeighborManagement {

        @Test
        @DisplayName("should return correct neighbors")
        void shouldReturnCorrectNeighbors() {
            Set<NodeId> result = context.neighbors();

            assertEquals(neighbors, result);
            assertEquals(2, result.size());
            assertTrue(result.contains(new NodeId("node-2")));
            assertTrue(result.contains(new NodeId("node-3")));
        }

        @Test
        @DisplayName("should return immutable neighbor set")
        void shouldReturnImmutableNeighborSet() {
            Set<NodeId> result = context.neighbors();

            assertThrows(UnsupportedOperationException.class,
                    () -> result.add(new NodeId("node-4")));
        }

        @Test
        @DisplayName("should not be affected by changes to original neighbor set")
        void shouldNotBeAffectedByChangesToOriginalNeighborSet() {
            Set<NodeId> mutableNeighbors = new HashSet<>();
            mutableNeighbors.add(new NodeId("node-2"));
            SimulationNodeContext ctx = new SimulationNodeContext(nodeId, mutableNeighbors, messagingPort);

            // Modify original set
            mutableNeighbors.add(new NodeId("node-3"));

            // Context should not be affected
            assertEquals(1, ctx.neighbors().size());
            assertTrue(ctx.neighbors().contains(new NodeId("node-2")));
            assertFalse(ctx.neighbors().contains(new NodeId("node-3")));
        }

        @Test
        @DisplayName("should return same neighbor set instance on multiple calls")
        void shouldReturnSameNeighborSetInstanceOnMultipleCalls() {
            Set<NodeId> first = context.neighbors();
            Set<NodeId> second = context.neighbors();

            assertSame(first, second);
        }
    }

    @Nested
    @DisplayName("Messaging - Send")
    class MessagingSend {

        @Test
        @DisplayName("should delegate send to messaging port")
        void shouldDelegateSendToMessagingPort() {
            NodeId target = new NodeId("node-2");
            SimulationMessage message = new SimulationMessage(
                    nodeId,
                    target,
                    "TEST",
                    "payload"
            );

            context.send(target, message);

            assertEquals(1, messagingPort.sentMessages.size());
            TestMessagingPort.SentMessage sent = messagingPort.sentMessages.get(0);
            assertEquals(target, sent.receiver);
            assertEquals(message, sent.message);
        }

        @Test
        @DisplayName("should send multiple messages")
        void shouldSendMultipleMessages() {
            NodeId target1 = new NodeId("node-2");
            NodeId target2 = new NodeId("node-3");
            SimulationMessage message1 = new SimulationMessage(nodeId, target1, "TEST1", null);
            SimulationMessage message2 = new SimulationMessage(nodeId, target2, "TEST2", null);

            context.send(target1, message1);
            context.send(target2, message2);

            assertEquals(2, messagingPort.sentMessages.size());
        }

        @Test
        @DisplayName("should reject null target")
        void shouldRejectNullTarget() {
            SimulationMessage message = new SimulationMessage(
                    nodeId,
                    new NodeId("node-2"),
                    "TEST",
                    null
            );

            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> context.send(null, message)
            );
            assertTrue(exception.getMessage().contains("target must not be null"));
        }

        @Test
        @DisplayName("should reject null message")
        void shouldRejectNullMessage() {
            NodeId target = new NodeId("node-2");

            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> context.send(target, null)
            );
            assertTrue(exception.getMessage().contains("message must not be null"));
        }

        @Test
        @DisplayName("should not block on send")
        void shouldNotBlockOnSend() {
            NodeId target = new NodeId("node-2");
            SimulationMessage message = new SimulationMessage(nodeId, target, "TEST", null);

            // Should complete immediately
            long startTime = System.nanoTime();
            context.send(target, message);
            long endTime = System.nanoTime();

            // Should take less than 10ms (non-blocking)
            long durationMs = (endTime - startTime) / 1_000_000;
            assertTrue(durationMs < 10, "send() should not block (took " + durationMs + "ms)");
        }
    }

    @Nested
    @DisplayName("Messaging - Broadcast")
    class MessagingBroadcast {

        @Test
        @DisplayName("should delegate broadcast to messaging port")
        void shouldDelegateBroadcastToMessagingPort() {
            Set<NodeId> targets = Set.of(new NodeId("node-2"), new NodeId("node-3"));
            SimulationMessage message = new SimulationMessage(
                    nodeId,
                    new NodeId("node-2"),
                    "TEST",
                    "payload"
            );

            context.broadcast(targets, message);

            assertEquals(1, messagingPort.broadcastMessages.size());
            TestMessagingPort.BroadcastMessage broadcast = messagingPort.broadcastMessages.get(0);
            assertEquals(targets, broadcast.receivers);
            assertEquals(message, broadcast.message);
        }

        @Test
        @DisplayName("should broadcast to empty target set")
        void shouldBroadcastToEmptyTargetSet() {
            Set<NodeId> emptyTargets = Set.of();
            SimulationMessage message = new SimulationMessage(
                    nodeId,
                    nodeId,
                    "TEST",
                    null
            );

            context.broadcast(emptyTargets, message);

            assertEquals(1, messagingPort.broadcastMessages.size());
            assertEquals(emptyTargets, messagingPort.broadcastMessages.get(0).receivers);
        }

        @Test
        @DisplayName("should reject null targets")
        void shouldRejectNullTargets() {
            SimulationMessage message = new SimulationMessage(
                    nodeId,
                    nodeId,
                    "TEST",
                    null
            );

            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> context.broadcast(null, message)
            );
            assertTrue(exception.getMessage().contains("targets must not be null"));
        }

        @Test
        @DisplayName("should reject null message in broadcast")
        void shouldRejectNullMessageInBroadcast() {
            Set<NodeId> targets = Set.of(new NodeId("node-2"));

            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> context.broadcast(targets, null)
            );
            assertTrue(exception.getMessage().contains("message must not be null"));
        }

        @Test
        @DisplayName("should not block on broadcast")
        void shouldNotBlockOnBroadcast() {
            Set<NodeId> targets = context.neighbors();
            SimulationMessage message = new SimulationMessage(nodeId, nodeId, "TEST", null);

            // Should complete immediately
            long startTime = System.nanoTime();
            context.broadcast(targets, message);
            long endTime = System.nanoTime();

            // Should take less than 10ms (non-blocking)
            long durationMs = (endTime - startTime) / 1_000_000;
            assertTrue(durationMs < 10, "broadcast() should not block (took " + durationMs + "ms)");
        }
    }

    @Nested
    @DisplayName("Transport Abstraction")
    class TransportAbstraction {

        @Test
        @DisplayName("should hide MessagingPort from algorithm")
        void shouldHideMessagingPortFromAlgorithm() {
            // Algorithm only sees NodeContext interface
            NodeContext algorithmContext = context;

            // Verify that MessagingPort is not exposed
            assertNotNull(algorithmContext.self());
            assertNotNull(algorithmContext.neighbors());

            // Algorithm can only send/broadcast, not access transport details
            NodeId target = new NodeId("node-2");
            SimulationMessage message = new SimulationMessage(nodeId, target, "TEST", null);
            algorithmContext.send(target, message);

            assertEquals(1, messagingPort.sentMessages.size());
        }

        @Test
        @DisplayName("should work with different MessagingPort implementations")
        void shouldWorkWithDifferentMessagingPortImplementations() {
            // Create context with different messaging port
            TestMessagingPort alternativePort = new TestMessagingPort();
            SimulationNodeContext alternativeContext = new SimulationNodeContext(
                    nodeId,
                    neighbors,
                    alternativePort
            );

            NodeId target = new NodeId("node-2");
            SimulationMessage message = new SimulationMessage(nodeId, target, "TEST", null);

            // Send via alternative context
            alternativeContext.send(target, message);

            // Should use alternative port
            assertEquals(0, messagingPort.sentMessages.size());
            assertEquals(1, alternativePort.sentMessages.size());
        }
    }

    /**
     * Test implementation of MessagingPort for testing purposes.
     */
    private static class TestMessagingPort implements MessagingPort {
        final List<SentMessage> sentMessages = new ArrayList<>();
        final List<BroadcastMessage> broadcastMessages = new ArrayList<>();

        @Override
        public void send(NodeId receiver, SimulationMessage message) {
            sentMessages.add(new SentMessage(receiver, message));
        }

        @Override
        public void broadcast(Set<NodeId> receivers, SimulationMessage message) {
            broadcastMessages.add(new BroadcastMessage(receivers, message));
        }

        @Override
        public void registerHandler(NodeId nodeId, MessageHandler handler) {
            // Not needed for NodeContext tests
        }

        @Override
        public void unregisterHandler(NodeId nodeId) {
            // Not needed for NodeContext tests
        }

        static class SentMessage {
            final NodeId receiver;
            final SimulationMessage message;

            SentMessage(NodeId receiver, SimulationMessage message) {
                this.receiver = receiver;
                this.message = message;
            }
        }

        static class BroadcastMessage {
            final Set<NodeId> receivers;
            final SimulationMessage message;

            BroadcastMessage(Set<NodeId> receivers, SimulationMessage message) {
                this.receivers = receivers;
                this.message = message;
            }
        }
    }
}

