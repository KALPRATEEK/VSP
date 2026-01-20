// views/SaveConfigPanel.tsx
import { useState } from "react";
import { simulationApi } from "../api/simulationApi";
import type { SimulationId } from "../api/dto/SimulationId";

interface SaveConfigPanelProps {
    simulationId: SimulationId;
}

/**
 * UC-08: Save/Load Configuration
 *
 * Allows users to save the current simulation configuration
 * to a JSON file for later use.
 *
 * Documentation Reference: § 1.1 SimulationControl.getCurrentConfig()
 */
export function SaveConfigPanel({ simulationId }: SaveConfigPanelProps) {
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [success, setSuccess] = useState(false);

    const handleSaveConfig = async () => {
        setError(null);
        setSuccess(false);
        setLoading(true);

        try {
            // Get current configuration from API
            const config = await simulationApi.getCurrentConfig(simulationId);

            // Convert to JSON
            const jsonString = JSON.stringify(config, null, 2);
            const blob = new Blob([jsonString], { type: "application/json" });

            // Create download link
            const url = URL.createObjectURL(blob);
            const link = document.createElement("a");
            link.href = url;
            link.download = `simulation-config-${simulationId}.json`;

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
            <h2>Save Configuration</h2>
            <p className="card-description">
                Download the current simulation configuration as JSON.
            </p>

            <button
                className="secondary"
                onClick={handleSaveConfig}
                disabled={loading}
            >
                {loading ? "Saving…" : "Save Configuration"}
            </button>

            {success && (
                <p style={{ color: "green", marginTop: "10px" }}>
                    ✓ Configuration saved successfully!
                </p>
            )}
            {error && <p className="error">{error}</p>}
        </div>
    );
}
