package de.haw.vsp.simulation.middleware.udp;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import de.haw.vsp.simulation.middleware.NodeId;
import de.haw.vsp.simulation.middleware.SimulationMessage;
import de.haw.vsp.simulation.middleware.TransportAddress;
import de.haw.vsp.simulation.middleware.TransportConfig;
import de.haw.vsp.simulation.middleware.codec.JacksonSimulationMessageCodec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.DatagramSocket;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class UdpMessagingPortTest {

    private UdpMessagingPort portA;
    private UdpMessagingPort portB;

    @AfterEach
    void tearDown() {
        if (portA != null) portA.close();
        if (portB != null) portB.close();
    }

    @Test
    void send_overUdp_deliversToReceiverHandler() throws Exception {
        var a = new NodeId("A");
        var b = new NodeId("B");

        int portNumA = freeUdpPort();
        int portNumB = freeUdpPort();

        TransportConfig config = new StaticTransportConfig(Map.of(
                a, new TransportAddress("127.0.0.1", portNumA),
                b, new TransportAddress("127.0.0.1", portNumB)
        ));

        var codec = new JacksonSimulationMessageCodec();
        portA = new UdpMessagingPort(a, config, codec, codec);
        portB = new UdpMessagingPort(b, config, codec, codec);

        var latch = new CountDownLatch(1);
        var received = new AtomicReference<SimulationMessage>();

        portB.registerHandler(b, msg -> {
            received.set(msg);
            latch.countDown();
        });

        var msg = SimulationMessage.of(
                a, b, "PING",
                JsonNodeFactory.instance.objectNode().put("hello", "world")
        );

        portA.send(b, msg);

        assertTrue(latch.await(2, TimeUnit.SECONDS), "UDP message not delivered");
        assertEquals("PING", received.get().type());
        assertEquals(a, received.get().sender());
        assertEquals(b, received.get().receiver());
    }

    @Test
    void broadcast_overUdp_deliversToBoth() throws Exception {
        var a = new NodeId("A");
        var b = new NodeId("B");
        var c = new NodeId("C");

        int portNumA = freeUdpPort();
        int portNumB = freeUdpPort();
        int portNumC = freeUdpPort();

        TransportConfig config = new StaticTransportConfig(Map.of(
                a, new TransportAddress("127.0.0.1", portNumA),
                b, new TransportAddress("127.0.0.1", portNumB),
                c, new TransportAddress("127.0.0.1", portNumC)
        ));

        var codec = new JacksonSimulationMessageCodec();
        portA = new UdpMessagingPort(a, config, codec, codec);
        portB = new UdpMessagingPort(b, config, codec, codec);
        var portC = new UdpMessagingPort(c, config, codec, codec);

        try {
            var latch = new CountDownLatch(2);
            portB.registerHandler(b, msg -> latch.countDown());
            portC.registerHandler(c, msg -> latch.countDown());

            var msg = SimulationMessage.of(a, null, "BCAST", JsonNodeFactory.instance.objectNode());
            portA.broadcast(Set.of(b, c), msg);

            assertTrue(latch.await(2, TimeUnit.SECONDS), "UDP broadcast not delivered to all");
        } finally {
            portC.close();
        }
    }

    /**
     * Asks the OS for a free UDP port, then releases it.
     * There is a tiny race, but it is very unlikely in tests and far better than hard-coded ports.
     */
    private static int freeUdpPort() throws IOException {
        try (DatagramSocket s = new DatagramSocket(0)) {
            return s.getLocalPort();
        }
    }

    static final class StaticTransportConfig implements TransportConfig {
        private final Map<NodeId, TransportAddress> map;

        StaticTransportConfig(Map<NodeId, TransportAddress> map) {
            this.map = Map.copyOf(map);
        }

        @Override
        public TransportAddress resolve(NodeId id) {
            return map.get(id);
        }
    }
}
