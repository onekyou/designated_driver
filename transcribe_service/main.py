#!/usr/bin/env python3
"""
통화예약 엔진 — 서버 STT(귀). Cloud Run 서비스.

검증된 자산: 6/7 측정에서 통과한 faster-whisper(large-v3-turbo) 로직을 그대로 서버화.
  - .m4a 네이티브(PyAV 디코딩), 한국어 정확, language="ko"·vad_filter·beam_size=5.
  - 출처: functions/scripts/transcribe-whisper.py (로컬 측정 스크립트)와 동일 설정.

흐름: parseReservation(functions) → POST /transcribe {gcsUri} → GCS .m4a 다운로드 → 전사 → {transcript}.
뇌(이해=14필드 파싱)는 Gemini(functions)가 담당. 여기는 받아쓰기만 한다.

인증: Cloud Run IAM (functions 서비스계정만 invoker, --no-allow-unauthenticated).
"""

import os
import tempfile
import logging

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from google.cloud import storage

logging.basicConfig(level=logging.INFO)
log = logging.getLogger("transcribe")

# 폴백 사다리 — transcribe-whisper.py와 동일. 이미지 빌드 시 large-v3-turbo 미리 받아둠(런타임 throttle 회피).
FALLBACK_MODELS = ["large-v3-turbo", "medium", "small"]

app = FastAPI()
_model = None
_model_name = None
_storage = None


def _load_model():
    """모델 1회 로드(콜드스타트 시). transcribe-whisper.py load_model 과 동일 폴백."""
    global _model, _model_name
    if _model is not None:
        return
    from faster_whisper import WhisperModel
    forced = os.environ.get("WHISPER_MODEL")
    candidates = [forced] if forced else FALLBACK_MODELS
    last_err = None
    for name in candidates:
        try:
            log.info(f"[STT] 모델 로드 시도: {name}")
            _model = WhisperModel(name, device="cpu", compute_type="int8")
            _model_name = name
            log.info(f"[STT] 사용 모델 = {name}")
            return
        except Exception as e:  # 다운로드 throttle / 메모리 등
            last_err = e
            log.warning(f"[STT] {name} 로드 실패 → 다음 후보로: {e}")
    raise RuntimeError(f"모든 모델 로드 실패. 마지막 오류: {last_err}")


def _storage_client():
    global _storage
    if _storage is None:
        _storage = storage.Client()
    return _storage


def _parse_gcs_uri(uri: str):
    """gs://bucket/path → (bucket, path). 아니면 None."""
    if not uri or not uri.startswith("gs://"):
        return None
    rest = uri[len("gs://"):]
    if "/" not in rest:
        return None
    bucket, path = rest.split("/", 1)
    if not bucket or not path:
        return None
    return bucket, path


class TranscribeRequest(BaseModel):
    gcsUri: str


class TranscribeResponse(BaseModel):
    transcript: str
    model: str


@app.on_event("startup")
def _startup():
    # 콜드스타트 때 모델 미리 로드(첫 요청 지연 축소).
    _load_model()


@app.get("/")
def health():
    return {"ok": True, "model": _model_name}


@app.post("/transcribe", response_model=TranscribeResponse)
def transcribe(req: TranscribeRequest):
    parsed = _parse_gcs_uri(req.gcsUri)
    if not parsed:
        raise HTTPException(status_code=400, detail="gcsUri 형식 오류 (gs://bucket/path)")
    bucket_name, blob_path = parsed

    _load_model()

    # GCS → 임시 파일(.m4a). PyAV 가 m4a 네이티브 디코딩.
    suffix = os.path.splitext(blob_path)[1] or ".m4a"
    tmp_path = None
    try:
        with tempfile.NamedTemporaryFile(suffix=suffix, delete=False) as tmp:
            tmp_path = tmp.name
        try:
            blob = _storage_client().bucket(bucket_name).blob(blob_path)
            blob.download_to_filename(tmp_path)
        except Exception as e:
            log.error(f"[STT] GCS 다운로드 실패: {req.gcsUri} — {e}")
            raise HTTPException(status_code=502, detail=f"GCS 다운로드 실패: {e}")

        # transcribe-whisper.py transcribe_one 과 동일 설정.
        segments, info = _model.transcribe(tmp_path, language="ko", vad_filter=True, beam_size=5)
        text = " ".join(s.text.strip() for s in segments if s.text.strip()).strip()
        log.info(f"[STT] 전사 완료 ({_model_name}, p={info.language_probability:.2f}, len={len(text)})")
        return TranscribeResponse(transcript=text, model=_model_name or "unknown")
    finally:
        if tmp_path and os.path.exists(tmp_path):
            try:
                os.remove(tmp_path)
            except OSError:
                pass
