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
exports.transcribePtt = void 0;
const https_1 = require("firebase-functions/v2/https");
const admin = __importStar(require("firebase-admin"));
const logger = __importStar(require("firebase-functions/logger"));
const stt_1 = require("./stt");
/**
 * PTT 발화 음성 → 텍스트 전사 (블랙박스 9-A, 서버 STT).
 *
 * 온폰 whisper(190MB 모델 adb push 필요 → 원격 보급 차단)를 대체.
 * 전사만 한다(Gemini 파싱 없음) — PTT 블랙박스는 "발음 그대로"가 미덕이라
 * LLM 해석 불필요(2026-06-19 측정 결정: PTT용 STT = faster-whisper 유지).
 *
 * 흐름: 앱이 PTT WAV 를 Storage(reservation_test/{uid}) 업로드 → gcsUri 전달 →
 *       transcribeAudio(Cloud Run faster-whisper) → { transcript } → 앱이 채팅 게시.
 * 처리 후 업로드 오디오 즉시 삭제(PII, reservation.ts 와 동일 정책).
 *
 * 인증: onCall(request.auth). Cloud Run 호출은 transcribeAudio(stt.ts)가 functions SA
 *       ID 토큰으로 전담 — 새 IAM 불요(run.invoker 기보유, parseReservation W경로와 동일).
 */
const REGION = "asia-northeast3"; // PTT·예약 등과 동일 리전
/** gs://bucket/path → { bucket, path } (reservation.ts 와 동일) */
function parseGcsUri(uri) {
    const m = /^gs:\/\/([^/]+)\/(.+)$/.exec(uri);
    if (!m)
        return null;
    return { bucket: m[1], path: m[2] };
}
exports.transcribePtt = (0, https_1.onCall)({ region: REGION, timeoutSeconds: 120, memory: "256MiB" }, async (request) => {
    var _a;
    if (!request.auth) {
        throw new https_1.HttpsError("unauthenticated", "인증되지 않은 사용자입니다.");
    }
    const { gcsUri } = ((_a = request.data) !== null && _a !== void 0 ? _a : {});
    if (!gcsUri) {
        throw new https_1.HttpsError("invalid-argument", "gcsUri 가 필요합니다.");
    }
    try {
        const transcript = await (0, stt_1.transcribeAudio)(gcsUri);
        return { transcript };
    }
    finally {
        // PII: 성공/실패 무관하게 업로드 오디오 즉시 삭제(전사용 임시 파일). lifecycle 은 백업.
        const parsed = parseGcsUri(gcsUri);
        if (parsed) {
            try {
                await admin.storage().bucket(parsed.bucket).file(parsed.path).delete();
            }
            catch (e) {
                logger.warn("[transcribePtt] 임시 오디오 삭제 실패(무시)", { gcsUri, error: String(e) });
            }
        }
    }
});
//# sourceMappingURL=pttStt.js.map