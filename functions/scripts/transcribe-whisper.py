#!/usr/bin/env python3
# 싼 STT(귀) — 통화 녹음(.m4a) → 한국어 전사 텍스트.
# W경로 측정용 transcript 생성. 뇌(이해)는 Gemini, 여기는 받아쓰기만 한다.
#
# 설계 근거: memory/designated_drive/reservation_engine_plan_2026-06-07.md
#  - 무료·로컬(PII 안전, 외부 미전송) Whisper = 우리가 가려는 "싼 길"과 같은 계열.
#  - large-v3-turbo(한국어 large-v3 동급 정확도·2~5배 빠름) 우선,
#    HF 다운로드 throttle(6/4 medium/large 행) 시 medium→small 폴백.
#
# 사전(1회): pip install faster-whisper
# 실행(functions/ 에서):
#   python scripts/transcribe-whisper.py "<녹음.m4a>" [--model large-v3-turbo]
# 출력: stdout(전사) + 같은 폴더 "<녹음>.txt" (standalone 이 읽음)

import sys
import os
import glob
import argparse

# 폴백 사다리 — 앞에서부터 시도, 다운로드/로드 실패 시 다음으로.
FALLBACK_MODELS = ["large-v3-turbo", "medium", "small"]


def load_model(forced):
    from faster_whisper import WhisperModel
    candidates = [forced] if forced else FALLBACK_MODELS
    last_err = None
    for name in candidates:
        try:
            print(f"[STT] 모델 로드 시도: {name} ...", file=sys.stderr)
            # CPU 기본(int8) — GPU 있으면 device='cuda', compute_type='float16' 로 바꿔도 됨.
            m = WhisperModel(name, device="cpu", compute_type="int8")
            print(f"[STT] 사용 모델 = {name}", file=sys.stderr)
            return m, name
        except Exception as e:  # 다운로드 throttle / 메모리 등
            last_err = e
            print(f"[STT] {name} 로드 실패 → 다음 후보로: {e}", file=sys.stderr)
    raise RuntimeError(f"모든 모델 로드 실패. 마지막 오류: {last_err}")


def transcribe_one(model, used, audio):
    # language='ko' 고정(자동감지 오판 방지), vad_filter 로 무음 구간 제거(hallucination 완화).
    segments, info = model.transcribe(audio, language="ko", vad_filter=True, beam_size=5)
    text = " ".join(s.text.strip() for s in segments if s.text.strip()).strip()
    base, _ = os.path.splitext(audio)
    out_txt = base + ".txt"
    with open(out_txt, "w", encoding="utf-8") as f:
        f.write(text + "\n")
    print(f"\n=== {os.path.basename(audio)}  ({used}, p={info.language_probability:.2f}) ===",
          file=sys.stderr)
    print(text)
    print(f"[STT] 저장 → {out_txt}", file=sys.stderr)
    return text


def main():
    ap = argparse.ArgumentParser(description="통화 녹음 → 한국어 전사 (faster-whisper)")
    ap.add_argument("path", help="녹음 파일(.m4a) 또는 폴더(폴더면 안의 *.m4a 전부, 모델 1회 로드)")
    ap.add_argument("--model", default=None,
                    help="모델 강제 지정 (기본: large-v3-turbo→medium→small 폴백)")
    args = ap.parse_args()

    p = args.path
    if os.path.isdir(p):
        targets = sorted(glob.glob(os.path.join(p, "*.m4a")))
        if not targets:
            print(f"[오류] 폴더에 .m4a 없음: {p}", file=sys.stderr)
            sys.exit(1)
    elif os.path.isfile(p):
        targets = [p]
    else:
        print(f"[오류] 경로 없음: {p}", file=sys.stderr)
        sys.exit(1)

    try:
        model, used = load_model(args.model)
    except ImportError:
        print("[오류] faster-whisper 미설치 — `pip install faster-whisper` 후 다시 실행하세요.",
              file=sys.stderr)
        sys.exit(2)
    except RuntimeError as e:
        print(f"[오류] {e}", file=sys.stderr)
        sys.exit(3)

    print(f"[STT] 대상 {len(targets)}개", file=sys.stderr)
    for t in targets:
        transcribe_one(model, used, t)
    print(f"\n[STT] 완료 — {len(targets)}개 전사", file=sys.stderr)


if __name__ == "__main__":
    main()
