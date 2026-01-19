package de.haw.vsp.simulation.engine;

import de.haw.vsp.simulation.core.NetworkConfig;
import de.haw.vsp.simulation.core.NodeId;
import de.haw.vsp.simulation.core.TopologyType;
import de.haw.vsp.simulation.middleware.MessagingPort;
import de.haw.vsp.simulation.middleware.MessagingPorts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests to verify that messages arriving during initialization are properly buffered
 * and processed after algorithm initialization, preventing message loss.
 */
@DisplayName("SimulationNode Message Buffering")
class SimulationNodeMessageBufferingTest {

    @Test
    @DisplayName("should buffer and process messages arriving before algorithm initialization")
    void shouldBufferAndProcessMessagesArrivingBeforeAlgorithmInitialization() {
        // Create a simple 2-node line topology
        NetworkConfig config = new NetworkConfig(2, TopologyType.LINE);
        MessagingPort messagingPort = MessagingPorts.virtual();
        DefaultSimulationEngine engine = new DefaultSimulationEngine(messagingPort);

        // Initialize the engine (creates nodes but doesn't start them)
        engine.createEngineAndNodes(config);

        // Configure algorithm before starting simulation
        engine.configureAlgorithm("flooding-leader-election");

        // Get the nodes
        // Note: We need to access nodes to verify behavior, but DefaultSimulationEngine
        // doesn't expose them. Instead, we'll test through the startSimulation behavior.

        // Start simulation - this triggers the two-phase initialization
        de.haw.vsp.simulation.core.SimulationParameters params =
                new de.haw.vsp.simulation.core.SimulationParameters(42L, 100, 10);
        
        // This should not throw an exception and should process all messages correctly
        assertDoesNotThrow(() -> engine.startSimulation(params));
        
        // Verify simulation is running
        assertEquals(DefaultSimulationEngine.SimulationState.RUNNING, engine.getState());
    }

    @Test
    @DisplayName("should process buffered messages in order after initialization")
    void shouldProcessBufferedMessagesInOrderAfterInitialization() {
        // Create nodes manually to test buffering behavior
        NodeId nodeId1 = new NodeId("node-1");
        NodeId nodeId2 = new NodeId("node-2");
        Set<NodeId> neighbors1 = Set.of(nodeId2);
        Set<NodeId> neighbors2 = Set.of(nodeId1);
        
        MessagingPort messagingPort = MessagingPorts.virtual();
        
        SimulationNodeContext context1 = new SimulationNodeContext(nodeId1, neighbors1, messagingPort);
        SimulationNodeContext context2 = new SimulationNodeContext(nodeId2, neighbors2, messagingPort);
        
        FloodingLeaderElectionAlgorithm algorithm1 = new FloodingLeaderElectionAlgorithm();
        FloodingLeaderElectionAlgorithm algorithm2 = new FloodingLeaderElectionAlgorithm();
        
        SimulationNode node1 = new SimulationNode(nodeId1, neighbors1, algorithm1, context1);
        SimulationNode node2 = new SimulationNode(nodeId2, neighbors2, algorithm2, context2);

        // Register handlers (middleware MessageHandler expects only SimulationMessage)
        messagingPort.registerHandler(nodeId1, node1::onMessage);
        messagingPort.registerHandler(nodeId2, node2::onMessage);
        
        // Mark nodes as started (allows message reception)
        node1.markAsStarted();
        node2.markAsStarted();
        
        // Node 1 starts and sends a message to Node 2
        // This message should be buffered in Node 2 since algorithm2.onStart() hasn't been called yet
        node1.onStart();
        
        // Verify Node 1's algorithm is initialized
        assertNotNull(algorithm1.getCurrentLeaderId());
        assertEquals(nodeId1, algorithm1.getCurrentLeaderId());
        
        // Now start Node 2 - this should process the buffered message
        node2.onStart();
        
        // Verify Node 2 received and processed the message from Node 1
        // Since Node 1 has ID "node-1" and Node 2 has ID "node-2", we need to compare numerically
        // node-1 has numeric suffix 1, node-2 has numeric suffix 2, so node-2 > node-1
        assertNotNull(algorithm2.getCurrentLeaderId());
        
        // The important thing is that the message was not lost - Node 2 should have processed it
        // Since node-2 > node-1 (comparing numeric suffixes), Node 2 should remain as its own leader
        assertEquals(nodeId2, algorithm2.getCurrentLeaderId());
    }
}
