# 다음 세션 TODO 목록

## 날짜: 2026-02-03
## 상태: 미해결 문제 있음

---

## 미해결 문제 3가지

### 문제 1: 마감 시 기사 알림 안됨
- **현상**: 콜매니저에서 업무 마감 버튼 → 기사앱에 알림 안 옴
- **원인**: 오늘 첫 마감이 구 코드로 실행됨 → "이미 마감됨" 상태 → 이후 모든 마감 시도가 스킵됨
- **해결 방향**: "이미 마감됨"이어도 알림 재전송 옵션 추가 또는 시간 기반 자동 마감 검토

### 문제 2: 기사앱 총정산내역에 모든 과거 운행 표시
- **현상**: 당일 운행만 나와야 하는데 과거 모든 운행이 표시됨
- **원인**: `settlementLastCleared`가 Firestore에 저장 안 됨 → `lastClearedMillis = 0L` → 모든 기록 통과
- **해결 방향**:
  1. Cloud Function에서 "이미 마감됨"이어도 `settlementLastCleared` 누락 시 저장 (코드 수정 시작함)
  2. 또는 Firestore에서 수동으로 `settlementLastCleared` 설정

### 문제 3: 누적 미수령금 실시간 반영 안됨
- **현상**: 외상 운행 완료 시 즉시 반영 안 됨
- **원인**: 문제 2와 연결 - `lastClearedMillis`가 0이면 계산이 정상 동작 안 함
- **해결 방향**: 문제 2 해결 시 함께 해결될 가능성 높음

---

## 핵심 질문: 마감이 버튼인가 시간인가?

### 현재 상태
- **버튼 방식**: 콜매니저에서 "업무 마감" 버튼 클릭 시 실행
- `finalizeSettlementAndNotifyDrivers` Cloud Function 호출

### 사용자 질문 의도
- 시간 기반 자동 마감이 가능한지?
- 예: 매일 새벽 5시에 자동 마감

### 확인 필요
- `autoFinalizeSettlements` 스케줄 함수가 존재함 (확인 필요)
- 현재 활성화 상태인지, 어떤 조건으로 동작하는지 확인

---

## 수정 진행 중인 코드

### functions/src/index.ts (미배포)
```typescript
// "이미 마감됨"이어도 settlementLastCleared 누락 시 설정
if (session?.metadata?.isFinalized) {
  const officeRef = db.collection("provinces").doc(provinceId)
    .collection("cities").doc(cityId)
    .collection("offices").doc(officeId);
  const officeDoc = await officeRef.get();
  if (!officeDoc.data()?.settlementLastCleared) {
    await officeRef.update({
      settlementLastCleared: admin.firestore.Timestamp.now()
    });
    logger.info(`[finalizeSettlement] settlementLastCleared 보완 설정됨`);
  }
  return { success: true, alreadyFinalized: true, sent: 0, skipped: 0 };
}
```

**상태**: 코드 수정됨, 빌드/배포 필요

---

## 다음 세션 작업 순서

1. **마감 방식 확인**
   - 시간 기반 자동 마감 원하는지 확인
   - `autoFinalizeSettlements` 함수 분석

2. **Cloud Function 배포**
   ```bash
   cd D:/designated_driver/functions
   ./node_modules/.bin/tsc
   firebase deploy --only functions:finalizeSettlementAndNotifyDrivers
   ```

3. **테스트**
   - 콜매니저에서 업무 마감 버튼 클릭
   - `settlementLastCleared` 저장 확인
   - 기사앱 재로그인 → 당일 운행만 표시되는지 확인

4. **알림 문제 해결**
   - "이미 마감됨"이어도 알림 재전송 옵션 추가 검토
   - 또는 별도의 "알림 재전송" 버튼 추가

---

## 관련 파일

| 파일 | 역할 | 상태 |
|------|------|------|
| `functions/src/index.ts` | Cloud Function | 수정됨, 미배포 |
| `functions/src/handlers/settlement.ts` | 알림 전송 로직 | 확인 필요 |
| `driver_app/.../DriverViewModel.kt` | 정산 데이터 로드 | 커밋됨 |
| `driver_app/.../HistorySettlementScreen.kt` | 정산 UI | 커밋됨 |

---

## Git 상태 확인 필요

```bash
cd D:/designated_driver
bash git-check.sh
```

미커밋 변경사항:
- `functions/src/index.ts` (settlementLastCleared 보완 로직)
- `.claude/settings.local.json`

---

## 핵심 원칙 (재확인)

```
앱 시작 → Firestore에서 settlementLastCleared 로드
    ↓
lastClearedMillis 설정
    ↓
tripHistory 필터링 (lastClearedMillis 이후만 표시)
    ↓
todaySettlement 계산 (lastClearedMillis 이후 calls만)
```

**문제**: `settlementLastCleared`가 없으면 `lastClearedMillis = 0` → 모든 기록 표시
