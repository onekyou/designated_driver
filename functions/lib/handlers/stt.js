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
exports.transcribeAudio = transcribeAudio;
const logger = __importStar(require("firebase-functions/logger"));
const google_auth_library_1 = require("google-auth-library");
/**
 * 서버 STT(귀) — 통화 녹음(GCS .m4a) → 한국어 전사 텍스트.
 *
 * 엔진 = faster-whisper (Cloud Run `transcribe-service`). 6/7 측정에서 직접 통과시킨 검증 자산:
 *   .m4a 네이티브 ✅ / 한국어 정확(옥천명→옥천면 복원) ✅ / 무료·로컬·PII안전 계열 ✅.
 *
 * ★ 이 모듈이 STT의 **유일한 교체 지점**. 서버 무게가 진짜 문제로 판명되면(콜드스타트·메모리),
 *   transcribeAudio 구현만 다른 엔진(예: Google STT+ffmpeg)으로 교체. 호출부(reservation.ts)는 불변.
 *
 * 인증: Cloud Run(--no-allow-unauthenticated)에 ID 토큰(audience=서비스 URL)으로 호출.
 *   functions 서비스계정에 roles/run.invoker 필요.
 */
// Cloud Run transcribe-service URL. 배포 후 `firebase functions:secrets`/`.env` 또는
// --set-env-vars TRANSCRIBE_SERVICE_URL=... 로 주입.
const SERVICE_URL = process.env.TRANSCRIBE_SERVICE_URL || "";
const auth = new google_auth_library_1.GoogleAuth();
/**
 * GCS 오디오를 전사한다.
 * @param gcsUri gs://bucket/path 형식
 * @returns 전사 텍스트(빈 통화면 "")
 * @throws Error STT 실패(호출부에서 처리)
 */
async function transcribeAudio(gcsUri) {
    var _a, _b, _c;
    if (!SERVICE_URL) {
        throw new Error("TRANSCRIBE_SERVICE_URL 미설정 — Cloud Run STT 서비스 URL 환경변수 필요");
    }
    // Cloud Run 인증: audience = 서비스 URL 인 ID 토큰.
    const client = await auth.getIdTokenClient(SERVICE_URL);
    const headers = await client.getRequestHeaders();
    const start = Date.now();
    const resp = await fetch(`${SERVICE_URL}/transcribe`, {
        method: "POST",
        headers: Object.assign(Object.assign({}, headers), { "Content-Type": "application/json" }),
        body: JSON.stringify({ gcsUri }),
    });
    if (!resp.ok) {
        const detail = await resp.text().catch(() => "");
        logger.error("[stt] transcribe-service 오류", { status: resp.status, detail: detail.slice(0, 300) });
        throw new Error(`STT 서비스 오류 (${resp.status})`);
    }
    const data = (await resp.json());
    logger.info("[stt] 전사 완료", {
        latencyMs: Date.now() - start,
        model: data.model,
        len: (_b = (_a = data.transcript) === null || _a === void 0 ? void 0 : _a.length) !== null && _b !== void 0 ? _b : 0,
    });
    return (_c = data.transcript) !== null && _c !== void 0 ? _c : "";
}
//# sourceMappingURL=stt.js.map