export interface MetricsSnapshot {
  simulatedTime: number;
  realTimeMillis: number;
  messageCount: number;
  rounds: number;
  converged: boolean;
  leaderId: string | null;
}
