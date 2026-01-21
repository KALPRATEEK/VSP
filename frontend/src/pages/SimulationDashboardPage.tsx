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
    const [maxSteps, setMaxSteps] = useState(500);
    const [messageDelay, setMessageDelay] = useState(50);

    if (!id) return null;

    const startSimulation = async () => {
        await simulationApi.startSimulation(id, {
            randomSeed: Date.now(),
            maxSteps: maxSteps,
            messageDelayMillis: messageDelay,
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
                    <div style={{ marginBottom: "20px", padding: "20px", background: "#f5f5f5", borderRadius: "8px" }}>
                        <h3>Simulation Parameters</h3>
                        <div style={{ marginBottom: "15px" }}>
                            <label style={{ display: "block", marginBottom: "5px" }}>
                                Max Steps: {maxSteps}
                            </label>
                            <input
                                type="range"
                                min="50"
                                max="2000"
                                value={maxSteps}
                                onChange={(e) => setMaxSteps(Number(e.target.value))}
                                style={{ width: "100%" }}
                            />
                        </div>
                        <div style={{ marginBottom: "15px" }}>
                            <label style={{ display: "block", marginBottom: "5px" }}>
                                Message Delay: {messageDelay}ms
                            </label>
                            <input
                                type="range"
                                min="0"
                                max="500"
                                value={messageDelay}
                                onChange={(e) => setMessageDelay(Number(e.target.value))}
                                style={{ width: "100%" }}
                            />
                            <small style={{ color: "#666" }}>
                                0ms = Fast, 50-100ms = Visible, 200+ms = Slow
                            </small>
                        </div>
                    </div>
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

