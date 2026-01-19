// views/SimulationImportView.tsx
export function SimulationImportView() {
    return (
        <div className="card">
            <h2>Import Simulation</h2>
            <p className="card-description">
                Load a previously saved simulation configuration.
            </p>

            <input type="file" accept="application/json" />

            <button className="secondary" disabled>
                Import Configuration
            </button>
        </div>
    );
}
