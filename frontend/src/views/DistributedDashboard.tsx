import React, { useEffect, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { NetworkVisualization } from './NetworkVisualization';
import { MetricsPanel } from './MetricsPanel';
import './DistributedDashboard.css';

interface NodeStatus {
  nodeId: string;
  currentLeader: string | null;
  messagesSent: number;
  messagesReceived: number;
  lastUpdate: number;
}

interface Metrics {
  nodeCount: number;
  totalMessagesSent: number;
  totalMessagesReceived: number;
  consensusLeader: string | null;
  hasConsensus: boolean;
  leaderVotes: Record<string, number>;
  nodes: Record<string, NodeStatus>;
  sc6Warning?: string;
}

interface VisualizationData {
  nodes: Array<{
    id: string;
    currentLeader: string | null;
    messagesSent: number;
    messagesReceived: number;
    isLeader: boolean;
  }>;
  edges: Array<{ from: string; to: string }>;
  metrics: Metrics;
}

export const DistributedDashboard: React.FC = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const [simulationId, setSimulationId] = useState<string>('');
  const [metrics, setMetrics] = useState<Metrics | null>(null);
  const [visualization, setVisualization] = useState<VisualizationData | null>(null);
  const [loading, setLoading] = useState(true);
  const [paused, setPaused] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    // Extract simulation ID from location state or default to 'distributed'
    const state = location.state as { simulationId?: string };
    const id = state?.simulationId || 'distributed';
    setSimulationId(id);
  }, [location]);

  useEffect(() => {
    if (!simulationId) return;

    const fetchData = async () => {
      try {
        // Fetch metrics
        const metricsResponse = await fetch(`http://localhost:8080/api/distributed/${simulationId}/metrics`);
        if (metricsResponse.ok) {
          const metricsData = await metricsResponse.json();
          setMetrics(metricsData);
        }

        // Fetch visualization
        const vizResponse = await fetch(`http://localhost:8080/api/distributed/${simulationId}/visualization`);
        if (vizResponse.ok) {
          const vizData = await vizResponse.json();
          setVisualization(vizData);
        }

        setError(null);
      } catch (err) {
        console.error('Failed to fetch distributed system data:', err);
        setError('Failed to connect to backend');
      } finally {
        setLoading(false);
      }
    };

    fetchData();
    const interval = setInterval(fetchData, 2000); // Refresh every 2 seconds
    return () => clearInterval(interval);
  }, [simulationId]);

  const handlePause = async () => {
    try {
      const response = await fetch(`http://localhost:8080/api/distributed/${simulationId}/pause`, {
        method: 'POST',
      });
      if (response.ok) {
        setPaused(true);
      }
    } catch (err) {
      console.error('Failed to pause:', err);
      setError('Failed to pause simulation');
    }
  };

  const handleResume = async () => {
    try {
      const response = await fetch(`http://localhost:8080/api/distributed/${simulationId}/resume`, {
        method: 'POST',
      });
      if (response.ok) {
        setPaused(false);
      }
    } catch (err) {
      console.error('Failed to resume:', err);
      setError('Failed to resume simulation');
    }
  };

  const handleStop = async () => {
    if (!confirm('Really stop all containers? This will terminate the distributed system.')) {
      return;
    }

    try {
      await fetch('http://localhost:8080/api/distributed/stop', {
        method: 'POST',
      });
      navigate('/');
    } catch (err) {
      console.error('Failed to stop:', err);
      setError('Failed to stop containers');
    }
  };

  if (loading) {
    return (
      <div className="simulation-dashboard">
        <div className="card">
          <h2>⏳ Loading Distributed System...</h2>
          <p>Waiting for nodes to report their status...</p>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="simulation-dashboard">
        <div className="card error-card">
          <h2>❌ Error</h2>
          <p>{error}</p>
          <button onClick={() => navigate('/')}>Back to Setup</button>
        </div>
      </div>
    );
  }

  return (
    <div className="simulation-dashboard">
      <header className="dashboard-header">
        <h1>🚀 Distributed System Dashboard</h1>
        <div className="simulation-info">
          <span className="simulation-id">Simulation: {simulationId}</span>
          <span className={`status-badge ${paused ? 'paused' : 'running'}`}>
            {paused ? '⏸️ Paused' : '▶️ Running'}
          </span>
        </div>
      </header>

      <div className="dashboard-grid">
        {/* Control Panel */}
        <div className="control-panel card">
          <h2>⚙️ Control</h2>
          <div className="control-buttons">
            {!paused ? (
              <button onClick={handlePause} className="control-button pause-button">
                ⏸️ Pause All Nodes
              </button>
            ) : (
              <button onClick={handleResume} className="control-button resume-button">
                ▶️ Resume All Nodes
              </button>
            )}
            <button onClick={handleStop} className="control-button stop-button">
              ⏹️ Stop System
            </button>
            <button onClick={() => navigate('/')} className="control-button">
              🏠 Back to Setup
            </button>
          </div>

          <div className="system-info">
            <h3>System Information</h3>
            <div className="info-row">
              <span className="info-label">Mode:</span>
              <span className="info-value">Real Distributed (UDP)</span>
            </div>
            <div className="info-row">
              <span className="info-label">Nodes:</span>
              <span className="info-value">{metrics?.nodeCount || 0}</span>
            </div>
            <div className="info-row">
              <span className="info-label">Consensus Leader:</span>
              <span className="info-value">
                {metrics?.consensusLeader || 'None'}
                {metrics?.hasConsensus && ' ✅'}
              </span>
            </div>
          </div>
        </div>

        {/* SC6 Warning */}
        {metrics?.sc6Warning && (
          <div className="card" style={{ 
            backgroundColor: '#fff3cd', 
            border: '2px solid #ffc107',
            padding: '20px',
            marginBottom: '20px'
          }}>
            <h3 style={{ color: '#856404', marginTop: 0 }}>⚠️ Algorithm Correctness Warning</h3>
            <p style={{ color: '#856404', margin: '10px 0', fontSize: '16px', fontWeight: 'bold' }}>
              {metrics.sc6Warning}
            </p>
            <p style={{ color: '#856404', margin: '5px 0', fontSize: '14px' }}>
              The Flooding Leader Election algorithm should elect the node with the highest ID as the leader. 
              This indicates a potential bug in the algorithm implementation or message delivery issues.
            </p>
          </div>
        )}

        {/* Metrics Panel */}
        {metrics && (
          <div className="metrics-container card">
            <MetricsPanel 
              messagesSent={metrics.totalMessagesSent}
              messagesReceived={metrics.totalMessagesReceived}
              activeNodes={metrics.nodeCount}
              consensusAchieved={metrics.hasConsensus}
            />
          </div>
        )}

        {/* Visualization */}
        {visualization && (
          <div className="visualization-container card">
            <h2>Network Visualization</h2>
            <NetworkVisualization 
              nodes={visualization.nodes}
              edges={visualization.edges}
            />
          </div>
        )}

        {/* Node Status Table */}
        {metrics?.nodes && (
          <div className="nodes-table-container card">
            <h2>📋 Node Status</h2>
            <table className="nodes-table">
              <thead>
                <tr>
                  <th>Node ID</th>
                  <th>Current Leader</th>
                  <th>Messages Sent</th>
                  <th>Messages Received</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                {Object.entries(metrics.nodes).map(([nodeId, status]) => (
                  <tr key={nodeId}>
                    <td className="node-id">{nodeId}</td>
                    <td>{status.currentLeader || '-'}</td>
                    <td>{status.messagesSent}</td>
                    <td>{status.messagesReceived}</td>
                    <td>
                      {status.currentLeader === nodeId && (
                        <span className="leader-badge">👑 Leader</span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

      </div>
    </div>
  );
};
