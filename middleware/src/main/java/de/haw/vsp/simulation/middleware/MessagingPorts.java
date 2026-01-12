
package de.haw.vsp.simulation.middleware;

        import de.haw.vsp.simulation.middleware.codec.JacksonSimulationMessageCodec;
        import de.haw.vsp.simulation.middleware.inmemory.InMemoryMessagingPort;
        import de.haw.vsp.simulation.middleware.udp.UdpMessagingPort;

/**
 * Convenience factory methods for creating {@link MessagingPort} instances.
 *
 * <p>Engine/core should prefer depending on {@link MessagingPort} and choose an implementation
 * at wiring time.</p>
 */
public final class MessagingPorts {

    private MessagingPorts() {}

    /**
     * Best for tests / single-process runs.
     */
    public static MessagingPort inMemory() {
        return new InMemoryMessagingPort();
    }

    /**
     * UDP transport endpoint for a single local node.
     */
    public static UdpMessagingPort udp(NodeId localNodeId, TransportConfig config) {
        var codec = new JacksonSimulationMessageCodec();
        return new UdpMessagingPort(localNodeId, config, codec, codec);
    }
}
