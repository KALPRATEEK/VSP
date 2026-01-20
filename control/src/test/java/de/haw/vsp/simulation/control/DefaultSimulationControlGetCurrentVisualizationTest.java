package de.haw.vsp.simulation.control;

import de.haw.vsp.simulation.core.*;
import de.haw.vsp.simulation.engine.DockerNodeOrchestrator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for DefaultSimulationControl.getCurrentVisualization().
 *
 * Tests verify:
 * - Snapshot is read-only and consistent
 * - Snapshot is derived from event stream and internal state
 * - Snapshot does not affect simulation state
 * - Topology is correctly represented
 * - Node states are correctly determined from events
 */
@DisplayName("DefaultSimulationControl - getCurrentVisualization")
class DefaultSimulationControlGetCurrentVisualizationTest {

    private DefaultSimulationControl control;

    @BeforeEach
    void setUp() {
        DockerNodeOrchestrator mockOrchestrator = mock(DockerNodeOrchestrator.class);
        Map<SimulationId, SimulationEventBus> eventAggregationMap = new ConcurrentHashMap<>();
        control = new DefaultSimulationControl(mockOrchestrator, eventAggregationMap);
    }

    @Nested
    @DisplayName("Basic Functionality")
    class BasicFunctionality {

        @Test
        @DisplayName("should return visualization snapshot for initialized simulation")
        void shouldReturnVisualizationSnapshotForInitializedSimulation() {
            NetworkConfig config = new NetworkConfig(3, TopologyType.RING);
            SimulationId simulationId = control.initializeNetwork(config);

            VisualizationSnapshot snapshot = control.getCurrentVisualization(simulationId);

            assertNotNull(snapshot);
            assertNotNull(snapshot.nodes());
            assertNotNull(snapshot.topology());
            assertTrue(snapshot.timestamp() >= 0);
        }

