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
exports.parseReservation = void 0;
const https_1 = require("firebase-functions/v2/https");
const admin = __importStar(require("firebase-admin"));
const logger = __importStar(require("firebase-functions/logger"));
const genai_1 = require("@google/genai");
const stt_1 = require("./stt");
/**
 * 통화녹음 → 범용 예약 입력엔진 (검증 슬라이스).
 *
 * 한 콜을 두 경로로 채점:
 *  - W(목표): transcript(싼 STT) → Gemini 텍스트
 *  - C(천장): gcsUri 오디오 → Gemini 멀티모달
 * 둘의 이해도 격차 + 콜당 단가를 실측. 뇌는 둘 다 Gemini(이해 = 제품 본체).
 *
 * 출력 = coupon_app/app/lib/types.ts 의 SalonBookingPayload 평탄형(문서 계약, 코드 의존 0).
 * 비예약 콜(취소·문의·잘못걸림·거래처)에서 헛예약을 만들지 않도록 isReservation/intent 로 판정.
 */
const REGION = "asia-northeast3"; // 함수 리전 (PTT 등과 동일)
const VERTEX_LOCATION = "us-central1"; // Gemini 가용 리전 (ne3 회피, 교차리전 호출)
const MODEL = "gemini-2.5-flash";
const PROJECT = process.env.GCLOUD_PROJECT || "calldetector-5d61e";
// 평탄 스키마 — union(sealed) 금지. moduleType 디스크리미네이터 + 업종별 필드는 옵셔널 평탄.
const T = genai_1.Type;
const RESERVATION_SCHEMA = {
    type: T.OBJECT,
    properties: {
        isReservation: { type: T.BOOLEAN }, // 예약/변경 콜인가 (거짓양성 방지의 1차 게이트)
        intent: {
            type: T.STRING,
            enum: ["booking.request", "change", "cancel", "inquiry", "not_reservation", "other"],
        },
        moduleType: {
            type: T.STRING,
            enum: ["salon_booking", "designated_driver", "taxi", "restaurant", "other"],
        },
        service: { type: T.STRING }, // 미용: 시술/서비스
        datetimeText: { type: T.STRING }, // 원문 그대로 ("내일 3시")
        datetimeIso: { type: T.STRING }, // recordedAt 기준 정규화 (불가 시 빈 문자열)
        partySize: { type: T.INTEGER },
        callerPhone: { type: T.STRING },
        from: { type: T.STRING }, // 대리/택시: 출발
        to: { type: T.STRING }, // 대리/택시: 도착
        fare: { type: T.INTEGER },
        notes: { type: T.STRING },
        confidence: { type: T.NUMBER }, // 0~1
        rawTranscript: { type: T.STRING }, // (C경로) 모델 전사. STT 품질 비교용.
    },
    required: ["isReservation", "intent", "moduleType", "confidence"],
};
function buildPrompt(recordedAt) {
    return [
        "너는 한국 동네 가게(미용실·대리운전·택시·식당)로 걸려온 전화 통화의 녹취를 분석한다.",
        "통화에서 '예약/변경/취소' 의도와 핵심 정보를 구조화해 JSON 으로만 출력하라.",
        "",
        "규칙:",
        "1) 먼저 이 통화가 예약(또는 변경·취소) 콜인지 판단한다. 단순 문의·잘못 걸린 전화·거래처/광고·일상 대화면",
        "   isReservation=false, intent='not_reservation' 으로 두고 헛예약을 만들지 마라.",
        "2) 업종을 자동 판별해 moduleType 을 채운다(미용=salon_booking, 대리=designated_driver, 택시=taxi, 식당=restaurant).",
        "3) 상대 시각('내일','모레 3시')은 통화 시각을 기준으로 datetimeIso(ISO8601, KST)로 정규화한다.",
        `   통화 시각 = ${recordedAt}. 정규화 불가하면 datetimeIso 는 빈 문자열, datetimeText 에 원문을 남겨라.`,
        "4) 미용은 service(시술)·datetime 이 핵심. 대리/택시는 from·to·fare 가 핵심.",
        "5) 통화에 없거나 모르는 필드는 비워라 — 문자열은 빈 문자열(\"\"), 숫자(partySize·fare)는 명시됐을 때만 넣어라(추정·0 금지). 'null'·'없음' 같은 단어를 값으로 절대 쓰지 마라.",
        "6) confidence(0~1)는 근거가 부족하면(짧은 단편·콜 종류 불확정) 정직하게 낮춰라.",
        "7) 손님 전화번호가 대화에 나오면 callerPhone 에 담는다.",
        "",
        "예시(대화 → 핵심 판정):",
        "- '내일 오후 3시에 커트랑 펌 돼요?' → isReservation=true, intent='booking.request', moduleType='salon_booking', service='커트, 펌', datetimeText='내일 오후 3시'",
        "- '어제 3시 예약한 거 4시로 바꿔주세요' → isReservation=true, intent='change', moduleType='salon_booking', datetimeText='4시'",
        "- '거기 OO상사죠? 세금계산서 발행 건으로 전화드렸어요' → isReservation=false, intent='not_reservation', moduleType='other' (거래처 — 헛예약 금지)",
        "- '지금 영업하세요?' → isReservation=false, intent='inquiry', moduleType='other' (단순 문의 — 예약 아님)",
    ].join("\n");
}
async function runGemini(parts) {
    const ai = new genai_1.GoogleGenAI({ vertexai: true, project: PROJECT, location: VERTEX_LOCATION });
    const start = Date.now();
    const response = await ai.models.generateContent({
        model: MODEL,
        contents: [{ role: "user", parts }],
        config: {
            temperature: 0,
            responseMimeType: "application/json",
            responseSchema: RESERVATION_SCHEMA,
        },
    });
    const latencyMs = Date.now() - start;
    const text = response.text;
    if (!text) {
        throw new https_1.HttpsError("internal", "Gemini 응답이 비어 있습니다.");
    }
    let reservation;
    try {
        reservation = JSON.parse(text);
    }
    catch (e) {
        logger.error("[parseReservation] JSON 파싱 실패", { text, error: String(e) });
        throw new https_1.HttpsError("internal", "Gemini 응답 JSON 파싱 실패");
    }
    return { reservation, latencyMs };
}
/** gs://bucket/path → { bucket, path } */
function parseGcsUri(uri) {
    const m = /^gs:\/\/([^/]+)\/(.+)$/.exec(uri);
    if (!m)
        return null;
    return { bucket: m[1], path: m[2] };
}
exports.parseReservation = (0, https_1.onCall)({ region: REGION, timeoutSeconds: 300, memory: "1GiB" }, async (request) => {
    var _a;
    if (!request.auth) {
        throw new https_1.HttpsError("unauthenticated", "인증되지 않은 사용자입니다.");
    }
    const { gcsUri, transcript, recordedAt, mode } = ((_a = request.data) !== null && _a !== void 0 ? _a : {});
    if (!gcsUri && !transcript) {
        throw new https_1.HttpsError("invalid-argument", "gcsUri 또는 transcript 중 하나는 필요합니다.");
    }
    const anchor = recordedAt || "(통화 시각 미상)";
    const prompt = buildPrompt(anchor);
    let W = null;
    let C = null;
    try {
        // W경로(목표): 싼 STT 텍스트 → Gemini 텍스트.
        //  - transcript 직접 주면 그대로(레거시·측정).
        //  - mode="W" 인데 transcript 없으면 서버 STT(faster-whisper Cloud Run)로 gcsUri 전사.
        let wTranscript = transcript;
        if (mode === "W" && !wTranscript && gcsUri) {
            wTranscript = await (0, stt_1.transcribeAudio)(gcsUri);
        }
        if (wTranscript) {
            W = await runGemini([{ text: `${prompt}\n\n[통화 녹취 텍스트]\n${wTranscript}` }]);
            // 현장에서 STT 품질을 눈으로 확인할 수 있게 rawTranscript 채워 반환(GO바 근거).
            if ((W === null || W === void 0 ? void 0 : W.reservation) && typeof W.reservation === "object" && !W.reservation.rawTranscript) {
                W.reservation.rawTranscript = wTranscript;
            }
        }
        // C경로(천장): 오디오 → Gemini 멀티모달. mode="W"(파일럿)이면 C 안 함(비용·헛예약 안전).
        if (mode !== "W" && gcsUri) {
            C = await runGemini([
                { text: `${prompt}\n\n[통화 녹음 오디오를 직접 듣고 분석하라]` },
                { fileData: { fileUri: gcsUri, mimeType: "audio/mp4" } },
            ]);
        }
    }
    finally {
        // PII: 성공/실패 무관하게 업로드 오디오 즉시 삭제(측정용 임시 파일). 24h lifecycle 은 백업.
        if (gcsUri) {
            const parsed = parseGcsUri(gcsUri);
            if (parsed) {
                try {
                    await admin.storage().bucket(parsed.bucket).file(parsed.path).delete();
                }
                catch (e) {
                    logger.warn("[parseReservation] 임시 오디오 삭제 실패(무시)", { gcsUri, error: String(e) });
                }
            }
        }
    }
    return { success: true, W, C };
});
//# sourceMappingURL=reservation.js.map