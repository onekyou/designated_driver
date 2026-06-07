# 서버 STT(귀) — faster-whisper Cloud Run 서비스.
FROM python:3.11-slim

# ffmpeg: 오디오 디코딩 안전망(PyAV 가 자체 ffmpeg 번들하지만 m4a 견고성 위해 명시).
RUN apt-get update && apt-get install -y --no-install-recommends ffmpeg \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# ★ 모델 이미지에 미리 번들 — 런타임 HF 다운로드 throttle(6/4 행) 회피, 콜드스타트 단축.
ENV HF_HOME=/app/.cache/huggingface
ENV WHISPER_MODEL=large-v3-turbo
RUN python -c "from faster_whisper import WhisperModel; WhisperModel('large-v3-turbo', device='cpu', compute_type='int8')"

COPY main.py .

# Cloud Run 은 $PORT 주입(기본 8080).
ENV PORT=8080
CMD exec uvicorn main:app --host 0.0.0.0 --port ${PORT}
