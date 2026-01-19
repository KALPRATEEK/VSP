import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { simulationApi } from "../api/simulationApi";
import type { NetworkConfig, TopologyType } from "../api/dto/NetworkConfig";

export function NetworkConfigurationView() {
  const navigate = useNavigate();

  const [nodeCount, setNodeCount] = useState<number>(5);
  const [topologyType, setTopologyType] =
      useState<TopologyType>("RING");

  // ✅ Algorithmus-State
  const [algorithmId, setAlgorithmId] = useState<string>(
      "flooding-leader-election"
  );

  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const validate = (): boolean => {
    if (!Number.isInteger(nodeCount) || nodeCount <= 0) {
      setError("Node count must be a positive integer.");
      return false;
    }
    if (!algorithmId) {
      setError("Please select an algorithm.");
      return false;
    }
    return true;
  };

  const handleInitialize = async () => {
    setError(null);

    if (!validate()) {
      return;
    }

    const config: NetworkConfig = {
      nodeCount,
      topologyType,
    };

    try {
      setLoading(true);

      // 1️⃣ Netzwerk initialisieren
      const id = await simulationApi.initializeNetwork(config);

      // 2️⃣ Algorithmus konfigurieren (WICHTIG)
      await simulationApi.selectAlgorithm(id, algorithmId);

      // 3️⃣ Zum Dashboard navigieren
      navigate(`/simulation/${id}`);

    } catch (e) {
      setError((e as Error).message);
    } finally {
      setLoading(false);
    }
  };

  return (
      <div className="card">
        <h2>Initialize Simulation</h2>

        <div className="form-group">
          <label htmlFor="nodeCount">Node Count</label>
          <input
              id="nodeCount"
              type="number"
              min={1}
              value={nodeCount}
              onChange={(e) => setNodeCount(Number(e.target.value))}
          />
        </div>

        <div className="form-group">
          <label htmlFor="topology">Topology</label>
          <select
              id="topology"
              value={topologyType}
              onChange={(e) =>
                  setTopologyType(e.target.value as TopologyType)
              }
          >
            <option value="LINE">Line</option>
            <option value="RING">Ring</option>
            <option value="GRID">Grid</option>
            <option value="RANDOM">Random</option>
          </select>
        </div>

        {/* ✅ Algorithmus-Dropdown */}
        <div className="form-group">
          <label htmlFor="algorithm">Algorithm</label>
          <select
              id="algorithm"
              value={algorithmId}
              onChange={(e) => setAlgorithmId(e.target.value)}
          >
            <option value="flooding-leader-election">
              Flooding Leader Election
            </option>
            <option value="bully-election">
              Bully Algorithm
            </option>
          </select>
        </div>

        <button
            className="primary"
            onClick={handleInitialize}
            disabled={loading}
        >
          Initialize Network
        </button>

        {loading && <p>Initializing simulation…</p>}
        {error && <p className="error">{error}</p>}
      </div>
  );
}

