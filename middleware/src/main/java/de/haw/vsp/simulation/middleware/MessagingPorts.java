package de.haw.vsp.simulation.middleware;

import de.haw.vsp.simulation.core.NodeId;
import de.haw.vsp.simulation.core.SimulationEventPublisher;
import de.haw.vsp.simulation.middleware.adapter.UdpAdapter;
import de.haw.vsp.simulation.middleware.adapter.VirtualAdapter;
import de.haw.vsp.simulation.middleware.codec.JacksonSimulationMessageCodec;
import de.haw.vsp.simulation.middleware.virtual.VirtualFaultConfig;

public final class MessagingPorts {
    private MessagingPorts() {}

    /** MW_MODE=virtual (single shared router port; many node senders allowed). */
    public static MessagingPort virtual() {
        var q = EnvQueueConfigs.fromSystemEnvironment();
        return virtual(null, q.outbound(), q.inbound(), VirtualFaultConfig.DISABLED);
    }

    public static MessagingPort virtual(SimulationEventPublisher publisher) {
        var q = EnvQueueConfigs.fromSystemEnvironment();
        return virtual(publisher, q.outbound(), q.inbound(), VirtualFaultConfig.DISABLED);
    }

    public static MessagingPort virtual(
            SimulationEventPublisher publisher,
            QueueConfig outbound,
            QueueConfig inboundPerReceiver,
            VirtualFaultConfig faults
    ) {
        var codec = new JacksonSimulationMessageCodec();
        var adapter = new VirtualAdapter(
                codec, codec,
                outbound, inboundPerReceiver,
                Math.max(2, Runtime.getRuntime().availableProcessors()),
                faults
        );

        // IMPORTANT: enforceLocalSender=false in virtual mode (shared port)
        return new MessagingPortImpl(adapter, publisher, false);
    }

    /** MW_MODE=udp-docker (one node per container; sender must be local node). */
    public static MessagingPort udpDocker(NodeId localNode, TransportConfig config, SimulationEventPublisher publisher) {
        var codec = new JacksonSimulationMessageCodec();
        var q = EnvQueueConfigs.fromSystemEnvironment();

        // NOTE: UdpAdapter ctor order here assumes (inboundConfig, outboundConfig)
        var adapter = new UdpAdapter(localNode, config, codec, codec, q.inbound(), q.outbound());
        return new MessagingPortImpl(adapter, publisher, true);
    }

    public static MessagingPort udpDocker(NodeId localNode, TransportConfig config) {
        return udpDocker(localNode, config, null);
    }
}
