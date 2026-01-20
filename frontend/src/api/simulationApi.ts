import { httpRequest } from "./httpClient";

import type { NetworkConfig } from "./dto/NetworkConfig";
import type { SimulationParameters } from "./dto/SimulationParameters";
import type { SimulationConfig } from "./dto/SimulationConfig";
import type { SimulationId } from "./dto/SimulationId";
import type { MetricsSnapshot } from "./dto/MetricsSnapshot";

export const simulationApi = {
    /* =========================================================
       Simulation lifecycle
       ========================================================= */

    async initializeNetwork(
        config: NetworkConfig
    ): Promise<SimulationId> {
        return httpRequest<SimulationId>(() =>
            fetch("/api/simulations", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(config),
            })
        );
    },

    async selectAlgorithm(
        simulationId: SimulationId,
        algorithmId: string
    ): Promise<void> {
        return httpRequest<void>(() =>
            fetch(
                `/api/simulations/${simulationId}/algorithm?algorithmId=${encodeURIComponent(
                    algorithmId
                )}`,
                { method: "POST" }
            )
        );
    },

    async startSimulation(
        simulationId: SimulationId,
        parameters: SimulationParameters
    ): Promise<void> {
        return httpRequest<void>(() =>
            fetch(`/api/simulations/${simulationId}/start`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(parameters),
            })
        );
    },

    async pauseSimulation(simulationId: SimulationId): Promise<void> {
        return httpRequest<void>(() =>
            fetch(`/api/simulations/${simulationId}/pause`, {
                method: "POST",
            })
        );
    },

    async resumeSimulation(simulationId: SimulationId): Promise<void> {
        return httpRequest<void>(() =>
            fetch(`/api/simulations/${simulationId}/resume`, {
                method: "POST",
            })
        );
    },

    async stopSimulation(simulationId: SimulationId): Promise<void> {
        return httpRequest<void>(() =>
            fetch(`/api/simulations/${simulationId}/stop`, {
                method: "POST",
            })
        );
    },

    /* =========================================================
       Queries (non-visual)
       ========================================================= */

    async getMetrics(
        simulationId: SimulationId
    ): Promise<MetricsSnapshot> {
        return httpRequest<MetricsSnapshot>(() =>
            fetch(`/api/simulations/${simulationId}/metrics`)
        );
    },

    async getCurrentConfig(
        simulationId: SimulationId
    ): Promise<SimulationConfig> {
        return httpRequest<SimulationConfig>(() =>
            fetch(`/api/simulations/${simulationId}/config`)
        );
    },

    async getLogs(
        simulationId: SimulationId,
        filter?: string
    ): Promise<string[]> {
        const url =
            filter && filter.trim().length > 0
                ? `/api/simulations/${simulationId}/logs?filter=${encodeURIComponent(
                    filter
                )}`
                : `/api/simulations/${simulationId}/logs`;

        return httpRequest<string[]>(() => fetch(url));
    },

    /* =========================================================
       Save/Load/Export (UC-08, UC-09)
       ========================================================= */

    async loadConfig(config: SimulationConfig): Promise<SimulationId> {
        return httpRequest<SimulationId>(() =>
            fetch("/api/simulations/load", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(config),
            })
        );
    },

    async exportRunData(
        simulationId: SimulationId,
        format: "JSON" | "CSV"
    ): Promise<Blob> {
        const response = await fetch(
            `/api/simulations/${simulationId}/export?format=${format}`
        );

        if (!response.ok) {
            const text = await response.text();
            throw new Error(`Export failed: ${text}`);
        }

        return response.blob();
    },
};
