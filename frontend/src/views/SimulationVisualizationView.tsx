// views/SimulationVisualizationView.tsx
import { useEffect, useRef, useState } from "react";
import { connectSimulationSocket } from "../api/simulationSocket";
import type { VisualizationSnapshot } from "../api/dto/VisualizationSnapshot";

interface Props {
    simulationId: string;
    running: boolean;
}

interface PositionedNode {
    id: string;
    x: number;
    y: number;
    state: string;
    isLeader: boolean;
}

interface Edge {
    from: string;
    to: string;
}

export function SimulationVisualizationView({
                                                simulationId,
                                                running,
                                            }: Props) {
    const socketRef = useRef<WebSocket | null>(null);

    /* ---------- SNAPSHOT-QUEUE + STEP-DARSTELLUNG ---------- */

    const [snapshotQueue, setSnapshotQueue] =
        useState<VisualizationSnapshot[]>([]);

    const [displayedSnapshot, setDisplayedSnapshot] =
        useState<VisualizationSnapshot | null>(null);

    /* ---------------- WebSocket ---------------- */

    useEffect(() => {
        if (!running) {
            socketRef.current?.close();
            socketRef.current = null;
            setSnapshotQueue([]);
            setDisplayedSnapshot(null);
            return;
        }

        const socket = connectSimulationSocket<VisualizationSnapshot>(
            simulationId,
            (snapshot) => {
                setSnapshotQueue((q) => [...q, snapshot]);
            }
        );

        socketRef.current = socket;

        return () => {
            socket.close();
            socketRef.current = null;
        };
    }, [simulationId, running]);

    /* ---------------- STEP-TIMER (1 Sekunde) ---------------- */

    useEffect(() => {
        if (snapshotQueue.length === 0) return;

        const timer = setTimeout(() => {
            setDisplayedSnapshot(snapshotQueue[0]);
            setSnapshotQueue((q) => q.slice(1));
        }, 1000); // ⬅️ 1 Sekunde pro Schritt

        return () => clearTimeout(timer);
    }, [snapshotQueue]);

    /* ---------------- Layout ---------------- */

    const width = 400;
    const height = 400;
    const radius = 150;
    const centerX = width / 2;
    const centerY = height / 2;

    const nodes: PositionedNode[] = displayedSnapshot
        ? displayedSnapshot.nodes.map((n, i) => {
            const angle =
                (2 * Math.PI * i) / displayedSnapshot.nodes.length;
            return {
                id: n.nodeId,
                x: centerX + radius * Math.cos(angle),
                y: centerY + radius * Math.sin(angle),
                state: n.state,
                isLeader: n.isLeader,
            };
        })
        : [];

    const nodeById = Object.fromEntries(
        nodes.map((n) => [n.id, n])
    );

    const edges: Edge[] = displayedSnapshot
        ? Object.entries(displayedSnapshot.topology).flatMap(
            ([from, tos]) =>
                tos.map((to) => ({ from, to }))
        )
        : [];

    /* ---------------- Render ---------------- */

    return (
        <div className="card visualization">
            <h2>Live Visualization</h2>

            {!displayedSnapshot && (
                <p>Waiting for visualization data…</p>
            )}

            {displayedSnapshot && (
                <svg width={width} height={height}>
                    {/* Edges */}
                    {edges.map((e, i) => {
                        const from = nodeById[e.from];
                        const to = nodeById[e.to];
                        if (!from || !to) return null;

                        return (
                            <line
                                key={i}
                                x1={from.x}
                                y1={from.y}
                                x2={to.x}
                                y2={to.y}
                                stroke="#999"
                                strokeWidth={2}
                            />
                        );
                    })}

                    {/* Nodes */}
                    {nodes.map((n) => (
                        <g key={n.id}>
                            <circle
                                cx={n.x}
                                cy={n.y}
                                r={n.isLeader ? 20 : 16}
                                fill={
                                    n.isLeader
                                        ? "gold"
                                        : n.state === "RUNNING"
                                            ? "#1976d2"
                                            : "#aaa"
                                }
                            />
                            <text
                                x={n.x}
                                y={n.y + 4}
                                textAnchor="middle"
                                fontSize={10}
                                fill="black"
                            >
                                {n.id}
                            </text>
                        </g>
                    ))}
                </svg>
            )}
        </div>
    );
}


