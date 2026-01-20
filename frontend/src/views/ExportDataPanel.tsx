// views/ExportDataPanel.tsx
import { useState } from "react";
import { simulationApi } from "../api/simulationApi";
import type { SimulationId } from "../api/dto/SimulationId";

interface ExportDataPanelProps {
    simulationId: SimulationId;
}

/**
 * UC-09: Export Run Data
 *
 * Allows users to export simulation events and metrics
 * in JSON or CSV format.
 *
 * Documentation Reference: § 1.1 SimulationControl.exportRunData()
 */
export function ExportDataPanel({ simulationId }: ExportDataPanelProps) {
    const [format, setFormat] = useState<"JSON" | "CSV">("JSON");
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [success, setSuccess] = useState(false);

    const handleExport = async () => {
        setError(null);
        setSuccess(false);
        setLoading(true);

        try {
            // Call API: GET /api/simulations/:id/export?format=JSON|CSV
            const blob = await simulationApi.exportRunData(simulationId, format);

            // Create download link
            const url = URL.createObjectURL(blob);
            const link = document.createElement("a");
            link.href = url;

            const fileExtension = format === "JSON" ? "json" : "csv";
            link.download = `simulation-export-${simulationId}.${fileExtension}`;

            // Trigger download
            document.body.appendChild(link);
            link.click();
            document.body.removeChild(link);

            // Cleanup
            URL.revokeObjectURL(url);

            setSuccess(true);
            setTimeout(() => setSuccess(false), 3000);

        } catch (e) {
            setError((e as Error).message);
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="card">
            <h2>Export Run Data</h2>
            <p className="card-description">
                Export simulation events and metrics for analysis.
            </p>

            <div className="form-group">
                <label htmlFor="export-format">Export Format</label>
                <select
                    id="export-format"
                    value={format}
                    onChange={(e) => setFormat(e.target.value as "JSON" | "CSV")}
                >
                    <option value="JSON">JSON</option>
                    <option value="CSV">CSV</option>
                </select>
                <small style={{ color: "#666", display: "block", marginTop: "5px" }}>
                    JSON: Full event data | CSV: Tabular metrics
                </small>
            </div>

            <button
                className="secondary"
                onClick={handleExport}
                disabled={loading}
            >
                {loading ? "Exporting…" : "Export Data"}
            </button>

            {success && (
                <p style={{ color: "green", marginTop: "10px" }}>
                    ✓ Data exported successfully!
                </p>
            )}
            {error && <p className="error">{error}</p>}
        </div>
    );
}
