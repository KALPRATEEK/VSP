package de.haw.vsp.simulation.middleware;

import de.haw.vsp.simulation.core.NodeId;
import de.haw.vsp.simulation.core.SimulationEventPublisher;
import de.haw.vsp.simulation.middleware.adapter.InMemoryAdapter;
import de.haw.vsp.simulation.middleware.adapter.UdpAdapter;
import de.haw.vsp.simulation.middleware.codec.JacksonSimulationMessageCodec;

/**
 * Factory for constructing middleware ports with appropriate transport adapters.
 */
public final class MessagingPorts {

    private MessagingPorts() {}

    public static MessagingPort inMemory(NodeId localNode, SimulationEventPublisher publisher) {
        return new MessagingPortImpl(new InMemoryAdapter(localNode), publisher);
    }

    public static MessagingPort inMemory(NodeId localNode) {
        return inMemory(localNode, null);
    }

    public static MessagingPort udp(NodeId localNode, TransportConfig config, SimulationEventPublisher publisher) {
        var codec = new JacksonSimulationMessageCodec();
        var adapter = new UdpAdapter(localNode, config, codec, codec);
        return new MessagingPortImpl(adapter, publisher);
    }

    public static MessagingPort udp(NodeId localNode, TransportConfig config) {
        return udp(localNode, config, null);
    }
}

