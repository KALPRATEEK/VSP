/**
 * Simulation runtime parameters.
 *
 * Mirrors de.haw.vsp.simulation.core.SimulationParameters
 */
export interface SimulationParameters {
  /** Seed for deterministic randomness */
  randomSeed: number;

  /** Maximum number of simulation steps (must be > 0) */
  maxSteps: number;

  /** Artificial message delay in milliseconds (must be >= 0) */
  messageDelayMillis: number;
}
