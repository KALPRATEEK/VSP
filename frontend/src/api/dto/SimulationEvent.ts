export interface SimulationEvent {
    timestamp: number;
    type: string;
    nodeId: string;
    peerId?: string;
    payloadSummary: string;
}
