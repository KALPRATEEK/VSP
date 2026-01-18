package de.haw.vsp.simulation.middleware;

import de.haw.vsp.simulation.core.NodeId;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Middleware helpers for building {@link TransportConfig} from environment variables.
 *
 * Supported modes:
 *
 * 1) Explicit peers list:
 *    PEERS="node-0:node-0:9000,node-1:node-1:9000,..."
 *    Format per entry: "<id>:<host>:<port>"
 *
 * 2) Pattern mode (recommended for Docker):
 *    - HOST_TEMPLATE (optional, default "{ID}")
 *    - UDP_PORT (optional, falls back to PORT, default 9000)
 *    - NODE_COUNT (optional)
 *    - MIN_ID (optional, default 0)
 *
 * If NODE_COUNT is provided, a {@link BoundedTransportConfig} is returned (only ids in range allowed).
 * Otherwise, returns an unbounded {@link PatternTransportConfig}.
 */
public final class EnvTransportConfigs {

    private EnvTransportConfigs() {}

    // ---------- Mode 1: explicit peers list ----------

    public static TransportConfig fromPeersString(String peers) {
        return new MapTransportConfig(parsePeers(peers));
    }

    public static Map<NodeId, TransportAddress> parsePeers(String peers) {
        if (peers == null || peers.isBlank()) {
            throw new IllegalArgumentException("PEERS must not be null/blank");
        }

        Map<NodeId, TransportAddress> map = new HashMap<>();

        String[] entries = peers.split(",");
        for (String rawEntry : entries) {
            String entry = rawEntry.trim();
            if (entry.isEmpty()) continue;

            // id:host:port
            String[] parts = entry.split(":", 3);
            if (parts.length != 3) {
                throw new IllegalArgumentException("Invalid peer entry '" + entry + "'. Expected <id>:<host>:<port>");
            }

            NodeId id = new NodeId(parts[0].trim());
            String host = parts[1].trim();
            int port;
            try {
                port = Integer.parseInt(parts[2].trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid port in peer entry '" + entry + "'");
            }

            map.put(id, new TransportAddress(host, port));
        }

        if (map.isEmpty()) {
            throw new IllegalArgumentException("No peers parsed from PEERS: " + peers);
        }
        return map;
    }

    // ---------- Mode 2: pattern (+ optional bounded range) ----------

    /**
     * Pattern + bounded range (useful for programmatic setup).
     *
     * @param hostPatternOrTemplate either a prefix (e.g. "node") or a template containing "{ID}" (e.g. "{ID}")
     */
    public static TransportConfig patternBounded(String hostPatternOrTemplate, int port, int minId, int nodeCount) {
        Objects.requireNonNull(hostPatternOrTemplate, "hostPatternOrTemplate");
        PatternTransportConfig pattern = buildPatternConfig(hostPatternOrTemplate, port);
        NodeRange range = NodeRange.fromCount(minId, nodeCount);
        return new BoundedTransportConfig(pattern, range);
    }

    /**
     * Build config from env vars:
     *
     * Precedence:
     * - If PEERS is set -> MapTransportConfig
     * - Else -> PatternTransportConfig using HOST_TEMPLATE / HOST_PREFIX and UDP_PORT/PORT
     *
     * Env keys:
     * - PEERS (optional)
     * - HOST_TEMPLATE (optional, default "{ID}")
     * - HOST_PREFIX (optional legacy; used only if HOST_TEMPLATE not set)
     * - UDP_PORT (optional; falls back to PORT; default 9000)
     * - NODE_COUNT (optional; if set returns bounded config)
     * - MIN_ID (optional; default 0)
     */
    public static TransportConfig fromEnvironment(Map<String, String> env) {
        Objects.requireNonNull(env, "env");

        String peers = trimToNull(env.get("PEERS"));
        if (peers != null) {
            return fromPeersString(peers);
        }

        // Prefer HOST_TEMPLATE; fallback to legacy HOST_PREFIX; default is Docker-friendly "{ID}"
        String hostTemplate = trimToNull(env.get("HOST_TEMPLATE"));
        String hostPrefix = trimToNull(env.get("HOST_PREFIX"));

        String hostPatternOrTemplate;
        if (hostTemplate != null) {
            hostPatternOrTemplate = hostTemplate;
        } else if (hostPrefix != null) {
            hostPatternOrTemplate = hostPrefix; // legacy prefix behavior
        } else {
            hostPatternOrTemplate = PatternTransportConfig.PLACEHOLDER; // "{ID}"
        }

        // Prefer UDP_PORT; fallback to PORT; default 9000
        int port = firstInt(env, 9000, "UDP_PORT", "PORT");

        // Optional bounded range
        String countRaw = trimToNull(env.get("NODE_COUNT"));
        if (countRaw == null) {
            return buildPatternConfig(hostPatternOrTemplate, port);
        }

        int nodeCount = parseInt(countRaw, -1, "NODE_COUNT");
        if (nodeCount <= 0) {
            throw new IllegalArgumentException("NODE_COUNT must be > 0");
        }

        int minId = parseInt(env.get("MIN_ID"), 0, "MIN_ID");
        return patternBounded(hostPatternOrTemplate, port, minId, nodeCount);
    }

    private static PatternTransportConfig buildPatternConfig(String hostPatternOrTemplate, int port) {
        String s = Objects.requireNonNull(hostPatternOrTemplate, "hostPatternOrTemplate").trim();
        if (s.isEmpty()) {
            // empty prefix => "{ID}"
            return new PatternTransportConfig("", port);
        }
        if (s.contains(PatternTransportConfig.PLACEHOLDER)) {
            return new PatternTransportConfig(s, port, true);
        }
        // legacy prefix mode: "node" => "node{ID}"
        return new PatternTransportConfig(s, port);
    }

    // ---------- helpers ----------

    private static int firstInt(Map<String, String> env, int def, String... keys) {
        for (String key : keys) {
            String raw = env.get(key);
            String v = trimToNull(raw);
            if (v == null) continue;
            return parseInt(v, def, key);
        }
        return def;
    }

    private static int parseInt(String raw, int def, String key) {
        String v = trimToNull(raw);
        if (v == null) return def;
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid int for " + key + ": " + v);
        }
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
