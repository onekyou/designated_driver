import type { Message, MulticastMessage } from "firebase-admin/messaging";

export type NotificationLevel = "time-sensitive" | "active";

export interface BuildPayloadInput {
  data: Record<string, string>;
  title: string;
  body: string;
  level: NotificationLevel;
  ttlSeconds?: number;
}

function buildCommonBlocks(input: BuildPayloadInput) {
  const ttl = input.ttlSeconds ?? 30;
  const expiration = Math.floor(Date.now() / 1000) + ttl;
  return {
    android: {
      priority: "high" as const,
      ttl: ttl * 1000,
    },
    apns: {
      headers: {
        "apns-push-type": "alert" as const,
        "apns-priority": "10" as const,
        "apns-expiration": String(expiration),
      },
      payload: {
        aps: {
          alert: { title: input.title, body: input.body },
          sound: "default" as const,
          "content-available": 1,
          "mutable-content": 1,
          "interruption-level": input.level,
        },
      },
    },
  };
}

export function buildFcmPayload(input: BuildPayloadInput, token: string): Message {
  return {
    data: input.data,
    ...buildCommonBlocks(input),
    token,
  };
}

export function buildMulticastFcmPayload(
  input: BuildPayloadInput,
  tokens: string[]
): MulticastMessage {
  return {
    data: input.data,
    ...buildCommonBlocks(input),
    tokens,
  };
}
