// 측정 배치 — 폴더의 통화 녹음 전부를 W경로(STT 텍스트→Gemini)로 채점해 한 표로.
// 전제: 각 <녹음>.m4a 옆에 transcribe-whisper.py 가 만든 <녹음>.txt 존재.
// recordedAt 은 삼성 파일명(..._YYMMDD_HHMMSS.m4a)에서 자동 파싱(상대시각 정규화 앵커).
// C경로(오디오 천장)는 기본 OFF(로컬 네트워크 헤더타임아웃 회피) — 필요시 --audio.
//
// 실행(functions/, sandbox 꺼서):
//   node scripts/score-batch.mjs "<녹음폴더>" [--audio]

import { GoogleGenAI, Type as T } from "@google/genai";
import { readFileSync, existsSync, readdirSync } from "node:fs";
import { join } from "node:path";

const PROJECT = process.env.GCLOUD_PROJECT || "calldetector-5d61e";
const LOCATION = process.env.VERTEX_LOCATION || "global";
const MODEL = "gemini-2.5-flash";

const dir = process.argv[2];
const withAudio = process.argv.includes("--audio");
if (!dir) {
  console.error('사용법: node scripts/score-batch.mjs "<녹음폴더>" [--audio]');
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

function buildPrompt(recordedAt) {
  return [
    "너는 한국 동네 가게(미용실·대리운전·택시·식당)로 걸려온 전화 통화를 분석한다.",
    "예약/변경/취소 의도와 핵심 정보를 구조화해 JSON 으로만 출력하라.",
    "규칙:",
    "1) 예약(변경·취소) 콜이 아니면(단순 문의·잘못 걸림·거래처·일상 대화) isReservation=false, intent='not_reservation'. 헛예약 금지.",
    "2) 업종 자동 판별(미용=salon_booking, 대리=designated_driver, 택시=taxi, 식당=restaurant).",
    `3) 상대 시각은 통화 시각(${recordedAt}) 기준 datetimeIso(ISO8601, KST)로 정규화. 불가하면 빈 문자열 + datetimeText 원문.`,
    "4) 미용=service·datetime 핵심 / 대리·택시=from·to·fare 핵심.",
    "5) 통화에 없거나 모르는 필드는 비워라 — 문자열은 빈 문자열(\"\"), 숫자(partySize·fare)는 명시됐을 때만(추정·0 금지). 'null'·'없음' 금지.",
    "6) confidence(0~1)는 근거 부족(짧은 단편·콜 종류 불확정)이면 정직하게 낮춰라.",
    "7) 손님 전화번호가 대화에 나오면 callerPhone 에 담는다.",
  ].join("\n");
}

const ai = new GoogleGenAI({
  vertexai: true, project: PROJECT, location: LOCATION,
  httpOptions: { timeout: 600000 },
});

async function runGemini(parts) {
  const t0 = Date.now();
  const res = await ai.models.generateContent({
    model: MODEL,
    contents: [{ role: "user", parts }],
    config: {
      temperature: 0, responseMimeType: "application/json", responseSchema: SCHEMA,
      thinkingConfig: { thinkingBudget: 0 },
    },
  });
  const latencyMs = Date.now() - t0;
  const u = res.usageMetadata ?? {};
  let r;
  try { r = JSON.parse(res.text ?? ""); } catch { r = { _parseError: true }; }
  return { r, latencyMs, inTok: u.promptTokenCount ?? null, outTok: u.candidatesTokenCount ?? null };
}

// 삼성 파일명 ..._YYMMDD_HHMMSS.m4a → ISO8601(KST 가정)
function parseRecordedAt(name) {
  const m = /_(\d{2})(\d{2})(\d{2})_(\d{2})(\d{2})(\d{2})\.m4a$/i.exec(name);
  if (!m) return "(시각 미상)";
  const [, yy, mm, dd, hh, mi, ss] = m;
  return `20${yy}-${mm}-${dd}T${hh}:${mi}:${ss}`;
}

const files = readdirSync(dir).filter((f) => f.toLowerCase().endsWith(".m4a")).sort();
if (!files.length) { console.error(`[오류] .m4a 없음: ${dir}`); process.exit(1); }

const F = (v) => (v === undefined || v === null || v === "" ? "·" : String(v));
const results = [];

for (const f of files) {
  const base = f.replace(/\.m4a$/i, "");
  const txtPath = join(dir, base + ".txt");
  const recordedAt = parseRecordedAt(f);
  if (!existsSync(txtPath)) {
    console.log(`\n[건너뜀] ${f} — .txt 없음(전사 먼저)`);
    continue;
  }
  const transcript = readFileSync(txtPath, "utf-8").trim();
  const prompt = buildPrompt(recordedAt);

  let W = null, C = null;
  try {
    W = await runGemini([{ text: `${prompt}\n\n[아래는 이 통화의 STT 전사 텍스트다. 이것만 보고 분석하라.]\n${transcript}` }]);
  } catch (e) { console.error(`[W 오류] ${f}: ${e?.cause?.code || e}`); }

  if (withAudio) {
    try {
      const b64 = readFileSync(join(dir, f)).toString("base64");
      C = await runGemini([
        { text: `${prompt}\n\n[통화 녹음 오디오를 직접 듣고 분석하라.]` },
        { inlineData: { mimeType: "audio/mp4", data: b64 } },
      ]);
    } catch (e) { console.error(`[C 오류] ${f}: ${e?.cause?.code || e}`); }
  }

  results.push({ f, recordedAt, transcript, W, C });

  console.log(`\n${"=".repeat(72)}`);
  console.log(`${f}   (앵커 ${recordedAt})`);
  console.log(`[전사] ${transcript}`);
  if (W) {
    const r = W.r;
    console.log(`[W] 예약=${F(r.isReservation)} intent=${F(r.intent)} module=${F(r.moduleType)} conf=${F(r.confidence)}`);
    console.log(`    service=${F(r.service)} | datetime=${F(r.datetimeText)} → ${F(r.datetimeIso)} | party=${F(r.partySize)} phone=${F(r.callerPhone)}`);
    console.log(`    from=${F(r.from)} to=${F(r.to)} fare=${F(r.fare)} notes=${F(r.notes)}`);
    console.log(`    토큰 ${F(W.inTok)}→${F(W.outTok)}  ${W.latencyMs}ms`);
  }
  if (C) {
    const r = C.r;
    console.log(`[C] 예약=${F(r.isReservation)} intent=${F(r.intent)} module=${F(r.moduleType)} conf=${F(r.confidence)} service=${F(r.service)} dt=${F(r.datetimeText)}`);
  }
}

// 요약
console.log(`\n${"#".repeat(72)}`);
console.log(`총 ${results.length}건 채점 완료.`);
const totalIn = results.reduce((s, x) => s + (x.W?.inTok || 0), 0);
const totalOut = results.reduce((s, x) => s + (x.W?.outTok || 0), 0);
console.log(`W경로 토큰 합계: in ${totalIn} / out ${totalOut}`);
