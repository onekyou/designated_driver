// 이해 엔진 최소 검증 — 배포·앱 없이 데모 녹음 1개로 "Gemini가 통화를 예약으로 이해하는가"만 확인.
// C경로(천장) = 오디오 직접 → Gemini. STT 없이 모델이 한국어 통화를 구조화하는지 본다.
//
// SDK = @google/genai (Vertex 백엔드). @google-cloud/vertexai 는 deprecated(2026 제거).
// 사전(1회):
//   gcloud auth application-default login
//   gcloud services enable aiplatform.googleapis.com --project=calldetector-5d61e
// 실행(functions/ 에서):
//   node scripts/test-reservation-standalone.mjs "<녹음.m4a>" ["2026-06-04T15:30:12"]

import { GoogleGenAI, Type as T } from "@google/genai";
import { readFileSync } from "node:fs";

const PROJECT = process.env.GCLOUD_PROJECT || "calldetector-5d61e";
const LOCATION = "us-central1";
const MODEL = "gemini-2.5-flash";

const audioPath = process.argv[2];
const recordedAt = process.argv[3] || "(통화 시각 미상)";
if (!audioPath) {
  console.error('사용법: node scripts/test-reservation-standalone.mjs "<녹음.m4a>" ["YYYY-MM-DDTHH:MM:SS"]');
  process.exit(1);
}

const SCHEMA = {
  type: T.OBJECT,
  properties: {
    isReservation: { type: T.BOOLEAN },
    intent: { type: T.STRING, enum: ["booking.request", "change", "cancel", "inquiry", "not_reservation", "other"] },
    moduleType: { type: T.STRING, enum: ["salon_booking", "designated_driver", "taxi", "restaurant", "other"] },
    service: { type: T.STRING },
    datetimeText: { type: T.STRING },
    datetimeIso: { type: T.STRING },
    partySize: { type: T.INTEGER },
    callerPhone: { type: T.STRING },
    from: { type: T.STRING },
    to: { type: T.STRING },
    fare: { type: T.INTEGER },
    notes: { type: T.STRING },
    confidence: { type: T.NUMBER },
    rawTranscript: { type: T.STRING },
  },
  required: ["isReservation", "intent", "moduleType", "confidence"],
};

const PROMPT = [
  "너는 한국 동네 가게(미용실·대리운전·택시·식당)로 걸려온 전화 통화의 녹음을 직접 듣고 분석한다.",
  "예약/변경/취소 의도와 핵심 정보를 구조화해 JSON 으로만 출력하라.",
  "규칙:",
  "1) 예약(변경·취소) 콜이 아니면(단순 문의·잘못 걸림·거래처·일상 대화) isReservation=false, intent='not_reservation'. 헛예약 금지.",
  "2) 업종 자동 판별(미용=salon_booking, 대리=designated_driver, 택시=taxi, 식당=restaurant).",
  `3) 상대 시각은 통화 시각(${recordedAt}) 기준 datetimeIso(ISO8601, KST)로 정규화. 불가하면 빈 문자열 + datetimeText 원문.`,
  "4) 미용=service·datetime 핵심 / 대리·택시=from·to·fare 핵심.",
  "5) 통화에 없거나 모르는 필드는 비워라 — 문자열은 빈 문자열(\"\"), 숫자(partySize·fare)는 명시됐을 때만 넣어라(추정·0 금지). 'null'·'없음' 같은 단어를 값으로 절대 쓰지 마라.",
  "6) confidence(0~1)는 근거가 부족하면(짧은 단편·콜 종류 불확정) 정직하게 낮춰라. 지어내지 마라.",
  "7) rawTranscript 에 들은 대화를 그대로 전사해 담아라(STT 품질 비교용).",
  "",
  "예시(대화 → 핵심 판정):",
  "- '내일 오후 3시에 커트랑 펌 돼요?' → isReservation=true, intent='booking.request', moduleType='salon_booking', service='커트, 펌', datetimeText='내일 오후 3시'",
  "- '어제 3시 예약한 거 4시로 바꿔주세요' → isReservation=true, intent='change', moduleType='salon_booking', datetimeText='4시'",
  "- '거기 OO상사죠? 세금계산서 발행 건으로 전화드렸어요' → isReservation=false, intent='not_reservation', moduleType='other' (거래처 — 헛예약 금지)",
  "- '지금 영업하세요?' → isReservation=false, intent='inquiry', moduleType='other' (단순 문의 — 예약 아님)",
].join("\n");

const mime = audioPath.toLowerCase().endsWith(".amr") ? "audio/amr" : "audio/mp4";
const data = readFileSync(audioPath).toString("base64");

const ai = new GoogleGenAI({ vertexai: true, project: PROJECT, location: LOCATION });

const t0 = Date.now();
const response = await ai.models.generateContent({
  model: MODEL,
  contents: [{ role: "user", parts: [{ text: PROMPT }, { inlineData: { mimeType: mime, data } }] }],
  config: { temperature: 0, responseMimeType: "application/json", responseSchema: SCHEMA },
});
const ms = Date.now() - t0;

const text = response.text ?? "(빈 응답)";
console.log(`\n=== ${audioPath}  (${ms}ms, ${MODEL}) ===`);
try {
  console.log(JSON.stringify(JSON.parse(text), null, 2));
} catch {
  console.log(text);
}
