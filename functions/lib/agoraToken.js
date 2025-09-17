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
exports.refreshAgoraToken = exports.generateAgoraToken = void 0;
const https_1 = require("firebase-functions/v2/https");
const admin = __importStar(require("firebase-admin"));
const logger = __importStar(require("firebase-functions/logger"));
const agora_token_1 = require("agora-token");
const params_1 = require("firebase-functions/params");
// Agora 프로젝트 설정
const APP_ID = "e5aae3aa18484cd2a1fed0018cfb15bd";
const AGORA_APP_CERTIFICATE = (0, params_1.defineSecret)("AGORA_APP_CERTIFICATE");
/**
 * PTT용 Agora 토큰 생성 함수
 *
 * 요청 파라미터:
 * - regionId: 지역 ID (예: "seoul", "busan", "gyeonggi")
 * - officeId: 사무실 ID (예: "gangnam", "songpa", "haeundae")
 * - userId: 사용자 ID (Firebase Auth UID)
 * - userType: 사용자 타입 ("call_manager", "pickup_driver", "driver")
 *
 * 반환값:
 * - token: 생성된 Agora 토큰
 * - channelName: 사용할 채널명
 * - uid: Agora UID (0 = 자동 할당)
 * - expiresIn: 토큰 만료 시간(초)
 */
