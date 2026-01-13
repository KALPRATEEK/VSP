export type TopologyType = "LINE" | "RING" | "GRID" | "RANDOM";

export interface NetworkConfig {
  nodeCount: number;
  topologyType: TopologyType;
}
