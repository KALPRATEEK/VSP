export const TopologyType = {
  RING: "RING",
  STAR: "STAR",
  FULLY_CONNECTED: "FULLY_CONNECTED",
} as const;

export type TopologyType =
  typeof TopologyType[keyof typeof TopologyType];
