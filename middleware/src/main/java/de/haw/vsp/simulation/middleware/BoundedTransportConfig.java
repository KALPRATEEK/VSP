package de.haw.vsp.simulation.middleware;

import de.haw.vsp.simulation.core.NodeId;

import java.util.Objects;

/**
 * Wraps another {@link TransportConfig} and returns null for node ids outside the allowed range.
 */
public final class BoundedTransportConfig implements TransportConfig {

    private final TransportConfig delegate;
    private final NodeRange range;

    public BoundedTransportConfig(TransportConfig delegate, NodeRange range) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.range = Objects.requireNonNull(range, "range");
    }

    @Override
    public TransportAddress resolve(NodeId id) {
        if (!range.contains(id)) {
            return null;
        }
        return delegate.resolve(id);
    }

    @Override
    public String toString() {
        return "BoundedTransportConfig{delegate=" + delegate + ", range=" + range + "}";
    }
}