exports.generateAgoraToken = (0, https_1.onCall)({
    region: "asia-northeast3",
    secrets: [AGORA_APP_CERTIFICATE],
}, async (request) => {
    // 인증 확인
    if (!request.auth) {
        throw new https_1.HttpsError("unauthenticated", "인증되지 않은 사용자입니다.");
    }
    const data = request.data;
    const context = request;
    // 파라미터 검증
    logger.info(`Raw request data: ${JSON.stringify(data)}`); // 전체 데이터 로깅
    const { regionId, officeId, userType, uid } = data;
    if (!regionId || !officeId || !userType) {
        throw new https_1.HttpsError("invalid-argument", "필수 파라미터가 누락되었습니다: regionId, officeId, userType");
    }
    // UID 파라미터 확인 (선택적이지만 권장)
    const clientUID = uid !== undefined ? Number(uid) : 0;
    logger.info(`[2] Server: Received request for UID: ${clientUID} (raw uid: ${uid})`);
    if (isNaN(clientUID)) {
        throw new https_1.HttpsError("invalid-argument", "UID는 숫자여야 합니다");
    }
    // 유효한 사용자 타입 확인
    const validUserTypes = ["call_manager", "pickup_driver", "driver", "media_session_ptt"];
    if (!validUserTypes.includes(userType)) {
        throw new Error(`유효하지 않은 userType: ${userType}`);
    }
    try {
        logger.info("generateAgoraToken function invoked. Starting diagnostics...");
        logger.info(`Token generation started for user: ${context.auth.uid}, userType: ${userType}, region: ${regionId}, office: ${officeId}`);
        // 진단 1: defineSecret으로 반환된 시크릿 객체의 상태를 로깅합니다.
        logger.info("Inspecting the secret object itself:", AGORA_APP_CERTIFICATE);
        logger.info("Secret object type:", typeof AGORA_APP_CERTIFICATE);
        logger.info("Secret object keys:", AGORA_APP_CERTIFICATE ? Object.keys(AGORA_APP_CERTIFICATE) : "null or undefined");
        // App Certificate 가져오기
        let appCertificate;
        try {
            appCertificate = AGORA_APP_CERTIFICATE.value();
            // 진단 2: .value() 호출 직후의 결과를 명확하게 로깅합니다.
            if (appCertificate === undefined) {
                logger.error("CRITICAL: AGORA_APP_CERTIFICATE.value() returned undefined. The secret is not loaded into the function's runtime environment.");
            }
            else if (appCertificate === null) {
                logger.error("CRITICAL: AGORA_APP_CERTIFICATE.value() returned null.");
            }
            else if (appCertificate === "") {
                logger.warn("Warning: Secret value was read, but it is an empty string.");
            }
            else {
                // 보안을 위해 실제 값은 로깅하지 않습니다.
                logger.info("Success: Secret value was read correctly.");
                logger.info(`Secret value type: ${typeof appCertificate}`);
                logger.info(`Secret value length: ${appCertificate.length}`);
            }
        }
        catch (certError) {
            logger.error("EXCEPTION while accessing App Certificate:", certError);
            logger.error("Error type:", typeof certError);
            logger.error("Error message:", certError instanceof Error ? certError.message : 'Unknown error');
            logger.error("Error stack:", certError instanceof Error ? certError.stack : 'No stack trace');
            appCertificate = undefined;
        }
        if (!appCertificate) {
            logger.error("CRITICAL ERROR: AGORA_APP_CERTIFICATE is not configured!");
            logger.error("Please set the secret using: firebase functions:secrets:set AGORA_APP_CERTIFICATE");
            throw new https_1.HttpsError("failed-precondition", "서버 설정 오류: Agora App Certificate가 구성되지 않았습니다. 관리자에게 문의하세요.");
        }
        // 채널명 생성 (지역_사무실_ptt 형식)
        const channelName = `${regionId}_${officeId}_ptt`;
        // UID 사용 (클라이언트에서 전달받은 값 또는 0)
        const agoraUID = clientUID;
        logger.info(`[3] Server: Building token with UID: ${agoraUID}`);
        // 역할 설정 (PTT는 모두 Publisher 권한 필요)
        const role = agora_token_1.RtcRole.PUBLISHER;
        // 시간 진단 추가
        const dateNow = Date.now();
        const currentDate = new Date();
        logger.info("=== SERVER TIME DIAGNOSTICS ===");
        logger.info(`Date.now(): ${dateNow}`);
        logger.info(`new Date(): ${currentDate.toISOString()}`);
        logger.info(`Timezone: ${Intl.DateTimeFormat().resolvedOptions().timeZone}`);
        logger.info(`UTC Offset: ${currentDate.getTimezoneOffset()} minutes`);
        // 토큰 만료 시간 설정 (24시간) - 상대 시간으로 설정
        const currentTimeInSeconds = Math.floor(Date.now() / 1000);
        const tokenExpireInSeconds = 86400; // 24시간 (상대 시간)
        const privilegeExpireInSeconds = 86400; // 24시간 (상대 시간)
        const privilegeExpiredTs = currentTimeInSeconds + 86400; // 절대 시간 (로깅/저장용)
        // 파라미터 유효성 검사
        if (!APP_ID || APP_ID.length !== 32) {
            logger.error(`Invalid APP_ID: ${APP_ID}`);
            throw new Error("Invalid APP_ID format");
        }
        if (!appCertificate || appCertificate.length !== 32) {
            logger.error(`Invalid App Certificate length: ${appCertificate ? appCertificate.length : 0}, expected 32`);
            logger.warn("Note: App Certificate should be exactly 32 characters without any spaces or special characters");
        }
        if (!channelName || channelName === "") {
            logger.error("Channel name is empty!");
            throw new Error("Channel name is required");
        }
        // 토큰 생성 전 파라미터 로깅
        logger.info("=== Token Generation Parameters ===");
        logger.info(`APP_ID: ${APP_ID} (length: ${APP_ID.length})`);
        logger.info(`App Certificate: ${appCertificate.substring(0, 4)}...${appCertificate.substring(appCertificate.length - 4)} (length: ${appCertificate.length})`);
        logger.info(`Full App Certificate for verification: ${appCertificate}`);
        logger.info(`Channel: ${channelName}`);
        logger.info(`UID: ${agoraUID} (type: ${typeof agoraUID}) - Client provided: ${clientUID !== 0 ? 'YES' : 'NO'}`);
        logger.info(`Role: ${role} (RtcRole.PUBLISHER = ${agora_token_1.RtcRole.PUBLISHER})`);
        logger.info(`Server Time: ${currentTimeInSeconds} (${new Date(currentTimeInSeconds * 1000).toISOString()})`);
        logger.info(`Token Expire: ${tokenExpireInSeconds} seconds (${tokenExpireInSeconds / 3600} hours) - RELATIVE TIME`);
        logger.info(`Privilege Expire: ${privilegeExpireInSeconds} seconds (${privilegeExpireInSeconds / 3600} hours) - RELATIVE TIME`);
        logger.info(`Absolute ExpireTime: ${privilegeExpiredTs} (Date: ${new Date(privilegeExpiredTs * 1000).toISOString()})`);
        logger.info("===================================");
        // 토큰 생성
        let token;
        try {
            token = agora_token_1.RtcTokenBuilder.buildTokenWithUid(APP_ID, appCertificate, channelName, agoraUID, role, tokenExpireInSeconds, // 상대 시간 (초)
            privilegeExpireInSeconds // 상대 시간 (초)
            );
            logger.info(`Token generation result - Length: ${token ? token.length : 0}`);
            if (!token || token === "") {
                logger.error("RtcTokenBuilder returned empty token!");
                logger.error("Possible causes: Invalid App Certificate format or App ID mismatch");
            }
        }
        catch (tokenError) {
            logger.error("Exception during token generation:", tokenError);
            throw tokenError;
        }
        // 토큰 생성 로그 (디버깅용)
        logger.info(`Token generated for channel: ${channelName}, user: ${context.auth.uid}, type: ${userType}`);
        // 토큰 정보를 Firestore에 저장 (선택적, 모니터링용)
        await admin.firestore()
            .collection("token_logs")
            .add({
            userId: context.auth.uid,
            userType,
            regionId,
            officeId,
            channelName,
            createdAt: admin.firestore.FieldValue.serverTimestamp(),
            expiresAt: new Date(privilegeExpiredTs * 1000)
        });
        // 반환 전 최종 토큰 정보 로그
        logger.info(`Final token details - Channel: ${channelName}, Token length: ${token.length}, Token: ${token ? 'HAS_VALUE' : 'EMPTY'}`);
        const result = {
            token,
            channelName,
            uid: agoraUID, // 실제 사용된 UID 반환
            expiresIn: tokenExpireInSeconds, // 상대 시간으로 반환
            appId: APP_ID
        };
        logger.info(`[4] Server: Returning token for UID: ${agoraUID}`);
        logger.info(`Returning result: ${JSON.stringify(Object.assign(Object.assign({}, result), { token: token ? 'HAS_VALUE' : 'EMPTY' }))}`);
        return result;
    }
    catch (error) {
        logger.error("Token generation error:", error);
        logger.error("Error details:", {
            message: error instanceof Error ? error.message : 'Unknown error',
            stack: error instanceof Error ? error.stack : 'No stack trace',
            type: typeof error
        });
        throw new https_1.HttpsError("internal", `토큰 생성 중 오류가 발생했습니다: ${error instanceof Error ? error.message : 'Unknown error'}`);
    }
});
/**
 * 토큰 갱신 함수
 * 만료가 임박한 토큰을 새로운 토큰으로 교체
 */
