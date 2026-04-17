"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.buildFcmPayload = buildFcmPayload;
exports.buildMulticastFcmPayload = buildMulticastFcmPayload;
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
//# sourceMappingURL=fcmPayload.js.map