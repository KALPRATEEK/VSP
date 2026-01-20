// views/SimulationImportView.tsx
import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { simulationApi } from "../api/simulationApi";
import type { SimulationConfig } from "../api/dto/SimulationConfig";

/**
 * UC-08: Save/Load Configuration
 *
 * Allows users to load a previously saved simulation configuration
 * from a JSON file.
 *
 * Documentation Reference: § 1.1 SimulationControl.loadConfig()
 */
export function SimulationImportView() {
    const navigate = useNavigate();

    const [selectedFile, setSelectedFile] = useState<File | null>(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const handleFileSelect = (event: React.ChangeEvent<HTMLInputElement>) => {
        const file = event.target.files?.[0];
        if (file) {
            setSelectedFile(file);
            setError(null);
        }
    };

    const handleImport = async () => {
        if (!selectedFile) {
            setError("Please select a file first.");
            return;
        }

        setError(null);
        setLoading(true);

        try {
            // Read file content
            const fileContent = await selectedFile.text();
            const config = JSON.parse(fileContent) as SimulationConfig;

            // Validate basic structure
            if (!config.networkConfig || !config.algorithmId) {
                throw new Error("Invalid configuration format.");
            }

            // Call API: POST /api/simulations/load
            const simulationId = await simulationApi.loadConfig(config);

            // Navigate to dashboard
            navigate(`/simulation/${simulationId}`);

        } catch (e) {
            setError((e as Error).message);
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="card">
            <h2>Import Simulation</h2>
            <p className="card-description">
                Load a previously saved simulation configuration.
            </p>

            <div className="form-group">
                <label htmlFor="config-file">Configuration File (JSON)</label>
                <input
                    id="config-file"
                    type="file"
                    accept="application/json"
                    onChange={handleFileSelect}
                />
                {selectedFile && (
                    <small style={{ color: "#666" }}>
                        Selected: {selectedFile.name}
                    </small>
                )}
            </div>

            <button
                className="secondary"
                onClick={handleImport}
                disabled={loading || !selectedFile}
            >
                {loading ? "Importing…" : "Import Configuration"}
            </button>

            {error && <p className="error">{error}</p>}
        </div>
    );
}


