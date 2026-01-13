package de.haw.vsp.simulation.engine;

import de.haw.vsp.simulation.core.NetworkConfig;
import de.haw.vsp.simulation.core.NodeId;
import de.haw.vsp.simulation.core.TopologyType;

import java.util.*;

/**
 * Utility class for generating network topologies.
 *
 * Creates node IDs and neighbor relationships based on NetworkConfig.
 * Supports LINE, RING, GRID, and RANDOM topology types.
 */
public class TopologyGenerator {

    /**
     * Generates a topology map from a NetworkConfig.
     *
     * The returned map contains an entry for each node ID, mapping it to
     * its set of neighbor node IDs according to the specified topology type.
     *
     * @param config the network configuration (must not be null)
     * @return a map from node ID to set of neighbor node IDs
     * @throws IllegalArgumentException if config is null or invalid
     */
    public static Map<NodeId, Set<NodeId>> generateTopology(NetworkConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }

        int nodeCount = config.nodeCount();
        TopologyType topologyType = config.topologyType();

        if (nodeCount <= 0) {
            throw new IllegalArgumentException("nodeCount must be greater than 0, but was: " + nodeCount);
        }
        if (topologyType == null) {
            throw new IllegalArgumentException("topologyType must not be null");
        }

        // Generate node IDs
        List<NodeId> nodeIds = new ArrayList<>();
        for (int i = 0; i < nodeCount; i++) {
            nodeIds.add(new NodeId(String.valueOf(i)));
        }

        // Generate topology based on type
        Map<NodeId, Set<NodeId>> topology = new HashMap<>();
        switch (topologyType) {
            case LINE:
                generateLineTopology(nodeIds, topology);
                break;
            case RING:
                generateRingTopology(nodeIds, topology);
                break;
            case GRID:
                generateGridTopology(nodeIds, topology);
                break;
            case RANDOM:
                generateRandomTopology(nodeIds, topology);
                break;
            default:
                throw new IllegalArgumentException("Unsupported topology type: " + topologyType);
        }

        return topology;
    }

    /**
     * Generates a line topology: nodes connected in a linear chain.
     * Node 0 connects to node 1, node 1 connects to nodes 0 and 2, etc.
     */
    private static void generateLineTopology(List<NodeId> nodeIds, Map<NodeId, Set<NodeId>> topology) {
        for (int i = 0; i < nodeIds.size(); i++) {
            Set<NodeId> neighbors = new HashSet<>();
            if (i > 0) {
                neighbors.add(nodeIds.get(i - 1));
            }
            if (i < nodeIds.size() - 1) {
                neighbors.add(nodeIds.get(i + 1));
            }
            topology.put(nodeIds.get(i), neighbors);
        }
    }

    /**
     * Generates a ring topology: nodes connected in a circular chain.
     * Each node connects to its immediate neighbors, with the last node
     * connecting back to the first.
     */
    private static void generateRingTopology(List<NodeId> nodeIds, Map<NodeId, Set<NodeId>> topology) {
        for (int i = 0; i < nodeIds.size(); i++) {
            Set<NodeId> neighbors = new HashSet<>();
            int prev = (i - 1 + nodeIds.size()) % nodeIds.size();
            int next = (i + 1) % nodeIds.size();
            neighbors.add(nodeIds.get(prev));
            neighbors.add(nodeIds.get(next));
            topology.put(nodeIds.get(i), neighbors);
        }
    }

    /**
     * Generates a grid topology: nodes arranged in a 2D grid.
     * For simplicity, we create a square grid (or as close as possible).
     * Each node connects to its horizontal and vertical neighbors.
     */
    private static void generateGridTopology(List<NodeId> nodeIds, Map<NodeId, Set<NodeId>> topology) {
        int nodeCount = nodeIds.size();
        int cols = (int) Math.ceil(Math.sqrt(nodeCount));
        int rows = (int) Math.ceil((double) nodeCount / cols);

        for (int i = 0; i < nodeCount; i++) {
            Set<NodeId> neighbors = new HashSet<>();
            int row = i / cols;
            int col = i % cols;

            // Left neighbor
            if (col > 0 && (row * cols + col - 1) < nodeCount) {
                neighbors.add(nodeIds.get(row * cols + col - 1));
            }
            // Right neighbor
            if (col < cols - 1 && (row * cols + col + 1) < nodeCount) {
                neighbors.add(nodeIds.get(row * cols + col + 1));
            }
            // Top neighbor
            if (row > 0 && ((row - 1) * cols + col) < nodeCount) {
                neighbors.add(nodeIds.get((row - 1) * cols + col));
            }
            // Bottom neighbor
            if (row < rows - 1 && ((row + 1) * cols + col) < nodeCount) {
                neighbors.add(nodeIds.get((row + 1) * cols + col));
            }

            topology.put(nodeIds.get(i), neighbors);
        }
    }

    /**
     * Generates a random topology: each node connects to a random subset of other nodes.
     * For simplicity, we ensure connectivity by creating a minimum spanning tree first,
     * then adding random edges.
     */
    private static void generateRandomTopology(List<NodeId> nodeIds, Map<NodeId, Set<NodeId>> topology) {
        Random random = new Random(42); // Fixed seed for reproducibility

        // Initialize all nodes with empty neighbor sets
        for (NodeId nodeId : nodeIds) {
            topology.put(nodeId, new HashSet<>());
        }

        // First, create a minimum spanning tree to ensure connectivity
        // Use a simple approach: connect each node to a random previous node
        for (int i = 1; i < nodeIds.size(); i++) {
            int connectTo = random.nextInt(i);
            topology.get(nodeIds.get(i)).add(nodeIds.get(connectTo));
            topology.get(nodeIds.get(connectTo)).add(nodeIds.get(i));
        }

        // Then add random edges (each node has a 30% chance to connect to each other node)
        for (int i = 0; i < nodeIds.size(); i++) {
            for (int j = i + 1; j < nodeIds.size(); j++) {
                if (random.nextDouble() < 0.3) {
                    topology.get(nodeIds.get(i)).add(nodeIds.get(j));
                    topology.get(nodeIds.get(j)).add(nodeIds.get(i));
                }
            }
        }
    }
}
