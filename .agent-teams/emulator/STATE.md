# Firebase Emulator 테스트 상태

- status: completed
- step: 10/10
- last_action: run-all.ts 전체 실행 PASS (8/8, 118.1s)

## 구현 진행

| 단계 | 작업 | 상태 |
|------|------|------|
| 0 | 컨텍스트 폴더 (PLAYBOOK/STATE/CONTEXT) | done |
| 1 | firebase.json + package.json 수정 | done |
| 2 | constants.ts + emulator-config.ts + tsconfig.json | done |
| 3 | seed-data.ts | done |
| 4 | helpers (firestore/wait/assertions) | done |
| 5 | 에뮬레이터 기동 + seed 검증 | done |
| 6 | 시나리오 1 (정상 플로우) | done |
| 7 | 시나리오 2~3 (거절/취소) | done |
| 8 | 시나리오 4~5 (공유콜) | done |
| 9 | 시나리오 6~8 (대량/정산/포인트) | done |
| 10 | run-all.ts + 전체 실행 | done |

## 전체 실행 결과 (run-all.ts)

| # | 시나리오 | 결과 | 시간 | 핵심 검증 |
|---|---------|------|------|----------|
| 1 | 정상 플로우 | PASS | 1.3s | CF 트리거 체인 + 정산 세션 자동 생성 |
| 2 | 거절→재배차 | PASS | 1.8s | 기사 복구 + 정산에 재배차 기사로 기록 |
| 3 | 취소 3종 | PASS | 10.2s | 관리자/기사/고객 취소 전부 정상 |
| 4 | 공유콜 | PASS | 1.8s | CF 체인 + 콜복사 + 포인트 ±10% (13000/7000) |
| 5 | 공유콜 경합 | PASS | 3.3s | 트랜잭션 1성공 1실패 + 승자에만 콜복사 |
| 6 | 대량 50건 | PASS | 55.0s | 10사무실×5건 정합성 (50건, 1,000,000원) |
| 7 | 정산 마감 | PASS | 3.9s | totals 정합성 + depositRatio 60% + 마감 후 추가 차단 |
| 8 | 고객 포인트 | PASS | 7.6s | BRONZE 3% 적립 (600P) + 멱등성 키 확인 |

**총 8건: PASS 8 / FAIL 0 / 소요 118.1s**

## 실행 방법

```bash
# 에뮬레이터 실행 (터미널 1)
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
export PATH="$JAVA_HOME/bin:$PATH"
firebase emulators:start --only functions,firestore

# 전체 테스트 (터미널 2)
cd functions
export FIRESTORE_EMULATOR_HOST="127.0.0.1:8080"
npx ts-node test/run-all.ts
```

## 발견 이슈

| 이슈 | 내용 | 심각도 | 조치 |
|------|------|--------|------|
| CF 큐 지연 | 시드 50명 트리거 영향, 시나리오 6에서 최대 53초 대기 | 낮음 | 타임아웃 충분히 확보 |
| 기사취소복구 | cancelCallByDriver 시 CF에서 기사 상태 미복구 | 정보 | 앱에서 직접 처리하는 구조 (정상) |
