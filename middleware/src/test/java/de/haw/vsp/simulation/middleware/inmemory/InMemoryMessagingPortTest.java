package de.haw.vsp.simulation.middleware.inmemory;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import de.haw.vsp.simulation.middleware.NodeId;
import de.haw.vsp.simulation.middleware.SimulationMessage;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryMessagingPortTest {

    @Test
    void send_deliversToRegisteredHandler() throws Exception {
        var port = new InMemoryMessagingPort();

        var a = new NodeId("A");
        var b = new NodeId("B");

        var latch = new CountDownLatch(1);
        var received = new AtomicReference<SimulationMessage>();

        port.registerHandler(b, msg -> {
            received.set(msg);
            latch.countDown();
        });

        var msg = SimulationMessage.of(
                a,
                b,
                "PING",
                JsonNodeFactory.instance.objectNode().put("x", 1)
        );

        port.send(b, msg);

        assertTrue(latch.await(1, TimeUnit.SECONDS), "message not delivered");
        assertEquals("PING", received.get().type());
        assertEquals(a, received.get().sender());
        assertEquals(b, received.get().receiver());
    }

    @Test
    void broadcast_deliversToAllReceiversWithHandlers() throws Exception {
        var port = new InMemoryMessagingPort();

        var a = new NodeId("A");
        var b = new NodeId("B");
        var c = new NodeId("C");

        var latch = new CountDownLatch(2);

        port.registerHandler(b, msg -> latch.countDown());
        port.registerHandler(c, msg -> latch.countDown());

        var msg = SimulationMessage.of(a, null, "BCAST", JsonNodeFactory.instance.objectNode());

        port.broadcast(Set.of(b, c), msg);

        assertTrue(latch.await(1, TimeUnit.SECONDS), "broadcast not delivered to all");
    }

    @Test
    void unregister_preventsFurtherDelivery() throws Exception {
        var port = new InMemoryMessagingPort();

        var a = new NodeId("A");
        var b = new NodeId("B");

        var latch = new CountDownLatch(1);

        port.registerHandler(b, msg -> latch.countDown());
        port.unregisterHandler(b);

        var msg = SimulationMessage.of(a, b, "PING", JsonNodeFactory.instance.objectNode());
        port.send(b, msg);

        assertFalse(latch.await(200, TimeUnit.MILLISECONDS), "message should not be delivered after unregister");
    }
}
