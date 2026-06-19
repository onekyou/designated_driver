// 발음충실 전사 측정 — Gemini 2.5-flash 오디오 직접 → "들린 그대로" 받아쓰기.
//   목적: PTT 블랙박스용 전사에서 Gemini vs faster-whisper 충실도/과잉해석/비용 비교.
//   ⚠️ 예약 파싱 아님(억지 해석 금지) — 발음 그대로가 미덕(분쟁·기록용).
//
// SDK = @google/genai (Vertex). 사전: gcloud auth application-default login (ADC).
// 실행(functions/ 에서):
//   node scripts/transcribe-gemini.mjs "<폴더 또는 .m4a>"
//   → 각 파일 전사 출력 + 토큰(비용) + "<base>.gemini.txt" 저장(whisper .txt 와 나란히 비교).

import { GoogleGenAI } from "@google/genai";
import { readFileSync, writeFileSync, readdirSync, statSync } from "node:fs";
import { join, extname } from "node:path";

const PROJECT = process.env.GCLOUD_PROJECT || "calldetector-5d61e";
const LOCATION = process.env.VERTEX_LOCATION || "us-central1";
const MODEL = "gemini-2.5-flash";

const target = process.argv[2];
if (!target) {
  console.error('사용법: node scripts/transcribe-gemini.mjs "<폴더 또는 .m4a>"');
  process.exit(1);
}

// 발음충실 전사 프롬프트 — LLM 의 정규화/보정/환각 본능을 명시적으로 억제.
const PROMPT = [
  "아래 한국어 통화 녹음을 듣고, 들린 말을 그대로 받아써라(STT/받아쓰기).",
  "엄격 규칙:",
  "1) 발음 그대로. 표준어로 고치거나 문법을 다듬지 마라. 사투리·말끝·반복도 들린 대로.",
  "2) 지명·상호·인명을 '그럴듯한 것'으로 추측·보정하지 마라. 들린 음 그대로 적어라.",
  "3) 안 들리거나 불확실한 구간은 지어내지 말고 [불명] 으로 표기하라.",
  "4) 요약·해석·설명 금지. 오직 받아쓴 텍스트만 출력. 화자 구분 라벨도 붙이지 마라.",
  "5) 배경 잡음·신호음은 무시하고 사람 발화만 받아써라.",
].join("\n");

const ai = new GoogleGenAI({
  vertexai: true,
  project: PROJECT,
  location: LOCATION,
  httpOptions: { timeout: 600000 },
});

async function transcribeOne(audioPath) {
  const mime = audioPath.toLowerCase().endsWith(".amr") ? "audio/amr" : "audio/mp4";
  const audioB64 = readFileSync(audioPath).toString("base64");
  const t0 = Date.now();
  let text = "", usage = {}, finishReason = "(없음)", err = null;
  try {
    const res = await ai.models.generateContent({
      model: MODEL,
      contents: [{ role: "user", parts: [
        { text: PROMPT },
        { inlineData: { mimeType: mime, data: audioB64 } },
      ] }],
      config: {
        temperature: 0,
        responseMimeType: "text/plain",
        thinkingConfig: { thinkingBudget: 0 },
      },
    });
    text = (res.text ?? "").trim();
    finishReason = res.candidates?.[0]?.finishReason ?? "(없음)";
    const u = res.usageMetadata ?? {};
    usage = { prompt: u.promptTokenCount ?? null, output: u.candidatesTokenCount ?? null, total: u.totalTokenCount ?? null };
  } catch (e) {
    err = e?.cause?.code || e?.message || String(e);
  }
  return { latencyMs: Date.now() - t0, text, usage, finishReason, err };
}

// 대상 수집
let files = [];
if (statSync(target).isDirectory()) {
  files = readdirSync(target).filter(f => extname(f).toLowerCase() === ".m4a").sort().map(f => join(target, f));
} else {
  files = [target];
}

console.log(`\n[Gemini 발음충실 전사] 모델=${MODEL}  대상=${files.length}건\n`);

// Gemini 2.5-flash 단가(USD/1M tokens) — 측정 단가 환산용(대략, audio input 포함 추정).
const PRICE_IN = 0.30, PRICE_OUT = 2.50; // flash 기준 근사. 실청구는 콘솔 확인.
let totIn = 0, totOut = 0;

for (const f of files) {
  const r = await transcribeOne(f);
  console.log("=".repeat(72));
  console.log(`파일: ${f.split(/[\\/]/).pop()}`);
  if (r.err) { console.log(`[오류] ${r.err}`); continue; }
  console.log(`[전사]\n${r.text}`);
  const inT = r.usage.prompt ?? 0, outT = r.usage.output ?? 0;
  totIn += inT; totOut += outT;
  const costUsd = (inT / 1e6) * PRICE_IN + (outT / 1e6) * PRICE_OUT;
  console.log(`\n토큰 in→out: ${inT}→${outT}  지연: ${r.latencyMs}ms  추정단가: $${costUsd.toFixed(5)} (~${(costUsd * 1380).toFixed(2)}원)  finish=${r.finishReason}`);
  // whisper .txt 와 나란히 저장
  const base = f.replace(/\.m4a$/i, "");
  writeFileSync(`${base}.gemini.txt`, r.text + "\n", "utf-8");
}

console.log("=".repeat(72));
const totCost = (totIn / 1e6) * PRICE_IN + (totOut / 1e6) * PRICE_OUT;
console.log(`합계 토큰 in→out: ${totIn}→${totOut}  총추정단가: $${totCost.toFixed(5)} (~${(totCost * 1380).toFixed(2)}원)  건당 ~${(totCost * 1380 / files.length).toFixed(2)}원`);
