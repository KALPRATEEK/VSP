import { useParams, useNavigate } from "react-router-dom";
import { useState } from "react";
import { simulationApi } from "../api/simulationApi";
import { SimulationVisualizationView } from "../views/SimulationVisualizationView";
import { SimulationControlPanel } from "../views/SimulationControlPanel";
import { MetricsPanel } from "../views/MetricsPanel";

export function SimulationDashboardPage() {
    const { id } = useParams();
    const navigate = useNavigate();

    const [state, setState] = useState<"INITIALIZED" | "RUNNING" | "PAUSED">(
        "INITIALIZED"
    );

    if (!id) return null;

    const startSimulation = async () => {
        await simulationApi.startSimulation(id, {
            randomSeed: 1,
            maxSteps: 100,
            messageDelayMillis: 0,
        });
        setState("RUNNING");
    };

    return (
        <main className="dashboard">
            <header className="dashboard-header">
                <h1>Simulation Dashboard</h1>
                <span className="simulation-id">ID: {id}</span>
            </header>

            {state === "INITIALIZED" && (
                <section className="dashboard-start">
                    <button className="primary" onClick={startSimulation}>
                        Start Simulation
                    </button>
                </section>
            )}

            {state !== "INITIALIZED" && (
                <section className="dashboard-main">
                    <SimulationVisualizationView
                        simulationId={id}
                        running={state === "RUNNING"}
                    />
                    <aside className="dashboard-side">
                        <SimulationControlPanel
                            simulationId={id}
                            state={state}
                            onStateChange={setState}
                            onStop={() => navigate("/")}
                        />
                        <MetricsPanel
                            simulationId={id}
                            active
                        />
                    </aside>
                </section>
            )}
        </main>
    );
}

