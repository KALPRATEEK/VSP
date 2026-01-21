import { simulationApi } from "../api/simulationApi";

type SimulationState = "INITIALIZED" | "RUNNING" | "PAUSED";

interface SimulationControlPanelProps {
    simulationId: string;
    state: SimulationState;
    onStateChange: (state: SimulationState) => void;
    onStop: () => void;
}

export function SimulationControlPanel({
                                           simulationId,
                                           state,
                                           onStateChange,
                                           onStop,
                                       }: SimulationControlPanelProps) {

    const pause = async () => {
        await simulationApi.pauseSimulation(simulationId);
        onStateChange("PAUSED");
    };

    const resume = async () => {
        await simulationApi.resumeSimulation(simulationId);
        onStateChange("RUNNING");
    };

    const stop = async () => {
        await simulationApi.stopSimulation(simulationId);
        onStop();
    };

    return (
        <div className="card">
            <h2>Simulation Control</h2>

            <div className="button-group">
                <button
                    onClick={pause}
                    disabled={state !== "RUNNING"}
                >
                    Pause
                </button>

                <button
                    onClick={resume}
                    disabled={state !== "PAUSED"}
                >
                    Resume
                </button>

                <button
                    className="danger"
                    onClick={stop}
                >
                    Stop Simulation
                </button>
            </div>
        </div>
    );
}
