import type { NetworkConfig } from "./NetworkConfig";
import type { SimulationParameters } from "./SimulationParameters";

export interface SimulationConfig {
  networkConfig: NetworkConfig;
  algorithmId: string;
  defaultParameters: SimulationParameters;
}
