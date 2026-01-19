import { NetworkConfigurationView } from "../views/NetworkConfigurationView";
import { SimulationImportView } from "../views/SimulationImportView";

export function SimulationSetupPage() {
    return (
        <main className="page">
            <header className="page-header">
                <h1>Distributed Algorithm Simulator</h1>
                <p className="subtitle">
                    Configure, import, and start a new simulation
                </p>
            </header>

            <section className="grid">
                <NetworkConfigurationView />
                <SimulationImportView />
            </section>
        </main>
    );
}
