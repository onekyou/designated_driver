"use strict";
var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    var desc = Object.getOwnPropertyDescriptor(m, k);
    if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
      desc = { enumerable: true, get: function() { return m[k]; } };
    }
    Object.defineProperty(o, k2, desc);
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __setModuleDefault = (this && this.__setModuleDefault) || (Object.create ? (function(o, v) {
    Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
    o["default"] = v;
});
var __importStar = (this && this.__importStar) || (function () {
    var ownKeys = function(o) {
        ownKeys = Object.getOwnPropertyNames || function (o) {
            var ar = [];
            for (var k in o) if (Object.prototype.hasOwnProperty.call(o, k)) ar[ar.length] = k;
            return ar;
        };
        return ownKeys(o);
    };
    return function (mod) {
        if (mod && mod.__esModule) return mod;
        var result = {};
        if (mod != null) for (var k = ownKeys(mod), i = 0; i < k.length; i++) if (k[i] !== "default") __createBinding(result, mod, k[i]);
        __setModuleDefault(result, mod);
        return result;
    };
})();
Object.defineProperty(exports, "__esModule", { value: true });
exports.sendPttWake = exports.generateAgoraToken = void 0;
const https_1 = require("firebase-functions/v2/https");
const admin = __importStar(require("firebase-admin"));
const logger = __importStar(require("firebase-functions/logger"));
const agora_token_1 = require("agora-token");
const params_1 = require("firebase-functions/params");
const fcmPayload_1 = require("../utils/fcmPayload");
const REGION = "asia-northeast3";
// Agora App ID는 공개값 (클라이언트 앱에도 임베드되는 식별자). Certificate만 비밀.
const APP_ID = "e5aae3aa18484cd2a1fed0018cfb15bd";
const AGORA_APP_CERTIFICATE = (0, params_1.defineSecret)("AGORA_APP_CERTIFICATE");
const TOKEN_EXPIRE_SECONDS = 86400; // 24h (상대 시간)
/**
 * PTT용 Agora RTC 토큰 발급 (FCM wake + Trigger Join 모델).
 *  입력: { regionId, officeId, uid? }  (uid 미전달 시 0 = Agora 자동 할당)
 *  채널명 = `${regionId}_${officeId}_ptt` (사무실당 1채널, 매니저+기사 공유)
 *  반환: { token, channelName, uid, expiresIn, appId }
 *  (2025-08 검증 템플릿 agoraToken.ts 복구본 — PoC 범위로 진단 로깅·token_logs·refresh 제거)
 */
exports.generateAgoraToken = (0, https_1.onCall)({ region: REGION, secrets: [AGORA_APP_CERTIFICATE] }, async (request) => {
    var _a;
    if (!request.auth) {
        throw new https_1.HttpsError("unauthenticated", "인증되지 않은 사용자입니다.");
    }
    const { regionId, officeId, uid } = (_a = request.data) !== null && _a !== void 0 ? _a : {};
    if (!regionId || !officeId) {
        throw new https_1.HttpsError("invalid-argument", "필수 파라미터 누락: regionId, officeId");
    }
    const agoraUID = uid !== undefined ? Number(uid) : 0;
    if (isNaN(agoraUID)) {
        throw new https_1.HttpsError("invalid-argument", "uid는 숫자여야 합니다.");
    }
    const appCertificate = AGORA_APP_CERTIFICATE.value();
    if (!appCertificate || appCertificate.length !== 32) {
        logger.error(`[generateAgoraToken] AGORA_APP_CERTIFICATE 미구성/형식오류 (len=${appCertificate ? appCertificate.length : 0}, expected 32). 'firebase functions:secrets:set AGORA_APP_CERTIFICATE' 필요.`);
        throw new https_1.HttpsError("failed-precondition", "서버 설정 오류: Agora App Certificate가 구성되지 않았습니다.");
    }
    if (APP_ID.length !== 32) {
        throw new https_1.HttpsError("internal", "APP_ID 형식 오류");
    }
    const channelName = `${regionId}_${officeId}_ptt`;
    let token;
    try {
        token = agora_token_1.RtcTokenBuilder.buildTokenWithUid(APP_ID, appCertificate, channelName, agoraUID, agora_token_1.RtcRole.PUBLISHER, // PTT는 송수신 모두 PUBLISHER 토큰. 수신전용은 클라가 enableLocalAudio(false)로 제어.
        TOKEN_EXPIRE_SECONDS, TOKEN_EXPIRE_SECONDS);
    }
    catch (err) {
        logger.error("[generateAgoraToken] 토큰 생성 예외:", err);
        throw new https_1.HttpsError("internal", `토큰 생성 오류: ${err instanceof Error ? err.message : "unknown"}`);
    }
    if (!token) {
        throw new https_1.HttpsError("internal", "토큰 생성 실패 (빈 토큰)");
    }
    logger.info(`[generateAgoraToken] auth=${request.auth.uid} channel=${channelName} agoraUID=${agoraUID}`);
    return { token, channelName, uid: agoraUID, expiresIn: TOKEN_EXPIRE_SECONDS, appId: APP_ID };
});
/**
 * PTT 발화 시작 시 수신측(사무실 기사) wake.
 *  입력: { provinceId, cityId, officeId, channelName, senderName? }
 *  office designated_drivers 전체 fcmToken 수집(발신자 제외) → data-only high-priority FCM.
 *  수신 클라(driver_app)는 type=ptt_dispatch 분기에서 channelName으로 fast-join.
 */
exports.sendPttWake = (0, https_1.onCall)({ region: REGION }, async (request) => {
    var _a;
    if (!request.auth) {
        throw new https_1.HttpsError("unauthenticated", "인증되지 않은 사용자입니다.");
    }
    const { provinceId, cityId, officeId, channelName, senderName } = (_a = request.data) !== null && _a !== void 0 ? _a : {};
    if (!provinceId || !cityId || !officeId || !channelName) {
        throw new https_1.HttpsError("invalid-argument", "필수 파라미터 누락: provinceId, cityId, officeId, channelName");
    }
    const senderId = request.auth.uid;
    const officeRef = admin.firestore()
        .doc(`provinces/${provinceId}/cities/${cityId}/offices/${officeId}`);
    // 대리기사 토큰 수집 — onChatMessageCreated 패턴 정합 (authUid/docId == sender 제외)
    const designatedSnapshot = await officeRef.collection("designated_drivers").get();
    const tokens = designatedSnapshot.docs
        .filter((d) => {
        const data = d.data();
        if (data.authUid === senderId || d.id === senderId)
            return false;
        return typeof data.fcmToken === "string" && data.fcmToken.length > 0;
    })
        .map((d) => d.data().fcmToken);
    const uniqueTokens = Array.from(new Set(tokens));
    logger.info(`[sendPttWake] channel=${channelName} 대상 기사 토큰=${uniqueTokens.length}`);
    if (uniqueTokens.length === 0) {
        return { sent: 0, failed: 0 };
    }
    const multicast = (0, fcmPayload_1.buildMulticastPttWakePayload)({
        type: "ptt_dispatch",
        channelName,
        senderName: senderName !== null && senderName !== void 0 ? senderName : "매니저",
        provinceId,
        cityId,
        officeId,
    }, uniqueTokens);
    const response = await admin.messaging().sendEachForMulticast(multicast);
    logger.info(`[sendPttWake] 성공=${response.successCount} 실패=${response.failureCount}`);
    return { sent: response.successCount, failed: response.failureCount };
});
//# sourceMappingURL=ptt.js.map