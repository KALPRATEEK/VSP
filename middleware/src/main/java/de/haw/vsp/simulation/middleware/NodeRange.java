package de.haw.vsp.simulation.middleware;

import de.haw.vsp.simulation.core.NodeId;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Defines a valid universe of node indices (inclusive).
 *
 * Supports NodeId formats:
 * - "node-<n>"  (preferred / contract)
 * - "<n>"       (legacy)
 *
 * Any NodeId that cannot be mapped to a numeric index is considered NOT contained.
 */
public record NodeRange(int minInclusive, int maxInclusive) {

    private static final Pattern NODE_INDEX_PATTERN = Pattern.compile("^node-(\\d+)$");
    private static final Pattern NUMERIC_PATTERN = Pattern.compile("^\\d+$");

    public NodeRange {
        if (minInclusive < 0) throw new IllegalArgumentException("minInclusive must be >= 0");
        if (maxInclusive < minInclusive) throw new IllegalArgumentException("maxInclusive must be >= minInclusive");
    }

    public boolean contains(NodeId id) {
        if (id == null) return false;
        Integer idx = tryParseIndex(id.value());
        if (idx == null) return false;
        return idx >= minInclusive && idx <= maxInclusive;
    }

    public static NodeRange fromCount(int minInclusive, int count) {
        if (count <= 0) throw new IllegalArgumentException("count must be > 0");
        return new NodeRange(minInclusive, minInclusive + count - 1);
    }

    /** Parses "node-<n>" or "<n>" into an int index, otherwise returns null. */
    public static Integer tryParseIndex(String raw) {
        if (raw == null) return null;
        String id = raw.trim();
        if (id.isEmpty()) return null;

        Matcher m = NODE_INDEX_PATTERN.matcher(id);
        if (m.matches()) return safeParseInt(m.group(1));

        if (NUMERIC_PATTERN.matcher(id).matches()) return safeParseInt(id);

        return null;
    }

    private static Integer safeParseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
