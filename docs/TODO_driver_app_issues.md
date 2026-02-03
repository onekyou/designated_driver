# 기사앱 개선 필요 사항

## 날짜: 2026-02-03

---

## 1. 데이터 저장 중복 문제 (검토 필요)

### 현상
운행 완료 시 3곳에 데이터 저장:
1. **Firestore calls** - 운행 기록 (필수)
2. **SharedPreferences tripHistory** - 화면 표시용 문자열
3. **StateFlow todaySettlement** - 정산 집계 (메모리)

### 의문점
- SharedPreferences의 `tripHistory`가 중복일 수 있음
- calls에서 다시 계산하면 되는데 왜 별도로 저장?
- 원칙: "하나의 문서(calls)를 읽고 각자 계산"

### 현재 상태
- 기능에는 문제 없음 (로그아웃 후 다시 로그인해도 동일한 결과)
- 추후 구조 정리 시 검토 필요

---

## 2. 원칙 정리

### 콜매니저 & 기사앱 공통 원칙
```
앱 시작 → 최초 1회 Firestore 로드 (calls, carryOver 등)
    ↓
로컬에서 계산/업데이트 (todaySettlement, todayUnpaid 등)
    ↓
마감 시 콜매니저가 carryOver 업로드
    ↓
다음날 다시 1회 로드 → 반복
```

### 핵심
- **동일한 소스(calls)** → **동일한 계산 로직** → **동일한 결과**
- 콜매니저와 기사앱이 불일치 없이 동일한 값 표시

---

## 3. 완료된 수정 사항 (2026-02-03)

### 기사앱
- [x] `todaySettlement` 로컬 즉시 업데이트 (운행 완료 시)
- [x] 계산 로직 콜매니저와 일치 (`(fare * (100 - ratio) / 100.0).toInt()`)
- [x] `lastClearedMillis` StateFlow 노출
- [x] `tripHistory` 마감 기준 필터링 (5일 → lastClearedMillis)
- [x] 누적 미수령금 = carryOver.balance + todayUnpaid 합산 표시
- [ ] 빌드 및 테스트 (진행 중)

### Cloud Functions
- [x] `sendDriverNotification` 함수 추가 및 배포

### Firestore Rules
- [x] 불필요한 `drivers` 컬렉션 규칙 삭제

---

## 관련 파일

- `DriverViewModel.kt` - 정산 로직
- `HistorySettlementScreen.kt` - 정산 UI
- `SettlementViewModel.kt` (콜매니저) - 비교 참조용
