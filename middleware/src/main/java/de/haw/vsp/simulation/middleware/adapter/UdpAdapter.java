package de.haw.vsp.simulation.middleware.adapter;

import de.haw.vsp.simulation.core.NodeId;
import de.haw.vsp.simulation.core.SimulationMessage;
import de.haw.vsp.simulation.middleware.QueueConfig;
import de.haw.vsp.simulation.middleware.TransportAddress;
import de.haw.vsp.simulation.middleware.TransportConfig;
import de.haw.vsp.simulation.middleware.codec.MessageCodecException;
import de.haw.vsp.simulation.middleware.codec.SimulationMessageDeserializer;
import de.haw.vsp.simulation.middleware.codec.SimulationMessageSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.haw.vsp.simulation.middleware.QueueOps;

import java.io.IOException;
import java.net.*;
import java.util.Objects;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * UDP-based {@link TransportAdapter}.
 *
 * Realistic DS behavior:
 * - outbound queue: producer = send(), consumer = sender thread
 * - inbound queue: producer = receiver loop, consumer = delivery thread
 * - bounded queues with explicit overflow policies (no silent drops due to thread pool rejection)
 *
 * Docker behavior:
 * - binds to 0.0.0.0:port (NOT to the configured hostname) so it works in containers
 */
public final class UdpAdapter implements TransportAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(UdpAdapter.class);
    //private static final int MAX_DATAGRAM_BYTES = 64 * 1024;
    private static final int MAX_DATAGRAM_BYTES = 65_507;

    private final NodeId localNode;
    private final TransportConfig config;
    private final SimulationMessageSerializer serializer;
    private final SimulationMessageDeserializer deserializer;

    private final QueueConfig inboundConfig;
    private final QueueConfig outboundConfig;

    private volatile ReceiveCallback callback;

    private final DatagramSocket socket;

    private final LinkedBlockingDeque<SimulationMessage> inboundQueue;
    private final LinkedBlockingDeque<OutboundDatagram> outboundQueue;

    private final AtomicBoolean running = new AtomicBoolean(true);

    private final Thread recvThread;
    private final Thread deliverThread;
    private final Thread sendThread;

    private volatile ErrorCallback errorCallback;

    public UdpAdapter(
            NodeId localNode,
            TransportConfig config,
            SimulationMessageSerializer serializer,
            SimulationMessageDeserializer deserializer
    ) {
        this(localNode, config, serializer, deserializer, QueueConfig.defaultConfig(), QueueConfig.defaultConfig());
    }

    public UdpAdapter(
            NodeId localNode,
            TransportConfig config,
            SimulationMessageSerializer serializer,
            SimulationMessageDeserializer deserializer,
            QueueConfig inboundConfig,
            QueueConfig outboundConfig
    ) {
        this.localNode = Objects.requireNonNull(localNode, "localNode");
        this.config = Objects.requireNonNull(config, "config");
        this.serializer = Objects.requireNonNull(serializer, "serializer");
        this.deserializer = Objects.requireNonNull(deserializer, "deserializer");
        this.inboundConfig = Objects.requireNonNull(inboundConfig, "inboundConfig");
        this.outboundConfig = Objects.requireNonNull(outboundConfig, "outboundConfig");

        this.inboundQueue = new LinkedBlockingDeque<>(this.inboundConfig.capacity());
        this.outboundQueue = new LinkedBlockingDeque<>(this.outboundConfig.capacity());

        TransportAddress localAddr = config.resolve(this.localNode);
        if (localAddr == null) {
            throw new IllegalArgumentException("No transport address for " + this.localNode);
        }

        // Docker correctness: bind to all interfaces on the configured port
        try {
            DatagramSocket s = new DatagramSocket(null);
            s.setReuseAddress(true);
            s.bind(new InetSocketAddress(localAddr.port()));
            this.socket = s;
        } catch (SocketException e) {
            throw new IllegalStateException(
                    "Failed to bind UDP socket for " + this.localNode + " on port " + localAddr.port(), e
            );
        }

        this.recvThread = new Thread(this::receiveLoop, "udp-adapter-recv-" + this.localNode);
        this.recvThread.setDaemon(true);

        this.deliverThread = new Thread(this::deliverLoop, "udp-adapter-deliver-" + this.localNode);
        this.deliverThread.setDaemon(true);

        this.sendThread = new Thread(this::sendLoop, "udp-adapter-send-" + this.localNode);
        this.sendThread.setDaemon(true);

        this.recvThread.start();
        this.deliverThread.start();
        this.sendThread.start();
    }

    @Override
    public boolean send(SimulationMessage message) {
        if (message == null) return false;

        NodeId receiver = message.receiver();
        TransportAddress addr = config.resolve(receiver);
        if (addr == null) {
            reportError(localNode, receiver, "unknown address (dropped)");
            return false;
        }

        final byte[] bytes;
        try {
            bytes = serializer.serialize(message);
        } catch (MessageCodecException e) {
            reportError(localNode, receiver, "serialization failed: " + e.getMessage());
            return false;
        }

        if (bytes.length > MAX_DATAGRAM_BYTES) {
            reportError(localNode, receiver, "oversized datagram " + bytes.length + " bytes (dropped)");
            return false;
        }

        boolean accepted = QueueOps.enqueue(outboundQueue, new OutboundDatagram(receiver, addr, bytes), outboundConfig);
        if (!accepted) {
            reportError(localNode, receiver, "outbound queue full (dropped)");
        }
        return accepted;
    }

    private void sendLoop() {
        while (running.get()) {
            try {
                OutboundDatagram job = outboundQueue.takeFirst();
                doSend(job.receiver, job.addr, job.bytes);
            } catch (InterruptedException ie) {
                if (!running.get()) break;
                Thread.currentThread().interrupt();
            } catch (RuntimeException e) {
                LOG.debug("UDP send loop error: {}", e.getMessage());
                // receiver unknown here
                reportError(localNode, null, "udp send loop error: " + e.getMessage());
            }
        }
    }

    private void doSend(NodeId receiver, TransportAddress addr, byte[] bytes) {
        try {
            DatagramPacket packet = new DatagramPacket(
                    bytes, bytes.length,
                    InetAddress.getByName(addr.host()), addr.port()
            );
            socket.send(packet);
        } catch (IOException e) {
            reportError(localNode, receiver, "udp send failed: " + e.getMessage());
        }
    }

    private void receiveLoop() {
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
                    reportError(localNode, null, "decode error from " + packet.getSocketAddress() + ": " + e.getMessage());
                    continue;
                }

                boolean accepted = QueueOps.enqueue(inboundQueue, msg, inboundConfig);
                if (!accepted) {
                    reportError(localNode, msg.sender(), "inbound queue full (dropped)");
                }

            } catch (SocketException se) {
                // expected on close()
                if (running.get()) {
                    reportError(localNode, null, "udp socket error: " + se.getMessage());
                }
                break;
            } catch (IOException e) {
                if (running.get()) {
                    reportError(localNode, null, "udp receive error: " + e.getMessage());
                }
            } finally {
                packet.setLength(buf.length);
            }
        }
    }

    private void deliverLoop() {
        while (running.get()) {
            try {
                SimulationMessage msg = inboundQueue.takeFirst();
                ReceiveCallback cb = this.callback;
                if (cb != null) {
                    cb.onMessage(msg);
                } else {
                    // No handler wired yet -> transient drop
                    reportError(localNode, msg.sender(), "no receive callback yet (dropped)");
                }
            } catch (InterruptedException ie) {
                if (!running.get()) break;
                Thread.currentThread().interrupt();
            } catch (RuntimeException e) {
                LOG.debug("UDP deliver loop error: {}", e.getMessage());
                reportError(localNode, null, "udp deliver loop error: " + e.getMessage());
            }
        }
    }

    @Override
    public void onReceive(ReceiveCallback callback) {
        this.callback = callback;
    }

    @Override
    public void onError(ErrorCallback cb) {
        this.errorCallback = cb;
    }

    private void reportError(NodeId node, NodeId peer, String msg) {
        ErrorCallback cb = this.errorCallback;
        if (cb != null) cb.onError(node, peer, msg);
    }

    @Override
    public NodeId localNode() {
        return localNode;
    }

    @Override
    public void close() {
        running.set(false);

        // Unblock receiver loop
        try {
            socket.close();
        } catch (Exception ignored) {}

        // Unblock loops
        recvThread.interrupt();
        deliverThread.interrupt();
        sendThread.interrupt();

        inboundQueue.clear();
        outboundQueue.clear();
    }

    private record OutboundDatagram(NodeId receiver, TransportAddress addr, byte[] bytes) {}
}
