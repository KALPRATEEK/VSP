export type NodeId = string;

export interface VisNode {
    id: NodeId;
    x: number;
    y: number;
}

export interface VisEdge {
    from: NodeId;
    to: NodeId;
    highlightUntil?: number;
    label?: string;
}

export interface GraphState {
    nodes: Record<NodeId, VisNode>;
    edges: VisEdge[];
}
