# 정산 시스템 수정 TODO 리스트

> 우선순위: 🔴 긴급 | 🟠 높음 | 🟡 중간 | 🟢 낮음

---

## 🔴 긴급 (데이터 무결성 위험)

### 1. 정산 데이터 이중 저장 제거
**현재 문제:**
- 기사앱에서 `settlementSessions` 직접 저장
- Cloud Function에서도 `settlementSessions` 저장
- 동시 실행 시 Race Condition 발생 가능

**수정 방안:**
- [ ] 기사앱의 `saveToSettlementSession()` 함수 제거
- [ ] Cloud Function `onCallCompletedUpdateSettlement`만 사용
- [ ] 기사앱은 `calls` 컬렉션만 업데이트

**영향 파일:**
- `driver_app/.../DriverViewModel.kt` - `saveToSettlementSession()` 제거
- `driver_app/.../SettlementRepository.kt` - 관련 함수 정리

---

### 2. 마감 직전 운행완료 누락 방지
**현재 문제:**
- 콜매니저에서 마감 버튼 클릭
- 동시에 기사앱에서 운행완료
- 마감된 세션에 새 콜이 추가 안 됨

**수정 방안:**
- [ ] 마감 전 2분간 "마감 예정" 상태 추가
- [ ] 마감 예정 중에는 운행완료 허용, 마감 시 재집계
- [ ] 또는: 마감 후에도 해당 근무일 콜은 다음 세션으로 이월

**영향 파일:**
- `functions/src/handlers/settlement.ts`
- `call_manager/.../SettlementViewModel.kt`

---

## 🟠 높음 (사용자 경험 문제)

### 3. 마이너스 입금액 경고 시스템
**현재 문제:**
- 외상이 많으면 기사 납입액이 마이너스
- 아무런 경고 없음

**수정 방안:**
- [ ] 콜매니저: 기사별 납입액 마이너스 시 경고 배지
- [ ] 기사앱: 마이너스 상태 시 알림 표시
- [ ] 일일 마감 시 마이너스 기사 목록 표시

**영향 파일:**
- `call_manager/.../DriverSummaryScreen.kt`
- `driver_app/.../HomeScreen.kt`

---

### 4. 외상 한도 초과 경고
**현재 문제:**
- 고객별 외상 한도 없음
- 외상 누적되어도 알림 없음

**수정 방안:**
- [ ] 사무실 설정에 기본 외상 한도 추가 (예: 50,000원)
- [ ] 외상 결제 시 한도 체크
- [ ] 한도 초과 시 콜매니저에 경고

**영향 파일:**
- `driver_app/.../TripCompletionDialog.kt`
- `call_manager/.../SettlementViewModel.kt`
- Firestore: offices 컬렉션에 `creditLimit` 필드 추가

---

## 🟡 중간 (안정성 개선)

### 5. 오프라인 동기화 재시도 로직
**현재 문제:**
- 오프라인에서 저장 후 동기화 실패 시 재시도 불완전
- 실패한 데이터가 영구 누락 가능

**수정 방안:**
- [ ] WorkManager로 백그라운드 재시도 구현
- [ ] 지수 백오프 (1분 → 2분 → 4분 → 8분)
- [ ] 3회 실패 시 사용자에게 알림
- [ ] 동기화 대기 건수 UI 표시

**영향 파일:**
- `driver_app/.../SettlementRepository.kt`
- `driver_app/.../SyncWorker.kt` (신규)

---

### 6. 정산 불일치 실시간 알림
**현재 문제:**
- 불일치 검사는 매일 오전 7시에만 실행
- 불일치 발생 후 다음날까지 모름

**수정 방안:**
- [ ] 마감 시 즉시 불일치 검사 실행
- [ ] 불일치 발견 즉시 콜매니저에 팝업
- [ ] 불일치 상세 내역 표시 (누락 콜, 금액 차이 등)

**영향 파일:**
- `functions/src/handlers/settlement.ts` - `checkSettlementDiscrepancies`
- `call_manager/.../MyFirebaseMessagingService.kt`

---

### 7. 결제방식 변경 이력 추적
**현재 문제:**
- 기사가 결제방식을 잘못 선택 후 수정 불가
- 콜매니저에서 수정해도 이력 없음

**수정 방안:**
- [ ] calls 문서에 `paymentHistory` 배열 추가
- [ ] 변경 시 이전 값과 변경자 기록
- [ ] 콜매니저에서 결제방식 수정 기능 추가

**영향 파일:**
- `call_manager/.../TripDetailDialog.kt`
- Firestore: calls 문서 구조 변경

---

## 🟢 낮음 (기능 개선)

### 8. 기사별 정산 상세 리포트
**현재 문제:**
- 기사앱에서 본인 정산 상세 확인 불편
- 일별/주별/월별 통계 없음

**수정 방안:**
- [ ] 기사앱에 정산 리포트 화면 추가
- [ ] 일별/주별/월별 집계
- [ ] 결제방식별 통계 차트

**영향 파일:**
- `driver_app/.../SettlementReportScreen.kt` (신규)

---

### 9. 정산 데이터 내보내기
**현재 문제:**
- 정산 데이터 외부 활용 불가
- 세무/회계 처리 불편

**수정 방안:**
- [ ] 콜매니저에서 CSV/Excel 내보내기
- [ ] 기간 선택 가능
- [ ] 기사별/고객별/결제방식별 필터

**영향 파일:**
- `call_manager/.../SettlementExportUtil.kt` (신규)

---

### 10. 자동 외상 정산 알림
**현재 문제:**
- 외상 고객에게 알림 없음
- 관리자가 수동으로 연락해야 함

**수정 방안:**
- [ ] 외상 발생 7일 후 자동 SMS 알림
- [ ] 고객앱 사용자에게 푸시 알림
- [ ] 알림 발송 이력 관리

**영향 파일:**
- `functions/src/index.ts` - 스케줄러 함수 추가
- `customer_app/.../MyFirebaseMessagingService.kt`

---

## 작업 순서 권장

```
1주차: 🔴 긴급 (#1, #2)
       ├─ 이중 저장 제거
       └─ 마감 타이밍 문제 해결

2주차: 🟠 높음 (#3, #4)
       ├─ 마이너스 경고
       └─ 외상 한도

3주차: 🟡 중간 (#5, #6, #7)
       ├─ 오프라인 동기화
       ├─ 실시간 불일치 알림
       └─ 결제방식 변경 이력

4주차+: 🟢 낮음 (#8, #9, #10)
        ├─ 리포트 기능
        ├─ 내보내기
        └─ 외상 알림
```

---

## 변경 이력

| 날짜 | 버전 | 변경 내용 |
|------|------|----------|
| 2026-02-02 | 1.0 | 최초 작성 |
