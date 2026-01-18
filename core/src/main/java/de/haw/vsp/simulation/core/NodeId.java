package de.haw.vsp.simulation.core;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Immutable identifier for a simulation node.
 *
 * NodeId uniquely identifies a node within a simulation.
 * It is used for routing messages, identifying neighbors, and determining leader election outcomes.
 *
 * Ordering note:
 * - If both ids match "node-(\\d+)" they are compared by the numeric suffix (e.g. node-2 < node-10).
 * - Else, if both ids are purely numeric, they are compared numerically.
 * - Otherwise, falls back to lexicographic ordering.
 *
 * This record is immutable and fully JSON-serializable.
 */
public record NodeId(@JsonValue String value) implements Comparable<NodeId> {

    // Contract convention: "node-<number>"
    private static final Pattern NODE_INDEX_PATTERN = Pattern.compile("^node-(\\d+)$");
    private static final Pattern NUMERIC_PATTERN = Pattern.compile("^\\d+$");

    /**
     * Canonical constructor with validation.
     *
     * @param value the string representation of the node ID (must not be null or blank)
     * @throws IllegalArgumentException if validation fails
     */
    @JsonCreator
    public NodeId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "NodeId value must not be null or blank, but was: " +
                            (value == null ? "null" : "'" + value + "'")
            );
        }
    }

    @Override
    public int compareTo(NodeId other) {
        if (other == null) throw new NullPointerException("other");

        Long a = tryParseNumericKey(this.value);
        Long b = tryParseNumericKey(other.value);

        if (a != null && b != null) {
            return Long.compare(a, b);
        }
        return this.value.compareTo(other.value);
    }


    /**
     * Returns a numeric key if the id is of the form "node-<n>" or "<n>", otherwise null.
     */
    private static Long tryParseNumericKey(String id) {
        Matcher m = NODE_INDEX_PATTERN.matcher(id);
        if (m.matches()) {
            return safeParseLong(m.group(1));
        }
        if (NUMERIC_PATTERN.matcher(id).matches()) {
            return safeParseLong(id);
        }
        return null;
    }

    private static Long safeParseLong(String s) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException ex) {
            // If it overflows or is otherwise not parseable, fall back to lexicographic ordering.
            return null;
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
