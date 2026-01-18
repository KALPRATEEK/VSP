package de.haw.vsp.simulation.middleware;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class EnvQueueConfigs {

    private EnvQueueConfigs() {}

    public static final String KEY_OUT_CAPACITY = "QUEUE_OUT_CAPACITY";
    public static final String KEY_IN_CAPACITY = "QUEUE_IN_CAPACITY";
    public static final String KEY_OVERFLOW_POLICY = "QUEUE_OVERFLOW_POLICY";
    public static final String KEY_BLOCK_TIMEOUT_MS = "QUEUE_BLOCK_TIMEOUT_MS";

    public record QueuePair(QueueConfig outbound, QueueConfig inbound) {
        public QueuePair {
            Objects.requireNonNull(outbound, "outbound");
            Objects.requireNonNull(inbound, "inbound");
        }
    }

    public static QueuePair fromSystemEnvironment() {
        return fromEnvironment(System.getenv());
    }

    public static QueuePair fromEnvironment(Map<String, String> env) {
        Objects.requireNonNull(env, "env");

        int outCap = parsePositiveInt(env.get(KEY_OUT_CAPACITY), 1024, KEY_OUT_CAPACITY);
        int inCap  = parsePositiveInt(env.get(KEY_IN_CAPACITY), 1024, KEY_IN_CAPACITY);

        QueueOverflowPolicy policy = parsePolicy(env.get(KEY_OVERFLOW_POLICY), QueueOverflowPolicy.DROP_NEWEST);

        long blockTimeoutMs = 0L;
        if (policy == QueueOverflowPolicy.BLOCK) {
            blockTimeoutMs = parseNonNegativeLong(env.get(KEY_BLOCK_TIMEOUT_MS), 1000L, KEY_BLOCK_TIMEOUT_MS);
        }

        return new QueuePair(
                new QueueConfig(outCap, policy, blockTimeoutMs),
                new QueueConfig(inCap,  policy, blockTimeoutMs)
        );
    }

    private static QueueOverflowPolicy parsePolicy(String raw, QueueOverflowPolicy def) {
        String v = trimToNull(raw);
        if (v == null) return def;

        String norm = v.trim().replace('-', '_').replace(' ', '_').toUpperCase(Locale.ROOT);
        try {
            return QueueOverflowPolicy.valueOf(norm);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Invalid " + KEY_OVERFLOW_POLICY + ": '" + v +
                            "'. Expected one of: BLOCK, DROP_NEWEST, DROP_OLDEST"
            );
        }
    }

    private static int parsePositiveInt(String raw, int def, String key) {
        String v = trimToNull(raw);
        if (v == null) return def;
        try {
            int n = Integer.parseInt(v);
            if (n <= 0) throw new NumberFormatException("must be > 0");
            return n;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid " + key + ": '" + v + "' (must be > 0)");
        }
    }

    private static long parseNonNegativeLong(String raw, long def, String key) {
        String v = trimToNull(raw);
        if (v == null) return def;
        try {
            long n = Long.parseLong(v);
            if (n < 0) throw new NumberFormatException("must be >= 0");
            return n;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid " + key + ": '" + v + "' (must be >= 0)");
        }
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
