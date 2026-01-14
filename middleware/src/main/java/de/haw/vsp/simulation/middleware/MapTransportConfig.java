package de.haw.vsp.simulation.middleware;

import de.haw.vsp.simulation.core.NodeId;

import java.util.Map;
import java.util.Objects;

/**
 * Simple immutable {@link TransportConfig} backed by a map.
 */
public final class MapTransportConfig implements TransportConfig {

    private final Map<NodeId, TransportAddress> map;

    public MapTransportConfig(Map<NodeId, TransportAddress> map) {
        this.map = Map.copyOf(Objects.requireNonNull(map, "map"));
    }

    @Override
    public TransportAddress resolve(NodeId id) {
        return map.get(id);
    }
}
