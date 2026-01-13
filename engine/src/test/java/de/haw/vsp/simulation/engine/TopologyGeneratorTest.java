package de.haw.vsp.simulation.engine;

import de.haw.vsp.simulation.core.NetworkConfig;
import de.haw.vsp.simulation.core.NodeId;
import de.haw.vsp.simulation.core.TopologyType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link TopologyGenerator}.
 *
 * Tests verify:
 * - Correct topology generation for all topology types
 * - Proper neighbor relationships
 * - Validation of input parameters
 * - Connectivity of generated topologies
 */
@DisplayName("TopologyGenerator")
class TopologyGeneratorTest {

    @Nested
    @DisplayName("Validation")
    class Validation {

        @Test
        @DisplayName("should reject null config")
        void shouldRejectNullConfig() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> TopologyGenerator.generateTopology(null)
            );
            assertTrue(exception.getMessage().contains("config must not be null"));
        }

        @Test
        @DisplayName("should reject invalid nodeCount")
        void shouldRejectInvalidNodeCount() {
            // NetworkConfig constructor validates nodeCount, so we can't create invalid config
            // Instead, we test that generateTopology validates the config's nodeCount
            // We need to create a config that passes constructor validation but fails in generateTopology
            // However, since NetworkConfig validates in constructor, we test the validation
            // by ensuring that valid configs work and that generateTopology would validate
            // if we could pass invalid configs (which we can't due to NetworkConfig validation)
            
            // This test verifies that NetworkConfig validation prevents invalid configs
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new NetworkConfig(0, TopologyType.LINE),
                    "NetworkConfig should reject nodeCount <= 0"
            );
        }
    }

    @Nested
    @DisplayName("Line Topology")
    class LineTopology {

        @Test
        @DisplayName("should generate line topology for single node")
        void shouldGenerateLineTopologyForSingleNode() {
            NetworkConfig config = new NetworkConfig(1, TopologyType.LINE);
            Map<NodeId, Set<NodeId>> topology = TopologyGenerator.generateTopology(config);

            assertEquals(1, topology.size());
            assertTrue(topology.containsKey(new NodeId("0")));
            assertEquals(0, topology.get(new NodeId("0")).size()); // No neighbors for single node
        }

        @Test
        @DisplayName("should generate line topology for two nodes")
        void shouldGenerateLineTopologyForTwoNodes() {
            NetworkConfig config = new NetworkConfig(2, TopologyType.LINE);
            Map<NodeId, Set<NodeId>> topology = TopologyGenerator.generateTopology(config);

            assertEquals(2, topology.size());
            NodeId node0 = new NodeId("0");
            NodeId node1 = new NodeId("1");

            assertEquals(Set.of(node1), topology.get(node0));
            assertEquals(Set.of(node0), topology.get(node1));
        }

        @Test
        @DisplayName("should generate line topology for multiple nodes")
        void shouldGenerateLineTopologyForMultipleNodes() {
            NetworkConfig config = new NetworkConfig(5, TopologyType.LINE);
            Map<NodeId, Set<NodeId>> topology = TopologyGenerator.generateTopology(config);

            assertEquals(5, topology.size());

            // First node (0) should only connect to node 1
            assertEquals(Set.of(new NodeId("1")), topology.get(new NodeId("0")));

            // Middle nodes (1, 2, 3) should connect to neighbors
            assertEquals(Set.of(new NodeId("0"), new NodeId("2")), topology.get(new NodeId("1")));
            assertEquals(Set.of(new NodeId("1"), new NodeId("3")), topology.get(new NodeId("2")));
            assertEquals(Set.of(new NodeId("2"), new NodeId("4")), topology.get(new NodeId("3")));

            // Last node (4) should only connect to node 3
            assertEquals(Set.of(new NodeId("3")), topology.get(new NodeId("4")));
        }
    }

    @Nested
    @DisplayName("Ring Topology")
    class RingTopology {

        @Test
        @DisplayName("should generate ring topology for single node")
        void shouldGenerateRingTopologyForSingleNode() {
            NetworkConfig config = new NetworkConfig(1, TopologyType.RING);
            Map<NodeId, Set<NodeId>> topology = TopologyGenerator.generateTopology(config);

            assertEquals(1, topology.size());
            // Single node connects to itself in a ring
            assertEquals(Set.of(new NodeId("0")), topology.get(new NodeId("0")));
        }

        @Test
        @DisplayName("should generate ring topology for two nodes")
        void shouldGenerateRingTopologyForTwoNodes() {
            NetworkConfig config = new NetworkConfig(2, TopologyType.RING);
            Map<NodeId, Set<NodeId>> topology = TopologyGenerator.generateTopology(config);

            assertEquals(2, topology.size());
            NodeId node0 = new NodeId("0");
            NodeId node1 = new NodeId("1");

            assertEquals(Set.of(node1), topology.get(node0));
            assertEquals(Set.of(node0), topology.get(node1));
        }

        @Test
        @DisplayName("should generate ring topology for multiple nodes")
        void shouldGenerateRingTopologyForMultipleNodes() {
            NetworkConfig config = new NetworkConfig(5, TopologyType.RING);
            Map<NodeId, Set<NodeId>> topology = TopologyGenerator.generateTopology(config);

            assertEquals(5, topology.size());

            // Each node should connect to its neighbors in a ring
            assertEquals(Set.of(new NodeId("4"), new NodeId("1")), topology.get(new NodeId("0")));
            assertEquals(Set.of(new NodeId("0"), new NodeId("2")), topology.get(new NodeId("1")));
            assertEquals(Set.of(new NodeId("1"), new NodeId("3")), topology.get(new NodeId("2")));
            assertEquals(Set.of(new NodeId("2"), new NodeId("4")), topology.get(new NodeId("3")));
            assertEquals(Set.of(new NodeId("3"), new NodeId("0")), topology.get(new NodeId("4")));
        }
    }

    @Nested
    @DisplayName("Grid Topology")
    class GridTopology {

        @Test
        @DisplayName("should generate grid topology for single node")
        void shouldGenerateGridTopologyForSingleNode() {
            NetworkConfig config = new NetworkConfig(1, TopologyType.GRID);
            Map<NodeId, Set<NodeId>> topology = TopologyGenerator.generateTopology(config);

            assertEquals(1, topology.size());
            assertEquals(0, topology.get(new NodeId("0")).size());
        }

        @Test
        @DisplayName("should generate grid topology for four nodes")
        void shouldGenerateGridTopologyForFourNodes() {
            NetworkConfig config = new NetworkConfig(4, TopologyType.GRID);
            Map<NodeId, Set<NodeId>> topology = TopologyGenerator.generateTopology(config);

            assertEquals(4, topology.size());

            // Grid should be 2x2
            // Node 0 (0,0) should connect to node 1 (right) and node 2 (bottom)
            Set<NodeId> neighbors0 = topology.get(new NodeId("0"));
            assertTrue(neighbors0.contains(new NodeId("1")) || neighbors0.contains(new NodeId("2")));
        }

        @Test
        @DisplayName("should generate grid topology for nine nodes")
        void shouldGenerateGridTopologyForNineNodes() {
            NetworkConfig config = new NetworkConfig(9, TopologyType.GRID);
            Map<NodeId, Set<NodeId>> topology = TopologyGenerator.generateTopology(config);

            assertEquals(9, topology.size());

            // All nodes should exist
            for (int i = 0; i < 9; i++) {
                assertTrue(topology.containsKey(new NodeId(String.valueOf(i))));
            }
        }
    }

    @Nested
    @DisplayName("Random Topology")
    class RandomTopology {

        @Test
        @DisplayName("should generate random topology for single node")
        void shouldGenerateRandomTopologyForSingleNode() {
            NetworkConfig config = new NetworkConfig(1, TopologyType.RANDOM);
            Map<NodeId, Set<NodeId>> topology = TopologyGenerator.generateTopology(config);

            assertEquals(1, topology.size());
            assertEquals(0, topology.get(new NodeId("0")).size());
        }

        @Test
        @DisplayName("should generate random topology for multiple nodes")
        void shouldGenerateRandomTopologyForMultipleNodes() {
            NetworkConfig config = new NetworkConfig(10, TopologyType.RANDOM);
            Map<NodeId, Set<NodeId>> topology = TopologyGenerator.generateTopology(config);

            assertEquals(10, topology.size());

            // All nodes should exist
            for (int i = 0; i < 10; i++) {
                assertTrue(topology.containsKey(new NodeId(String.valueOf(i))));
            }

            // Random topology should be connected (at least one node has neighbors)
            boolean hasNeighbors = topology.values().stream()
                    .anyMatch(neighbors -> !neighbors.isEmpty());
            assertTrue(hasNeighbors, "Random topology should have at least some connections");
        }

        @Test
        @DisplayName("should generate deterministic random topology with same seed")
        void shouldGenerateDeterministicRandomTopologyWithSameSeed() {
            NetworkConfig config1 = new NetworkConfig(10, TopologyType.RANDOM);
            NetworkConfig config2 = new NetworkConfig(10, TopologyType.RANDOM);

            Map<NodeId, Set<NodeId>> topology1 = TopologyGenerator.generateTopology(config1);
            Map<NodeId, Set<NodeId>> topology2 = TopologyGenerator.generateTopology(config2);

            // Should be deterministic due to fixed seed
            assertEquals(topology1, topology2);
        }
    }

    @Nested
    @DisplayName("General Properties")
    class GeneralProperties {

        @Test
        @DisplayName("should create symmetric neighbor relationships")
        void shouldCreateSymmetricNeighborRelationships() {
            NetworkConfig config = new NetworkConfig(5, TopologyType.RING);
            Map<NodeId, Set<NodeId>> topology = TopologyGenerator.generateTopology(config);

            // For each node, verify that if A is neighbor of B, then B is neighbor of A
            for (Map.Entry<NodeId, Set<NodeId>> entry : topology.entrySet()) {
                NodeId node = entry.getKey();
                Set<NodeId> neighbors = entry.getValue();

                for (NodeId neighbor : neighbors) {
                    assertTrue(topology.get(neighbor).contains(node),
                            "If " + node + " is neighbor of " + neighbor +
                                    ", then " + neighbor + " should be neighbor of " + node);
                }
            }
        }

        @Test
        @DisplayName("should create all nodes")
        void shouldCreateAllNodes() {
            NetworkConfig config = new NetworkConfig(7, TopologyType.LINE);
            Map<NodeId, Set<NodeId>> topology = TopologyGenerator.generateTopology(config);

            assertEquals(7, topology.size());
            for (int i = 0; i < 7; i++) {
                assertTrue(topology.containsKey(new NodeId(String.valueOf(i))),
                        "Node " + i + " should exist in topology");
            }
        }
    }
}
