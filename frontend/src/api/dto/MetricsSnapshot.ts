/**
 * Aggregated simulation metrics snapshot.
 *
 * Mirrors de.haw.vsp.simulation.core.MetricsSnapshot
 */
export interface MetricsSnapshot {
    /** Simulation time in logical units */
    simulatedTime: number;

    /** Wall-clock time in milliseconds */
    realTimeMillis: number;

    /** Total number of messages sent */
    messageCount: number;

    /** Number of simulation rounds completed */
    rounds: number;

    /** Whether the algorithm has converged */
    converged: boolean;

    /** ID of the elected leader (null if none yet) */
    leaderId: string | null;
}
