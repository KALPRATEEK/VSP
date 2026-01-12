package de.haw.vsp.simulation.middleware;

/**
 * Resolves node ids to concrete transport addresses.
 * <p>
 * Implementations may use static tables, config files, environment variables,
 * or service discovery.
 */
public interface TransportConfig {

    TransportAddress resolve(NodeId id);
}
