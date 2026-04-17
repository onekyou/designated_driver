#!/bin/bash
# git-check.sh - 로컬과 원격 Git 상태 비교 스크립트
# 로컬 하드 쓰기 불안정 (캐시 증발) 대응용
# 사용: ./git-check.sh 또는 bash git-check.sh

echo "========================================"
echo "  Git 동기화 상태 확인"
echo "========================================"
echo ""

cd "$(dirname "$0")" || exit 1

# fetch로 원격 최신 상태 가져오기
git fetch origin 2>/dev/null

BRANCH=$(git branch --show-current)
REMOTE="origin/$BRANCH"

echo "현재 브랜치: $BRANCH"
echo ""

# 1. 푸시 안 된 커밋 확인
echo "--- [1] 푸시 안 된 커밋 ---"
UNPUSHED=$(git log "$REMOTE"..HEAD --oneline 2>/dev/null)
if [ -z "$UNPUSHED" ]; then
    echo "[OK] 없음 (원격과 동기화됨)"
else
    echo "[!!] 아래 커밋이 푸시되지 않음:"
    echo "$UNPUSHED"
fi
echo ""

# 2. 커밋 안 된 변경사항 확인 (실제 파일 상태)
echo "--- [2] 커밋 안 된 변경사항 ---"
CHANGES=$(git status --short)

if [ -z "$CHANGES" ]; then
    echo "[OK] 없음"
else
    echo "[!!] 변경된 파일:"
    echo "$CHANGES"
fi
echo ""

# 3. 로컬 vs 원격 실제 파일 차이 (핵심!)
echo "--- [3] 로컬 vs 원격 파일 차이 (실제 비교) ---"
DIFF=$(git diff "$REMOTE" --stat 2>/dev/null)
if [ -z "$DIFF" ]; then
    echo "[OK] 로컬과 원격 파일 내용 동일"
else
    echo "[!!] 차이 있음:"
    echo "$DIFF"
fi
echo ""

# 4. 메모리 저장소 분기 탐지 (auto-memory B 폴더에 MEMORY.md 외 파일 존재 여부)
echo "--- [4] 메모리 저장소 분기 탐지 ---"
AUTO_MEMORY="/c/Users/kala1/.claude/projects/C--Users-kala1-designated-driver/memory"
if [ -d "$AUTO_MEMORY" ]; then
    STRAY=$(find "$AUTO_MEMORY" -mindepth 1 ! -name "MEMORY.md" 2>/dev/null)
    if [ -z "$STRAY" ]; then
        echo "[OK] auto-memory 폴더에 MEMORY.md만 존재 (정상)"
    else
        echo "[!!] auto-memory 폴더에 분기 파일 발견:"
        echo "$STRAY"
        echo ""
        echo ">>> 해당 파일을 memory/ (프로젝트)로 이관 후 auto-memory에서 삭제하세요"
        echo ">>> 정책: CLAUDE.md §메모리 저장 정책"
    fi
else
    echo "[-] auto-memory 폴더 없음 (신규 환경이거나 Claude Code 미설치)"
fi
echo ""

# 5. 요약
echo "========================================"
echo "  요약"
echo "========================================"

UNPUSHED_COUNT=0
if [ -n "$UNPUSHED" ]; then
    UNPUSHED_COUNT=$(echo "$UNPUSHED" | wc -l)
fi

CHANGES_COUNT=0
if [ -n "$CHANGES" ]; then
    CHANGES_COUNT=$(echo "$CHANGES" | wc -l)
fi

if [ "$UNPUSHED_COUNT" -eq 0 ] && [ -z "$DIFF" ]; then
    echo ""
    echo "[SAFE] 모든 작업이 원격에 백업됨"
    echo "       절전/셧다운 해도 안전합니다"
else
    echo ""
    echo "[WARNING] 백업 안 된 작업 있음!"
    echo "  - 푸시 안 된 커밋: $UNPUSHED_COUNT 개"
    echo "  - 커밋 안 된 변경: $CHANGES_COUNT 개"
    echo ""
    echo ">>> 절전/셧다운 전에 반드시 커밋 & 푸시하세요!"
fi
echo ""
