import { useState, useEffect } from "react";
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
  
  // 🚀 Distributed Mode State
  const [useDistributedMode, setUseDistributedMode] = useState(false);
  const [distributedAvailable, setDistributedAvailable] = useState(false);

  // Check if distributed mode is available
  useEffect(() => {
    fetch('http://localhost:8080/api/distributed/available')
      .then(res => res.json())
      .then(data => {
        setDistributedAvailable(data.available);
        console.log('Distributed mode available:', data.available);
      })
      .catch(err => {
        console.warn('Could not check distributed mode:', err);
        setDistributedAvailable(false);
      });
  }, []);

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

    try {
      setLoading(true);

      if (useDistributedMode) {
        // 🚀 Distributed Mode: Start real Docker containers
        console.log(`Starting ${nodeCount} distributed containers with ${topologyType} topology`);
        
        const response = await fetch('http://localhost:8080/api/distributed/start', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            nodeCount,
            topology: topologyType
          })
        });
        
        const data = await response.json();
        
        if (data.success) {
          // Navigate immediately to distributed dashboard
          navigate(`/distributed-dashboard`, { state: { simulationId: data.simulationId } });
        } else {
          setError(data.error || 'Failed to start distributed containers');
        }
      } else {
        // Virtual Mode: Normal in-memory simulation
        const config: NetworkConfig = {
          nodeCount,
          topologyType,
        };

        // 1️⃣ Netzwerk initialisieren
        const id = await simulationApi.initializeNetwork(config);

        // 2️⃣ Algorithmus konfigurieren (WICHTIG)
        await simulationApi.selectAlgorithm(id, algorithmId);

        // 3️⃣ Zum Dashboard navigieren
        navigate(`/simulation/${id}`);
      }

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
          </select>
        </div>

        {/* 🚀 Distributed Mode Toggle */}
        {distributedAvailable && (
          <div className="form-group" style={{ 
            backgroundColor: '#f0f9ff', 
            padding: '15px', 
            borderRadius: '8px',
            border: '2px solid #3b82f6'
          }}>
            <label style={{ 
              display: 'flex', 
              alignItems: 'center',
              cursor: 'pointer',
              fontSize: '16px',
              fontWeight: 'bold'
            }}>
              <input
                type="checkbox"
                checked={useDistributedMode}
                onChange={(e) => setUseDistributedMode(e.target.checked)}
                style={{ marginRight: '10px', width: '20px', height: '20px' }}
              />
              🚀 Use Real Distributed System (Docker Containers)
            </label>
            <p style={{ 
              marginTop: '10px', 
              marginLeft: '30px',
              fontSize: '14px',
              color: useDistributedMode ? '#059669' : '#6b7280'
            }}>
              {useDistributedMode 
                ? `✅ Will start ${nodeCount} real Docker containers with UDP communication. Each node runs as a separate process!`
                : `Virtual mode: Fast in-memory simulation (all nodes in one process)`}
            </p>
            {useDistributedMode && (
              <p style={{ 
                marginTop: '5px', 
                marginLeft: '30px',
                fontSize: '12px',
                color: '#dc2626',
                fontStyle: 'italic'
              }}>
                ⏱️ Note: Starting containers may take 1-2 minutes
              </p>
            )}
          </div>
        )}

        <button
            className="primary"
            onClick={handleInitialize}
            disabled={loading}
            style={{ 
              fontSize: '16px',
              padding: '12px 24px',
              backgroundColor: useDistributedMode ? '#059669' : undefined
            }}
        >
          {useDistributedMode 
            ? `🚀 Start ${nodeCount} Distributed Containers` 
            : 'Initialize Network'}
        </button>

        {loading && <p style={{ color: '#3b82f6', fontWeight: 'bold' }}>
          {useDistributedMode 
            ? `⏳ Starting ${nodeCount} Docker containers... This may take 1-2 minutes. Check Docker Desktop!` 
            : 'Initializing simulation…'}
        </p>}
        {error && <p className="error">{error}</p>}
      </div>
  );
}

