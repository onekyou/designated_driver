# Firebase Emulator Suite 통합 테스트

## 목적

로컬 Firebase Emulator에서 Firestore + Cloud Functions를 실제 실행하여 검증.
코드 경로 추적이 아닌 **실제 트랜잭션 + CF 트리거** 동작 확인.

## 명령어

| 말하면 | 실행 |
|--------|------|
| "에뮬레이터 테스트해줘" | STATE.md 확인 → 미완료 단계부터 즉시 실행 |
| "시나리오 N 돌려줘" | 특정 시나리오만 실행 |
| "전체 돌려줘" | `npm run test:emulator` 실행 |

## 구조

```
functions/test/              — 테스트 코드
  setup/                     — 에뮬레이터 설정 + 시드 데이터
  helpers/                   — 유틸리티 (문서 CRUD, 대기, 검증)
  scenarios/                 — 8개 시나리오 (01~08)
  run-all.ts                 — 전체 순차 실행

.agent-teams/emulator/       — 컨텍스트 (이 폴더)
  PLAYBOOK.md                — 진입점 (이 파일)
  STATE.md                   — 현재 진행 상태
  CONTEXT.md                 — CF 트리거 맵 + Firestore 구조
  results/                   — 실행 결과 기록
```

## 실행 방법

### 자동 (전체)
```bash
cd functions && npm run test:emulator
```

### 수동 (개별 시나리오)
```bash
# 터미널 1
firebase emulators:start --only functions,firestore

# 터미널 2
export FIRESTORE_EMULATOR_HOST="127.0.0.1:8080"
npx ts-node test/setup/seed-data.ts
npx ts-node test/scenarios/01-normal-flow.test.ts
```

### 에뮬레이터 UI
http://127.0.0.1:4000

## 8개 시나리오

| # | 파일 | 내용 | 핵심 검증 |
|---|------|------|----------|
| 1 | 01-normal-flow | 정상 플로우 | CF 트리거 체인 + 정산 세션 |
| 2 | 02-reject-reassign | 거절 → 재배차 | 기사 상태 복구 |
| 3 | 03-cancel-types | 취소 3종 | 포인트 환불 + 멱등성 |
| 4 | 04-shared-call | 공유콜 | CF 체인 + 포인트 ±10% |
| 5 | 05-shared-contention | 공유콜 경합 | 트랜잭션 동시성 |
| 6 | 06-bulk-load | 대량 500건 | 성능 + 정합성 |
| 7 | 07-settlement | 정산 마감 | 자동마감 + 불일치 |
| 8 | 08-customer-points | 고객 포인트 | 등급 + 멱등성 |

## 주의사항

- FCM: 에뮬레이터 미지원, notifications 문서로 간접 검증
- Windows: bash shell에서 실행
- 기존 코드(functions/src/) 수정 없음

## 참조

- `CONTEXT.md` — CF 트리거 맵 + Firestore 데이터 구조
- `functions/src/index.ts` — 42개 CF 정의
- `functions/src/handlers/settlement.ts` — 정산 로직
- `functions/src/handlers/points.ts` — 포인트 로직
