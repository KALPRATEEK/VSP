import { useEffect, useState } from "react";
import { simulationApi } from "../api/simulationApi";
import type { MetricsSnapshot } from "../api/dto/MetricsSnapshot";

export function MetricsPanel({
                                 simulationId,
                                 active,
                             }: {
    simulationId: string;
    active: boolean;
}) {
    const [metrics, setMetrics] = useState<MetricsSnapshot | null>(null);

    useEffect(() => {
        if (!active) return;

        const interval = setInterval(async () => {
            setMetrics(await simulationApi.getMetrics(simulationId));
        }, 1000);

        return () => clearInterval(interval);
    }, [simulationId, active]);

    if (!metrics) return null;

    return (
        <div className="card">
            <h2>Metrics</h2>
            <ul className="metrics">
                <li>Simulated Time: {metrics.simulatedTime}</li>
                <li>Messages: {metrics.messageCount}</li>
                <li>Rounds: {metrics.rounds}</li>
                <li>Leader: {metrics.leaderId ?? "—"}</li>
            </ul>
        </div>
    );
}
