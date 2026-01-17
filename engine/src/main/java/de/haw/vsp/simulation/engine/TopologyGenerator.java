package de.haw.vsp.simulation.engine;

import de.haw.vsp.simulation.core.NetworkConfig;
import de.haw.vsp.simulation.core.NodeId;
import de.haw.vsp.simulation.core.TopologyType;

import java.util.*;

/**
 * Utility class for generating network topologies based on NetworkConfig.
 *
 * Supports the following topology types:
 * - LINE: nodes connected in a line (0-1-2-3-...)
 * - RING: nodes connected in a ring (0-1-2-3-...-n-0)
 * - GRID: nodes connected in a 2D grid
 * - RANDOM: nodes connected randomly
 */
final class TopologyGenerator {

    private static final String NODE_PREFIX = "node-";
    private static final Random RANDOM = new Random();

    private TopologyGenerator() {
        // Utility class
    }

    /**
     * Generates a topology map based on the given network configuration.
     *
     * @param config the network configuration
     * @return a map from NodeId to its set of neighbor NodeIds
     * @throws IllegalArgumentException if config is null
     */
    static Map<NodeId, Set<NodeId>> generateTopology(NetworkConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }

        int nodeCount = config.nodeCount();
        TopologyType topologyType = config.topologyType();

        return switch (topologyType) {
            case LINE -> generateLineTopology(nodeCount);
            case RING -> generateRingTopology(nodeCount);
            case GRID -> generateGridTopology(nodeCount);
            case RANDOM -> generateRandomTopology(nodeCount);
        };
    }

    /**
     * Generates a line topology: 0-1-2-3-...-n
     *
     * Each node (except endpoints) has exactly 2 neighbors.
     * Endpoints have 1 neighbor.
     */
    private static Map<NodeId, Set<NodeId>> generateLineTopology(int nodeCount) {
        Map<NodeId, Set<NodeId>> topology = new HashMap<>();

        for (int i = 0; i < nodeCount; i++) {
            NodeId nodeId = new NodeId(NODE_PREFIX + i);
            Set<NodeId> neighbors = new HashSet<>();

            if (i > 0) {
                neighbors.add(new NodeId(NODE_PREFIX + (i - 1)));
            }
            if (i < nodeCount - 1) {
                neighbors.add(new NodeId(NODE_PREFIX + (i + 1)));
            }

            topology.put(nodeId, neighbors);
        }

        return topology;
    }

    /**
     * Generates a ring topology: 0-1-2-3-...-n-0
     *
     * Each node has exactly 2 neighbors (including wraparound).
     */
    private static Map<NodeId, Set<NodeId>> generateRingTopology(int nodeCount) {
        Map<NodeId, Set<NodeId>> topology = new HashMap<>();

        for (int i = 0; i < nodeCount; i++) {
            NodeId nodeId = new NodeId(NODE_PREFIX + i);
            Set<NodeId> neighbors = new HashSet<>();

            int prev = (i - 1 + nodeCount) % nodeCount;
            int next = (i + 1) % nodeCount;

            neighbors.add(new NodeId(NODE_PREFIX + prev));
            neighbors.add(new NodeId(NODE_PREFIX + next));

            topology.put(nodeId, neighbors);
        }

        return topology;
    }

    /**
     * Generates a grid topology.
     *
     * Nodes are arranged in a rectangular grid.
     * Each node has up to 4 neighbors (north, south, east, west).
     */
    private static Map<NodeId, Set<NodeId>> generateGridTopology(int nodeCount) {
        Map<NodeId, Set<NodeId>> topology = new HashMap<>();

        // Calculate grid dimensions (try to make it as square as possible)
        int rows = (int) Math.sqrt(nodeCount);
        int cols = (nodeCount + rows - 1) / rows;

        for (int i = 0; i < nodeCount; i++) {
            NodeId nodeId = new NodeId(NODE_PREFIX + i);
            Set<NodeId> neighbors = new HashSet<>();

            int row = i / cols;
            int col = i % cols;

            addGridNeighbors(neighbors, i, row, col, rows, cols, nodeCount);

            topology.put(nodeId, neighbors);
        }

        return topology;
    }

    /**
     * Adds all valid neighbors for a grid node.
     */
    private static void addGridNeighbors(Set<NodeId> neighbors, int nodeIndex, int row, int col,
                                         int rows, int cols, int nodeCount) {
        addNorthNeighbor(neighbors, row, col, cols, nodeCount);
        addSouthNeighbor(neighbors, row, col, rows, cols, nodeCount);
        addWestNeighbor(neighbors, col, nodeIndex);
        addEastNeighbor(neighbors, col, cols, nodeIndex, nodeCount);
    }

    /**
     * Adds north neighbor if valid.
     */
    private static void addNorthNeighbor(Set<NodeId> neighbors, int row, int col, int cols, int nodeCount) {
        if (row > 0) {
            int northIdx = (row - 1) * cols + col;
            if (northIdx < nodeCount) {
                neighbors.add(new NodeId(NODE_PREFIX + northIdx));
            }
        }
    }

    /**
     * Adds south neighbor if valid.
     */
    private static void addSouthNeighbor(Set<NodeId> neighbors, int row, int col, int rows, int cols, int nodeCount) {
        if (row < rows - 1) {
            int southIdx = (row + 1) * cols + col;
            if (southIdx < nodeCount) {
                neighbors.add(new NodeId(NODE_PREFIX + southIdx));
            }
        }
    }

    /**
     * Adds west neighbor if valid.
     */
    private static void addWestNeighbor(Set<NodeId> neighbors, int col, int nodeIndex) {
        if (col > 0) {
            neighbors.add(new NodeId(NODE_PREFIX + (nodeIndex - 1)));
        }
    }

    /**
     * Adds east neighbor if valid.
     */
    private static void addEastNeighbor(Set<NodeId> neighbors, int col, int cols, int nodeIndex, int nodeCount) {
        if (col < cols - 1 && nodeIndex + 1 < nodeCount) {
            neighbors.add(new NodeId(NODE_PREFIX + (nodeIndex + 1)));
        }
    }

    /**
     * Generates a random topology.
     *
     * Each node is connected to a random subset of other nodes.
     * Ensures that the graph is connected.
     */
    private static Map<NodeId, Set<NodeId>> generateRandomTopology(int nodeCount) {
        Map<NodeId, Set<NodeId>> topology = new HashMap<>();

        // Initialize all nodes with empty neighbor sets
        List<NodeId> nodeIds = new ArrayList<>();
        for (int i = 0; i < nodeCount; i++) {
            NodeId nodeId = new NodeId(NODE_PREFIX + i);
            nodeIds.add(nodeId);
            topology.put(nodeId, new HashSet<>());
        }

        // Ensure connectivity: create a spanning tree first
        for (int i = 1; i < nodeCount; i++) {
            int parentIdx = RANDOM.nextInt(i);
            addBidirectionalEdge(topology, nodeIds.get(i), nodeIds.get(parentIdx));
        }

        // Add additional random edges
        int additionalEdges = RANDOM.nextInt(nodeCount);
        for (int i = 0; i < additionalEdges; i++) {
            int idx1 = RANDOM.nextInt(nodeCount);
            int idx2 = RANDOM.nextInt(nodeCount);
            if (idx1 != idx2) {
                addBidirectionalEdge(topology, nodeIds.get(idx1), nodeIds.get(idx2));
            }
        }

        return topology;
    }

    /**
     * Adds a bidirectional edge between two nodes.
     */
    private static void addBidirectionalEdge(Map<NodeId, Set<NodeId>> topology, NodeId node1, NodeId node2) {
        topology.get(node1).add(node2);
        topology.get(node2).add(node1);
    }
}
