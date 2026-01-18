export const EventType = {
  MESSAGE_SENT: "MESSAGE_SENT",
  MESSAGE_RECEIVED: "MESSAGE_RECEIVED",
  STATE_CHANGED: "STATE_CHANGED",
  LEADER_ELECTED: "LEADER_ELECTED",
  ERROR: "ERROR",
} as const;

export type EventType =
  typeof EventType[keyof typeof EventType];

