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

/**
 * PTT wake용 data-only high-priority 멀티캐스트 페이로드.
 *  notification 블록 없음 → 클라 onMessageReceived 가 Doze에서도 호출되어 fast-join 수행.
 *  ttl 짧게(10s) — 무전기 즉시성, 만료 후 도착은 무의미.
 */
export function buildMulticastPttWakePayload(
  data: Record<string, string>,
  tokens: string[]
): MulticastMessage {
  const ttlSeconds = 10;
  const expiration = Math.floor(Date.now() / 1000) + ttlSeconds;
  return {
    data,
    android: {
      priority: "high" as const,
      ttl: ttlSeconds * 1000,
    },
    apns: {
      headers: {
        "apns-push-type": "background" as const,
        "apns-priority": "10" as const,
        "apns-expiration": String(expiration),
      },
      payload: {
        aps: {
          "content-available": 1,
        },
      },
    },
    tokens,
  };
}
