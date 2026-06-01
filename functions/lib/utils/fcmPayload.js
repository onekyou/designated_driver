"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.buildFcmPayload = buildFcmPayload;
exports.buildMulticastFcmPayload = buildMulticastFcmPayload;
exports.buildMulticastPttWakePayload = buildMulticastPttWakePayload;
function buildCommonBlocks(input) {
    var _a;
    const ttl = (_a = input.ttlSeconds) !== null && _a !== void 0 ? _a : 30;
    const expiration = Math.floor(Date.now() / 1000) + ttl;
    return {
        android: {
            priority: "high",
            ttl: ttl * 1000,
        },
        apns: {
            headers: {
                "apns-push-type": "alert",
                "apns-priority": "10",
                "apns-expiration": String(expiration),
            },
            payload: {
                aps: {
                    alert: { title: input.title, body: input.body },
                    sound: "default",
                    "content-available": 1,
                    "mutable-content": 1,
                    "interruption-level": input.level,
                },
            },
        },
    };
}
function buildFcmPayload(input, token) {
    return Object.assign(Object.assign({ data: input.data }, buildCommonBlocks(input)), { token });
}
function buildMulticastFcmPayload(input, tokens) {
    return Object.assign(Object.assign({ data: input.data }, buildCommonBlocks(input)), { tokens });
}
/**
 * PTT wake용 data-only high-priority 멀티캐스트 페이로드.
 *  notification 블록 없음 → 클라 onMessageReceived 가 Doze에서도 호출되어 fast-join 수행.
 *  ttl 짧게(10s) — 무전기 즉시성, 만료 후 도착은 무의미.
 */
function buildMulticastPttWakePayload(data, tokens) {
    const ttlSeconds = 10;
    const expiration = Math.floor(Date.now() / 1000) + ttlSeconds;
    return {
        data,
        android: {
            priority: "high",
            ttl: ttlSeconds * 1000,
        },
        apns: {
            headers: {
                "apns-push-type": "background",
                "apns-priority": "10",
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
//# sourceMappingURL=fcmPayload.js.map