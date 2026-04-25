#!/bin/bash
# Kiwi 형태소 사전 다운로드 — Phase B 1단계 재현용
# 출처: github.com/bab2min/Kiwi (LGPL v3)
# 데이터만 흡수, 코드 의존 없음

set -e
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
CACHE_DIR="$SCRIPT_DIR/.kiwi-cache"
mkdir -p "$CACHE_DIR"

# Kiwi morphemes.txt — 표준 한국어 형태소 + POS + 빈도
MORPHEMES_URL="https://raw.githubusercontent.com/bab2min/Kiwi/main/ModelGenerator/morphemes.txt"
MORPHEMES_OUT="$CACHE_DIR/morphemes.txt"

echo "Downloading Kiwi morphemes.txt..."
curl -sL "$MORPHEMES_URL" -o "$MORPHEMES_OUT"

LINES=$(wc -l < "$MORPHEMES_OUT")
SIZE=$(du -h "$MORPHEMES_OUT" | cut -f1)
echo "  → $MORPHEMES_OUT ($LINES lines, $SIZE)"

# Kiwi commit hash 기록 (재현성용)
COMMIT_HASH=$(curl -sL "https://api.github.com/repos/bab2min/Kiwi/commits/main" | grep -m1 '"sha"' | cut -d'"' -f4 | head -c 12)
echo "$COMMIT_HASH" > "$CACHE_DIR/commit.txt"
echo "  Kiwi commit: $COMMIT_HASH"

echo "Done. Run extract-kiwi-markers.js next."
