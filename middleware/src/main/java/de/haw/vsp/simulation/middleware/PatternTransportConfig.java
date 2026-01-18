package de.haw.vsp.simulation.middleware;

import de.haw.vsp.simulation.core.NodeId;

import java.util.Locale;
import java.util.Objects;

/**
 * Scalable {@link TransportConfig} that resolves node addresses by a formula.
 *
 * Typical Docker/K8s pattern:
 *  - host = "node" + nodeId
 *  - port = 9000 (same inside every container)
 *
 * Template supports placeholder "{ID}".
 * Example: new PatternTransportConfig("node{ID}", 9000)
 */
public final class PatternTransportConfig implements TransportConfig {

    public static final String PLACEHOLDER = "{ID}";

    private final String hostTemplate;
    private final int port;
    private final boolean lowerCaseHost;

    /**
     * Uses a prefix + "{ID}" pattern, e.g. prefix "node" -> "node{ID}".
     */
    public PatternTransportConfig(String hostPrefix, int port) {
        this(hostPrefix + PLACEHOLDER, port, true);
    }

    /**
     * Uses an explicit template that must include "{ID}".
     */
    public PatternTransportConfig(String hostTemplate, int port, boolean lowerCaseHost) {
        this.hostTemplate = Objects.requireNonNull(hostTemplate, "hostTemplate").trim();
        if (!this.hostTemplate.contains(PLACEHOLDER)) {
            throw new IllegalArgumentException("hostTemplate must contain " + PLACEHOLDER + ", but was: " + hostTemplate);
        }
        if (port <= 0 || port > 65535) {
            throw new IllegalArgumentException("port must be in range 1..65535, but was: " + port);
        }
        this.port = port;
        this.lowerCaseHost = lowerCaseHost;
    }

    @Override
    public TransportAddress resolve(NodeId id) {
        if (id == null) return null;

        String host = hostTemplate.replace(PLACEHOLDER, id.value());
        if (lowerCaseHost) {
            host = host.toLowerCase(Locale.ROOT);
        }
        return new TransportAddress(host, port);
    }

    @Override
    public String toString() {
        return "PatternTransportConfig{hostTemplate='" + hostTemplate + "', port=" + port + ", lowerCaseHost=" + lowerCaseHost + "}";
    }
}
