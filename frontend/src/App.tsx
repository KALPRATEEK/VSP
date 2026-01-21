// App.tsx
import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import { SimulationSetupPage } from "./pages/SimulationSetupPage";
import { SimulationDashboardPage } from "./pages/SimulationDashboardPage";
import { DistributedDashboard } from "./views/DistributedDashboard";

export function App() {
    return (
        <BrowserRouter>
            <Routes>
                <Route path="/" element={<SimulationSetupPage />} />
                <Route path="/simulation/:id" element={<SimulationDashboardPage />} />
                <Route path="/distributed-dashboard" element={<DistributedDashboard />} />
                <Route path="*" element={<Navigate to="/" />} />
            </Routes>
        </BrowserRouter>
    );
}


