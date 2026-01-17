package de.haw.vsp.simulation.middleware.adapter;

import de.haw.vsp.simulation.core.NodeId;
import de.haw.vsp.simulation.core.SimulationMessage;
import de.haw.vsp.simulation.middleware.TransportAddress;
import de.haw.vsp.simulation.middleware.TransportConfig;
import de.haw.vsp.simulation.middleware.codec.MessageCodecException;
import de.haw.vsp.simulation.middleware.codec.SimulationMessageDeserializer;
import de.haw.vsp.simulation.middleware.codec.SimulationMessageSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.*;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * UDP-based {@link TransportAdapter}.
 */
public final class UdpAdapter implements TransportAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(UdpAdapter.class);
    private static final int MAX_DATAGRAM_BYTES = 64 * 1024;

    private final NodeId localNode;
    private final TransportConfig config;
    private final SimulationMessageSerializer serializer;
    private final SimulationMessageDeserializer deserializer;

    private volatile ReceiveCallback callback;

    private final DatagramSocket socket;
    private final ExecutorService receiver;
    private final ExecutorService sender;

    private final AtomicBoolean running = new AtomicBoolean(true);

    public UdpAdapter(NodeId localNode,
                      TransportConfig config,
                      SimulationMessageSerializer serializer,
                      SimulationMessageDeserializer deserializer) {
        this.localNode = Objects.requireNonNull(localNode);
        this.config = Objects.requireNonNull(config);
        this.serializer = Objects.requireNonNull(serializer);
        this.deserializer = Objects.requireNonNull(deserializer);

        // now localNode is definitely initialized, safe to use in thread names
        this.receiver = Executors.newSingleThreadExecutor(
                r -> new Thread(r, "udp-adapter-recv-" + this.localNode)
        );
        this.sender = new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.MILLISECONDS,
                new SynchronousQueue<>(),
                r -> new Thread(r, "udp-adapter-send-" + this.localNode),
                new ThreadPoolExecutor.DiscardPolicy()
        );

        TransportAddress addr = config.resolve(this.localNode);
        if (addr == null) {
            throw new IllegalArgumentException("No transport address for " + this.localNode);
        }

        try {
            this.socket = new DatagramSocket(new InetSocketAddress(addr.host(), addr.port()));
        } catch (SocketException e) {
            throw new IllegalStateException("Failed to bind UDP socket for " + this.localNode, e);
        }

        startReceiverLoop();
    }

    @Override
    public void send(SimulationMessage message) {
        NodeId receiver = message.receiver();
        TransportAddress addr = config.resolve(receiver);
        if (addr == null) {
            LOG.debug("Drop message to {} (unknown address)", receiver);
            return;
        }

        final byte[] bytes;
        try {
            bytes = serializer.serialize(message);
        } catch (MessageCodecException e) {
            LOG.debug("Serialization failed: {}", e.getMessage());
            return;
        }

        if (bytes.length > MAX_DATAGRAM_BYTES) {
            LOG.debug("Drop oversized datagram ({} bytes)", bytes.length);
            return;
        }

        try {
            sender.execute(() -> doSend(addr, bytes));
        } catch (RejectedExecutionException e) {
            LOG.debug("Drop message (sender busy)");
        }
    }

    private void doSend(TransportAddress addr, byte[] bytes) {
        try {
            DatagramPacket packet = new DatagramPacket(
                    bytes, bytes.length,
                    InetAddress.getByName(addr.host()), addr.port());
            socket.send(packet);
        } catch (IOException e) {
            LOG.debug("UDP send failed: {}", e.getMessage());
        }
    }

    private void startReceiverLoop() {
        receiver.submit(() -> {
            byte[] buf = new byte[MAX_DATAGRAM_BYTES];
            DatagramPacket packet = new DatagramPacket(buf, buf.length);

            while (running.get()) {
                try {
                    socket.receive(packet);
                    byte[] data = new byte[packet.getLength()];
                    System.arraycopy(packet.getData(), packet.getOffset(), data, 0, packet.getLength());

                    SimulationMessage msg;
                    try {
                        msg = deserializer.deserialize(data);
                    } catch (MessageCodecException e) {
                        LOG.debug("Decode error: {}", e.getMessage());
                        continue;
                    }

                    if (callback != null) callback.onMessage(msg);

                } catch (IOException e) {
                    if (running.get()) LOG.debug("UDP receive error: {}", e.getMessage());
                } finally {
                    packet.setLength(buf.length);
                }
            }
        });
    }

    @Override
    public void onReceive(ReceiveCallback callback) {
        this.callback = callback;
    }

    @Override
    public NodeId localNode() {
        return localNode;
    }

    @Override
    public void close() {
        running.set(false);
        socket.close();
        sender.shutdownNow();
        receiver.shutdownNow();
    }
}
