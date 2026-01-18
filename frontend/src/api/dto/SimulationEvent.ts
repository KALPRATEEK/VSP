import type { EventType } from "./EventType";

export interface SimulationEvent {
  timestamp: number;
  type: EventType;
  nodeId: string;
  peerId: string | null;
  payloadSummary: string;
}
