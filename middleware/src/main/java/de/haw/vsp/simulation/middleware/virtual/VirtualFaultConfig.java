package de.haw.vsp.simulation.middleware.virtual;

import java.util.Objects;
import java.util.Random;

public record VirtualFaultConfig(
        double dropProbability,
        long minDelayMs,
        long maxDelayMs,
        long seed
) {
    public static final VirtualFaultConfig DISABLED = new VirtualFaultConfig(0.0, 0L, 0L, 0L);

    public VirtualFaultConfig {
        if (Double.isNaN(dropProbability) || dropProbability < 0.0 || dropProbability > 1.0)
            throw new IllegalArgumentException("dropProbability must be in [0,1]");
        if (minDelayMs < 0) throw new IllegalArgumentException("minDelayMs must be >= 0");
        if (maxDelayMs < 0) throw new IllegalArgumentException("maxDelayMs must be >= 0");
        if (maxDelayMs > 0 && maxDelayMs < minDelayMs)
            throw new IllegalArgumentException("maxDelayMs must be >= minDelayMs");
    }

    public boolean enabled() {
        return dropProbability > 0.0 || maxDelayMs > 0L;
    }

    public boolean shouldDrop(Random rng) {
        Objects.requireNonNull(rng, "rng");
        return dropProbability > 0.0 && rng.nextDouble() < dropProbability;
    }

    public long sampleDelayMs(Random rng) {
        Objects.requireNonNull(rng, "rng");
        if (maxDelayMs <= 0L) return 0L;
        if (maxDelayMs == minDelayMs) return maxDelayMs;

        long bound = maxDelayMs - minDelayMs + 1L;
        long r = Math.floorMod(rng.nextLong(), bound);
        return minDelayMs + r;
    }
}
