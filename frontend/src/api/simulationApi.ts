import { httpRequest } from "./httpClient";
import type { NetworkConfig } from "./dto/NetworkConfig";
import type { SimulationId } from "./dto/SimulationId";

export const simulationApi = {
  async initializeNetwork(
    config: NetworkConfig
  ): Promise<SimulationId> {
    return httpRequest<SimulationId>(() =>
      fetch("/api/simulation", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(config),
      })
    );
  },
};
