// api/dto/VisualizationSnapshot.ts
export interface VisualizationSnapshot {
    nodes: {
        nodeId: string;
        state: "INITIALIZED" | "RUNNING";
        isLeader: boolean;
    }[];
    topology: Record<string, string[]>;
    timestamp: number;
}
