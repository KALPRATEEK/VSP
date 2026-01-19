import type { NetworkConfig } from "./NetworkConfig";
import type { SimulationParameters } from "./SimulationParameters";

/**
 * Bundled simulation configuration.
 *
 * Mirrors de.haw.vsp.simulation.core.SimulationConfig
 */
export interface SimulationConfig {
    /** Network topology configuration */
    networkConfig: NetworkConfig;

    /** Algorithm identifier */
    algorithmId: string;

    /** Default runtime parameters */
    defaultParameters: SimulationParameters;
}
