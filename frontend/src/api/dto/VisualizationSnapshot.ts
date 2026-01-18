export interface VisualizationSnapshot {
  nodes: NodeState[];
  topology: Record<string, Set<string> | string[]>;
  timestamp: number;
}

export interface NodeState {
  nodeId: string;
  state: string;
  isLeader: boolean;
}
