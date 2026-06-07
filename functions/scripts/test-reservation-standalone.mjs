// 이해 엔진 측정 — 배포·앱 없이 데모 녹음으로 W vs C 채점.
//   C경로(천장)  = 오디오 직접 → Gemini 멀티모달
//   W경로(목표)  = 싼 STT 전사 텍스트 → Gemini (transcript.txt 줄 때만)
// 한 콜을 두 경로로 같은 PROMPT·SCHEMA 로 채점해 이해도 격차 + 토큰 단가를 본다.
// 핵심 질문: STT 가 일부 틀려도 Gemini 가 맥락으로 복원하나(= 싼 길이 통하나).
//
// SDK = @google/genai (Vertex 백엔드). 사전(1회):
//   gcloud auth application-default login
//   gcloud services enable aiplatform.googleapis.com --project=calldetector-5d61e
// 실행(functions/ 에서):
//   node scripts/test-reservation-standalone.mjs "<녹음.m4a>" ["YYYY-MM-DDTHH:MM:SS"] ["<transcript.txt>"]
//     - 인자 3개(transcript) 있으면 W·C 둘 다, 없으면 C 만 (회귀 0).
//     - W_ideal 측정: transcript 자리에 "수기 완벽 전사.txt" 를 넣으면 됨(추가 코드 0).

import { GoogleGenAI, Type as T } from "@google/genai";
import { readFileSync } from "node:fs";

const PROJECT = process.env.GCLOUD_PROJECT || "calldetector-5d61e";
// 로컬 측정 한정 네트워크 튜닝: us-central1 교차리전이 느리면 VERTEX_LOCATION=global 등으로 실험.
// (production reservation.ts 는 GCP 내부망이라 무관 — 이 오버라이드는 standalone 전용)
const LOCATION = process.env.VERTEX_LOCATION || "us-central1";
const MODEL = "gemini-2.5-flash";

const audioPath = process.argv[2];
const recordedAt = process.argv[3] || "(통화 시각 미상)";
const transcriptPath = process.argv[4]; // 있으면 W경로
if (!audioPath) {
  console.error('사용법: node scripts/test-reservation-standalone.mjs "<녹음.m4a>" ["YYYY-MM-DDTHH:MM:SS"] ["<transcript.txt>"]');
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
    rawTranscript: { type: T.STRING }, // C경로: 모델 전사(STT 품질 비교용). W경로: 보통 비움.
  },
  required: ["isReservation", "intent", "moduleType", "confidence"],
};

// 중립 PROMPT(모달리티 무관) — 경로별 꼬리만 다름. reservation.ts buildPrompt 와 동형.
function buildPrompt() {
  return [
    "너는 한국 동네 가게(미용실·대리운전·택시·식당)로 걸려온 전화 통화를 분석한다.",
    "예약/변경/취소 의도와 핵심 정보를 구조화해 JSON 으로만 출력하라.",
    "규칙:",
    "1) 예약(변경·취소) 콜이 아니면(단순 문의·잘못 걸림·거래처·일상 대화) isReservation=false, intent='not_reservation'. 헛예약 금지.",
    "2) 업종 자동 판별(미용=salon_booking, 대리=designated_driver, 택시=taxi, 식당=restaurant).",
    `3) 상대 시각은 통화 시각(${recordedAt}) 기준 datetimeIso(ISO8601, KST)로 정규화. 불가하면 빈 문자열 + datetimeText 원문.`,
    "4) 미용=service·datetime 핵심 / 대리·택시=from·to·fare 핵심.",
    "5) 통화에 없거나 모르는 필드는 비워라 — 문자열은 빈 문자열(\"\"), 숫자(partySize·fare)는 명시됐을 때만 넣어라(추정·0 금지). 'null'·'없음' 같은 단어를 값으로 절대 쓰지 마라.",
    "6) confidence(0~1)는 근거가 부족하면(짧은 단편·콜 종류 불확정) 정직하게 낮춰라. 지어내지 마라.",
    "",
    "예시(대화 → 핵심 판정):",
    "- '내일 오후 3시에 커트랑 펌 돼요?' → isReservation=true, intent='booking.request', moduleType='salon_booking', service='커트, 펌', datetimeText='내일 오후 3시'",
    "- '어제 3시 예약한 거 4시로 바꿔주세요' → isReservation=true, intent='change', moduleType='salon_booking', datetimeText='4시'",
    "- '거기 OO상사죠? 세금계산서 발행 건으로 전화드렸어요' → isReservation=false, intent='not_reservation', moduleType='other' (거래처 — 헛예약 금지)",
    "- '지금 영업하세요?' → isReservation=false, intent='inquiry', moduleType='other' (단순 문의 — 예약 아님)",
  ].join("\n");
}

const ai = new GoogleGenAI({
  vertexai: true,
  project: PROJECT,
  location: LOCATION,
  httpOptions: { timeout: 600000 }, // 10분 — 느린 로컬 업링크 + 오디오 페이로드 대비
});