exports.refreshAgoraToken = (0, https_1.onCall)({
    region: "asia-northeast3",
    secrets: [AGORA_APP_CERTIFICATE],
}, async (request) => {
    // 인증 확인
    if (!request.auth) {
        throw new https_1.HttpsError("unauthenticated", "인증되지 않은 사용자입니다.");
    }
    const { channelName } = request.data;
    if (!channelName) {
        throw new Error("channelName이 필요합니다.");
    }
    // channelName에서 regionId와 officeId 추출
    const parts = channelName.split("_");
    if (parts.length !== 3 || parts[2] !== "ptt") {
        throw new Error("유효하지 않은 채널명 형식입니다.");
    }
    const regionId = parts[0];
    const officeId = parts[1];
    // 갱신된 토큰 직접 생성
    try {
        const appCertificate = AGORA_APP_CERTIFICATE.value();
        if (!appCertificate) {
            throw new https_1.HttpsError("failed-precondition", "서버 설정 오류: Agora App Certificate가 구성되지 않았습니다.");
        }
        // refresh 함수에서는 UID를 받지 않으므로 0 사용 (호환성)
        const agoraUID = 0;
        const channelName = `${regionId}_${officeId}_ptt`;
        const role = agora_token_1.RtcRole.PUBLISHER;
        const tokenExpireInSeconds = 86400; // 24시간 (상대 시간)
        const privilegeExpireInSeconds = 86400; // 24시간 (상대 시간)
        const token = agora_token_1.RtcTokenBuilder.buildTokenWithUid(APP_ID, appCertificate, channelName, agoraUID, role, tokenExpireInSeconds, privilegeExpireInSeconds);
        return {
            token,
            channelName,
            uid: agoraUID,
            expiresIn: tokenExpireInSeconds,
            appId: APP_ID
        };
    }
    catch (error) {
        logger.error("Token refresh error:", error);
        throw new https_1.HttpsError("internal", `토큰 갱신 중 오류가 발생했습니다: ${error instanceof Error ? error.message : 'Unknown error'}`);
    }
});
//# sourceMappingURL=agoraToken.js.map