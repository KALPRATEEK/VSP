import { useState } from "react";
import { simulationApi } from "../api/simulationApi";
import type { NetworkConfig, TopologyType } from "../api/dto/NetworkConfig";

export function NetworkConfigurationView() {
  const [nodeCount, setNodeCount] = useState<number>(5);
  const [topologyType, setTopologyType] =
    useState<TopologyType>("RING");

  const [error, setError] = useState<string | null>(null);
  const [simulationId, setSimulationId] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const validate = (): boolean => {
    if (!Number.isInteger(nodeCount) || nodeCount <= 0) {
      setError("Node count must be a positive integer.");
      return false;
    }
    return true;
  };

  const handleInitialize = async () => {
    setError(null);
    setSimulationId(null);

    if (!validate()) {
      return;
    }

    const config: NetworkConfig = {
      nodeCount,
      topologyType,
    };

    try {
      setLoading(true);
      const id = await simulationApi.initializeNetwork(config);
      setSimulationId(id);
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

      <button
        className="primary"
        onClick={handleInitialize}
        disabled={loading}
      >
        Initialize Network
      </button>

      {loading && <p>Initializing simulation…</p>}
      {error && <p className="error">{error}</p>}
      {simulationId && (
        <p className="success">
          Simulation initialized (ID: {simulationId})
        </p>
      )}
    </div>
  );
}
