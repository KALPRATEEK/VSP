package de.haw.vsp.simulation.middleware.udp;

import de.haw.vsp.simulation.middleware.MessageHandler;
import de.haw.vsp.simulation.middleware.MessagingPort;
import de.haw.vsp.simulation.middleware.NodeId;
import de.haw.vsp.simulation.middleware.SimulationMessage;
import de.haw.vsp.simulation.middleware.TransportAddress;
import de.haw.vsp.simulation.middleware.TransportConfig;
import de.haw.vsp.simulation.middleware.codec.MessageCodecException;
import de.haw.vsp.simulation.middleware.codec.SimulationMessageDeserializer;
import de.haw.vsp.simulation.middleware.codec.SimulationMessageSerializer;

import java.io.Closeable;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * UDP-based implementation of {@link MessagingPort}.
 *
 * <p>One instance is intended to represent the local node endpoint:
 * it binds to the local node's configured port and dispatches inbound messages
 * to the handler registered for that local node.</p>
 *
 * <p>Semantics are best-effort (like UDP): messages can be dropped, duplicated, or arrive out of order.</p>
 */
public final class UdpMessagingPort implements MessagingPort, Closeable {

    private static final int DEFAULT_MAX_DATAGRAM_BYTES = 64 * 1024; // UDP theoretical max payload ~65k

    private final NodeId localNodeId;
    private final TransportConfig transportConfig;
    private final SimulationMessageSerializer serializer;
    private final SimulationMessageDeserializer deserializer;

    private final DatagramSocket socket;
    private final int maxDatagramBytes;

    private volatile MessageHandler handler; // for the local node
    private final ExecutorService receiverExecutor;
    private final AtomicBoolean running = new AtomicBoolean(true);

    /**
     * Create and bind a UDP endpoint for the given local node.
     *
     * @param localNodeId      local node identity
     * @param transportConfig  resolves node ids to (host,port)
     * @param serializer       message -> bytes (e.g., JSON)
     * @param deserializer     bytes -> message
     */
    public UdpMessagingPort(
            NodeId localNodeId,
            TransportConfig transportConfig,
            SimulationMessageSerializer serializer,
            SimulationMessageDeserializer deserializer
    ) {
        this(localNodeId, transportConfig, serializer, deserializer, DEFAULT_MAX_DATAGRAM_BYTES);
    }

    public UdpMessagingPort(
            NodeId localNodeId,
            TransportConfig transportConfig,
            SimulationMessageSerializer serializer,
            SimulationMessageDeserializer deserializer,
            int maxDatagramBytes
    ) {
        this.localNodeId = Objects.requireNonNull(localNodeId, "localNodeId");
        this.transportConfig = Objects.requireNonNull(transportConfig, "transportConfig");
        this.serializer = Objects.requireNonNull(serializer, "serializer");
        this.deserializer = Objects.requireNonNull(deserializer, "deserializer");

        if (maxDatagramBytes <= 0) {
            throw new IllegalArgumentException("maxDatagramBytes must be > 0");
        }
        this.maxDatagramBytes = maxDatagramBytes;

        TransportAddress localAddr = transportConfig.resolve(localNodeId);
        if (localAddr == null) {
            throw new IllegalArgumentException("TransportConfig returned null for local node: " + localNodeId);
        }

        try {
            // Bind explicitly to the configured port. Host is not used for binding here (bind all interfaces).
            this.socket = new DatagramSocket(new InetSocketAddress(localAddr.port()));
        } catch (SocketException e) {
            throw new IllegalStateException("Failed to bind UDP socket for " + localNodeId
                    + " on port " + localAddr.port(), e);
        }

        this.receiverExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "udp-messaging-receiver-" + localNodeId);
            t.setDaemon(true);
            return t;
        });

        startReceiverLoop();
    }

    @Override
    public void send(NodeId receiver, SimulationMessage message) {
        Objects.requireNonNull(receiver, "receiver");
        Objects.requireNonNull(message, "message");

        TransportAddress addr = transportConfig.resolve(receiver);
        if (addr == null) {
            // Best-effort: drop if cannot resolve
            return;
        }

        byte[] payload;
        try {
            payload = serializer.serialize(message);
        } catch (MessageCodecException e) {
            // Best-effort: drop if cannot encode
            return;
        }

        // UDP datagrams have size limits; ensure we don't exceed.
        if (payload.length > maxDatagramBytes) {
            // Best-effort: drop oversize datagram
            return;
        }

        try {
            InetAddress targetHost = InetAddress.getByName(addr.host());
            DatagramPacket packet = new DatagramPacket(payload, payload.length, targetHost, addr.port());
            socket.send(packet);
        } catch (IOException e) {
            // Best-effort: drop on send error
        }
    }

    @Override
    public void broadcast(Set<NodeId> receivers, SimulationMessage message) {
        Objects.requireNonNull(receivers, "receivers");
        Objects.requireNonNull(message, "message");

        for (NodeId r : receivers) {
            if (r == null) continue;
            send(r, message);
        }
    }

    @Override
    public void registerHandler(NodeId nodeId, MessageHandler handler) {
        Objects.requireNonNull(nodeId, "nodeId");
        Objects.requireNonNull(handler, "handler");

        // In UDP mode, one endpoint instance represents one local node.
        if (!nodeId.equals(localNodeId)) {
            throw new IllegalArgumentException(
                    "UdpMessagingPort is bound to local node " + localNodeId + " but tried to register handler for " + nodeId
            );
        }
        this.handler = handler;
    }

    @Override
    public void unregisterHandler(NodeId nodeId) {
        Objects.requireNonNull(nodeId, "nodeId");
        if (!nodeId.equals(localNodeId)) {
            return;
        }
        this.handler = null;
    }

    private void startReceiverLoop() {
        receiverExecutor.submit(() -> {
            byte[] buffer = new byte[maxDatagramBytes];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

            while (running.get()) {
                try {
                    socket.receive(packet);

                    // Copy exactly the received bytes
                    byte[] received = new byte[packet.getLength()];
                    System.arraycopy(packet.getData(), packet.getOffset(), received, 0, packet.getLength());

                    SimulationMessage msg;
                    try {
                        msg = deserializer.deserialize(received);
                    } catch (MessageCodecException decodeError) {
                        // Ignore invalid datagrams
                        continue;
                    }

                    MessageHandler current = this.handler;
                    if (current != null) {
                        current.onMessage(msg);
                    }
                    // else: drop if no handler registered

                } catch (SocketException e) {
                    // socket.close() will interrupt receive() with a SocketException
                    if (running.get()) {
                        // unexpected socket error while still running -> continue loop
                        continue;
                    }
                    break;
                } catch (IOException e) {
                    // best-effort: ignore and continue
                } finally {
                    // Reset packet length in case it was shortened by receive()
                    packet.setLength(buffer.length);
                }
            }
        });
    }

    /**
     * @return the UDP port this endpoint is bound to.
     */
    public int localPort() {
        return socket.getLocalPort();
    }

    /**
     * @return whether the receiver loop is still running.
     */
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public void close() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        // Closing socket interrupts receive()
        socket.close();
        receiverExecutor.shutdownNow();
    }

    @Override
    public String toString() {
        TransportAddress local = transportConfig.resolve(localNodeId);
        String bound = (local == null)
                ? ("port=" + socket.getLocalPort())
                : (local.host() + ":" + local.port());
        return "UdpMessagingPort{localNodeId=" + localNodeId + ", bound=" + bound + "}";
    }
}
S