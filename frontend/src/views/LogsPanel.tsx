// views/LogsPanel.tsx
import { useEffect, useState } from "react";
import { simulationApi } from "../api/simulationApi";
import type { SimulationId } from "../api/dto/SimulationId";

interface LogsPanelProps {
    simulationId: SimulationId;
    active: boolean;
}

/**
 * UC-10: View Errors/Logs
 *
 * Displays runtime and error logs for diagnostics.
 * Logs are fetched from the backend and can be filtered
 * by severity level.
 *
 * Documentation Reference: § 1.1 SimulationControl.getLogs()
 */
export function LogsPanel({ simulationId, active }: LogsPanelProps) {
    const [logs, setLogs] = useState<string[]>([]);
    const [filter, setFilter] = useState<string>("");
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const fetchLogs = async () => {
        setLoading(true);
        setError(null);

        try {
            const fetchedLogs = await simulationApi.getLogs(
                simulationId,
                filter || undefined
            );
            setLogs(fetchedLogs);
        } catch (e) {
            setError((e as Error).message);
        } finally {
            setLoading(false);
        }
    };

    // Auto-fetch on mount and when filter changes
    useEffect(() => {
        if (active) {
            fetchLogs();
        }
    }, [simulationId, filter, active]);

    // Auto-refresh every 2 seconds if active
    useEffect(() => {
        if (!active) return;

        const interval = setInterval(() => {
            fetchLogs();
        }, 2000);

        return () => clearInterval(interval);
    }, [simulationId, filter, active]);

    if (!active) return null;

    return (
        <div className="card">
            <h2>Logs</h2>

            <div className="form-group">
                <label htmlFor="log-filter">Filter</label>
                <select
                    id="log-filter"
                    value={filter}
                    onChange={(e) => setFilter(e.target.value)}
                >
                    <option value="">All Logs</option>
                    <option value="ERROR">Errors Only</option>
                    <option value="MESSAGE_SENT">Messages Sent</option>
                    <option value="MESSAGE_RECEIVED">Messages Received</option>
                    <option value="STATE_CHANGED">State Changes</option>
                    <option value="LEADER_ELECTED">Leader Elected</option>
                </select>
            </div>

            {loading && <p style={{ color: "#666" }}>Loading logs…</p>}
            {error && <p className="error">{error}</p>}

            <div
                className="logs-container"
                style={{
                    maxHeight: "400px",
                    overflowY: "auto",
                    background: "#f5f5f5",
                    padding: "10px",
                    borderRadius: "4px",
                    fontFamily: "monospace",
                    fontSize: "12px",
                }}
            >
                {logs.length === 0 && !loading && (
                    <p style={{ color: "#999" }}>No logs available.</p>
                )}
                {logs.map((log, index) => (
                    <div
                        key={index}
                        style={{
                            marginBottom: "5px",
                            padding: "5px",
                            background: log.includes("ERROR") ? "#ffebee" : "white",
                            borderLeft: log.includes("ERROR")
                                ? "3px solid red"
                                : "3px solid #ddd",
                            borderRadius: "2px",
                        }}
                    >
                        {log}
                    </div>
                ))}
            </div>
        </div>
    );
}