async function runGemini(parts) {
  const t0 = Date.now();
  const response = await ai.models.generateContent({
    model: MODEL,
    contents: [{ role: "user", parts }],
    config: {
      temperature: 0,
      responseMimeType: "application/json",
      responseSchema: SCHEMA,
      thinkingConfig: { thinkingBudget: 0 }, // 2.5-flash thinking 끔 — candidates 비는 현상 방지 + 측정 단가/일관성
    },
  });
  const latencyMs = Date.now() - t0;
  const finishReason = response.candidates?.[0]?.finishReason ?? "(없음)";
  const text = response.text ?? "";
  let reservation;
  try {
    reservation = JSON.parse(text);
  } catch {
    reservation = { _parseError: true, raw: text, finishReason };
  }
  const u = response.usageMetadata ?? {};
  const usage = {
    prompt: u.promptTokenCount ?? null,      // 입력 토큰 (오디오 vs 텍스트 = 단가 차이의 핵심)
    output: u.candidatesTokenCount ?? null,
    total: u.totalTokenCount ?? null,
  };
  return { reservation, latencyMs, usage, finishReason };
}

// 경로별 독립 실행 — 한 경로가 네트워크로 죽어도 다른 경로 결과는 건진다(측정 도구 견고성).
async function runSafe(parts, label) {
  try {
    return await runGemini(parts);
  } catch (e) {
    const code = e?.cause?.code || e?.message || String(e);
    console.error(`[${label} 오류] ${code}`);
    return { reservation: null, latencyMs: -1, usage: {}, finishReason: "ERROR", error: code };
  }
}

const prompt = buildPrompt();
const mime = audioPath.toLowerCase().endsWith(".amr") ? "audio/amr" : "audio/mp4";
const audioB64 = readFileSync(audioPath).toString("base64");

// W경로 먼저(텍스트=가벼움·안정), C경로 나중(오디오=무거움). 순차 — 동시 두 요청 네트워크 경쟁 제거.
let W = null;
let transcriptText = null;
if (transcriptPath) {
  transcriptText = readFileSync(transcriptPath, "utf-8").trim();
  W = await runSafe(
    [{ text: `${prompt}\n\n[아래는 이 통화의 STT 전사 텍스트다. 이것만 보고 분석하라.]\n${transcriptText}` }],
    "W",
  );
}
const C = await runSafe(
  [
    { text: `${prompt}\n\n[통화 녹음 오디오를 직접 듣고 분석하라. rawTranscript 에 들은 대화를 그대로 전사해 담아라(STT 품질 비교용).]` },
    { inlineData: { mimeType: mime, data: audioB64 } },
  ],
  "C",
);

// ── 출력 ──────────────────────────────────────────────
const FIELDS = ["isReservation", "intent", "moduleType", "service", "datetimeText",
  "datetimeIso", "partySize", "callerPhone", "from", "to", "fare", "notes", "confidence"];

function fmt(v) {
  if (v === undefined || v === null || v === "") return "·";
  return String(v);
}

console.log(`\n${"=".repeat(72)}`);
console.log(`파일: ${audioPath}`);
console.log(`통화 시각 앵커: ${recordedAt}   모델: ${MODEL}`);
console.log("=".repeat(72));

if (transcriptText !== null) {
  console.log(`\n[W 입력 — STT 전사 (${transcriptPath})]`);
  console.log(transcriptText);
}
if (C.reservation?.rawTranscript) {
  console.log(`\n[C 참고 — 모델이 들은 전사]`);
  console.log(C.reservation.rawTranscript);
}

console.log(`\n${"필드".padEnd(16)}${"W (STT텍스트)".padEnd(26)}C (오디오 천장)`);
console.log("-".repeat(72));
for (const f of FIELDS) {
  const w = W ? fmt(W.reservation?.[f]) : "—";
  const c = fmt(C.reservation?.[f]);
  console.log(`${f.padEnd(16)}${w.padEnd(26)}${c}`);
}

console.log("-".repeat(72));
const wTok = W ? `${fmt(W.usage.prompt)}→${fmt(W.usage.output)} (${fmt(W.usage.total)})` : "—";
const cTok = `${fmt(C.usage.prompt)}→${fmt(C.usage.output)} (${fmt(C.usage.total)})`;
console.log(`${"토큰 in→out".padEnd(16)}${wTok.padEnd(26)}${cTok}`);
const wMs = W ? `${W.latencyMs}ms` : "—";
console.log(`${"지연".padEnd(16)}${wMs.padEnd(26)}${C.latencyMs}ms`);
const wFr = W ? fmt(W.finishReason) : "—";
console.log(`${"finishReason".padEnd(16)}${wFr.padEnd(26)}${fmt(C.finishReason)}`);
console.log("=".repeat(72));
if (!W) console.log("(transcript 인자 없음 → C경로만 실행. W 측정하려면 3번째 인자로 .txt 전달)");