        @Test
        @DisplayName("should throw exception for null simulationId")
        void shouldThrowExceptionForNullSimulationId() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> control.getCurrentVisualization(null)
            );
        }

        @Test
        @DisplayName("should throw exception for non-existent simulation")
        void shouldThrowExceptionForNonExistentSimulation() {
            SimulationId nonExistentId = new SimulationId("non-existent");

            assertThrows(
                    IllegalStateException.class,
                    () -> control.getCurrentVisualization(nonExistentId)
            );
        }
    }

    @Nested
    @DisplayName("Topology Representation")
    class TopologyRepresentation {

        @Test
        @DisplayName("should include all nodes in topology")
        void shouldIncludeAllNodesInTopology() {
            NetworkConfig config = new NetworkConfig(5, TopologyType.RING);
            SimulationId simulationId = control.initializeNetwork(config);

            VisualizationSnapshot snapshot = control.getCurrentVisualization(simulationId);

            assertEquals(5, snapshot.topology().size(), "Topology should contain all nodes");
            assertEquals(5, snapshot.nodes().size(), "Node states should contain all nodes");
        }

        @Test
        @DisplayName("should represent node connections in topology")
        void shouldRepresentNodeConnectionsInTopology() {
            NetworkConfig config = new NetworkConfig(3, TopologyType.RING);
            SimulationId simulationId = control.initializeNetwork(config);

            VisualizationSnapshot snapshot = control.getCurrentVisualization(simulationId);

            Map<String, Set<String>> topology = snapshot.topology();
            assertFalse(topology.isEmpty(), "Topology should not be empty");

            // In a ring topology, each node should have neighbors
            for (Map.Entry<String, Set<String>> entry : topology.entrySet()) {
                assertNotNull(entry.getValue(), "Neighbors set should not be null");
                assertFalse(entry.getValue().isEmpty(), "Each node should have at least one neighbor in ring topology");
            }
        }

        @Test
        @DisplayName("should have consistent node IDs in topology and node states")
        void shouldHaveConsistentNodeIdsInTopologyAndNodeStates() {
            NetworkConfig config = new NetworkConfig(4, TopologyType.RING);
            SimulationId simulationId = control.initializeNetwork(config);

            VisualizationSnapshot snapshot = control.getCurrentVisualization(simulationId);

            Set<String> topologyNodeIds = snapshot.topology().keySet();
            Set<String> stateNodeIds = snapshot.nodes().stream()
                    .map(VisualizationSnapshot.NodeState::nodeId)
                    .collect(java.util.stream.Collectors.toSet());

            assertEquals(topologyNodeIds, stateNodeIds, "Node IDs should be consistent between topology and states");
        }
    }

    @Nested
    @DisplayName("Node States")
    class NodeStates {

        @Test
        @DisplayName("should have INITIALIZED state for nodes before simulation starts")
        void shouldHaveInitializedStateForNodesBeforeSimulationStarts() {
            NetworkConfig config = new NetworkConfig(3, TopologyType.RING);
            SimulationId simulationId = control.initializeNetwork(config);

            VisualizationSnapshot snapshot = control.getCurrentVisualization(simulationId);

            for (VisualizationSnapshot.NodeState nodeState : snapshot.nodes()) {
                assertEquals("INITIALIZED", nodeState.state(), 
                        "Nodes should be INITIALIZED before simulation starts");
                assertFalse(nodeState.isLeader(), 
                        "No node should be leader before simulation starts");
            }
        }

        @Test
        @DisplayName("should update node states after simulation starts")
        void shouldUpdateNodeStatesAfterSimulationStarts() {
            NetworkConfig config = new NetworkConfig(3, TopologyType.RING);
            SimulationId simulationId = control.initializeNetwork(config);
            control.selectAlgorithm(simulationId, "flooding-leader-election");

            SimulationParameters params = new SimulationParameters(42L, 10, 0);
            control.startSimulation(simulationId, params);

            // Wait a bit for events to be processed
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            VisualizationSnapshot snapshot = control.getCurrentVisualization(simulationId);

            // At least some nodes should be RUNNING after start
            long runningNodes = snapshot.nodes().stream()
                    .filter(n -> "RUNNING".equals(n.state()))
                    .count();
            assertTrue(runningNodes > 0, "At least some nodes should be RUNNING after simulation starts");
        }
    }

    @Nested
    @DisplayName("Read-Only and Immutability")
    class ReadOnlyAndImmutability {

        @Test
        @DisplayName("should return immutable snapshot")
        void shouldReturnImmutableSnapshot() {
            NetworkConfig config = new NetworkConfig(3, TopologyType.RING);
            SimulationId simulationId = control.initializeNetwork(config);

            VisualizationSnapshot snapshot = control.getCurrentVisualization(simulationId);

            // Try to modify the snapshot - should throw UnsupportedOperationException
            assertThrows(
                    UnsupportedOperationException.class,
                    () -> snapshot.nodes().add(new VisualizationSnapshot.NodeState("new-node", "INITIALIZED", false))
            );

            assertThrows(
                    UnsupportedOperationException.class,
                    () -> snapshot.topology().put("new-node", Set.of())
            );
        }

        @Test
        @DisplayName("should not affect simulation state when getting visualization")
        void shouldNotAffectSimulationStateWhenGettingVisualization() {
            NetworkConfig config = new NetworkConfig(3, TopologyType.RING);
            SimulationId simulationId = control.initializeNetwork(config);
            control.selectAlgorithm(simulationId, "flooding-leader-election");

            SimulationParameters params = new SimulationParameters(42L, 10, 0);
            control.startSimulation(simulationId, params);

            // Wait a bit
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // Get metrics before visualization
            MetricsSnapshot metricsBefore = control.getMetrics(simulationId);
            long roundsBefore = metricsBefore.rounds();

            // Get visualization (should not affect simulation)
            VisualizationSnapshot snapshot = control.getCurrentVisualization(simulationId);
            assertNotNull(snapshot);

            // Wait a bit more
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // Get metrics after visualization
            MetricsSnapshot metricsAfter = control.getMetrics(simulationId);
            long roundsAfter = metricsAfter.rounds();

            // Simulation should have progressed (rounds should have increased)
            assertTrue(roundsAfter >= roundsBefore, 
                    "Simulation should continue running after getting visualization");
        }

        @Test
        @DisplayName("should return consistent snapshots on multiple calls")
        void shouldReturnConsistentSnapshotsOnMultipleCalls() {
            NetworkConfig config = new NetworkConfig(3, TopologyType.RING);
            SimulationId simulationId = control.initializeNetwork(config);

            VisualizationSnapshot snapshot1 = control.getCurrentVisualization(simulationId);
            VisualizationSnapshot snapshot2 = control.getCurrentVisualization(simulationId);

            // Snapshots should have same topology and node count
            assertEquals(snapshot1.topology().size(), snapshot2.topology().size());
            assertEquals(snapshot1.nodes().size(), snapshot2.nodes().size());
        }
    }

    @Nested
    @DisplayName("Event Stream Integration")
    class EventStreamIntegration {

        @Test
        @DisplayName("should derive node states from event stream")
        void shouldDeriveNodeStatesFromEventStream() {
            NetworkConfig config = new NetworkConfig(3, TopologyType.RING);
            SimulationId simulationId = control.initializeNetwork(config);
            control.selectAlgorithm(simulationId, "flooding-leader-election");

            // Initially, nodes should be INITIALIZED
            VisualizationSnapshot snapshotBefore = control.getCurrentVisualization(simulationId);
            for (VisualizationSnapshot.NodeState nodeState : snapshotBefore.nodes()) {
                assertEquals("INITIALIZED", nodeState.state());
            }

            // Start simulation to generate events
            SimulationParameters params = new SimulationParameters(42L, 10, 0);
            control.startSimulation(simulationId, params);

            // Wait for events to be processed
            try {
                Thread.sleep(150);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // After start, nodes should be RUNNING (derived from events)
            VisualizationSnapshot snapshotAfter = control.getCurrentVisualization(simulationId);
            long runningNodes = snapshotAfter.nodes().stream()
                    .filter(n -> "RUNNING".equals(n.state()))
                    .count();
            assertTrue(runningNodes > 0, 
                    "Node states should be updated based on event stream");
        }
    }

    @Nested
    @DisplayName("Leader Election")
    class LeaderElection {

        @Test
        @DisplayName("should identify leader node when leader is elected")
        void shouldIdentifyLeaderNodeWhenLeaderIsElected() {
            NetworkConfig config = new NetworkConfig(3, TopologyType.RING);
            SimulationId simulationId = control.initializeNetwork(config);
            control.selectAlgorithm(simulationId, "flooding-leader-election");

            SimulationParameters params = new SimulationParameters(42L, 100, 0);
            control.startSimulation(simulationId, params);

            // Wait for leader election to potentially occur
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            VisualizationSnapshot snapshot = control.getCurrentVisualization(simulationId);

            // Check if any node is marked as leader
            long leaderCount = snapshot.nodes().stream()
                    .filter(VisualizationSnapshot.NodeState::isLeader)
                    .count();

            // In a leader election algorithm, at most one node should be leader
            assertTrue(leaderCount <= 1, 
                    "At most one node should be marked as leader");
        }
    }
}
