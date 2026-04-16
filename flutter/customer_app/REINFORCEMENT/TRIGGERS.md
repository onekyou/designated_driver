# Flutter 손님앱 보강 발동 기준 (Triggers)

> **목적**: 손님앱 보강 기능(R1/R2)을 **언제 도입할지** 측정 가능한 기준 미리 정의.
> **작성일**: 2026-04-16

---

## 측정 인프라 선결 조건

`flutter/driver_app/REINFORCEMENT/TRIGGERS.md` §측정 인프라와 동일. 추가:
- 사무실 코드 입력 화면 이탈률 측정 (Phase 2 App Clip 발동 기준)
- iOS 손님 콜 요청 성공률 vs Android 비교

---

## R1 — 1차 보강

### R1_ATTRIBUTION_ACCURACY — Phase 2 App Clip 우선 도입

**대상 기능**: iOS 손님앱 귀속을 수동 사무실 코드 입력에서 App Clip QR 스캔으로 전환

**발동 기준 (하나라도 충족 시 Phase 2 즉시 진입)**:
1. **수동 입력 화면 이탈률 30% 이상** (월 단위)
2. **사무실 코드 잘못 입력 → 콜 요청 실패** 사례가 **월 5건 이상**
3. **기사 피드백**: "iOS 손님이 코드 입력을 어려워한다" **월 3회 이상**
4. **Mac 도착 시점에 자동으로 진입** (이탈률 무관, 우선순위 작업)

**도입 비용 추산**:
- 개발: 2~3주 (Swift App Clip 타겟 + AASA 호스팅 + Custom Token 이관 CF)
- **Mac 필수**

---

### R1_PUSH_RELIABILITY — iOS FCM 수신률 보강

**대상 기능**: iOS data-only FCM 수신 안정화

**발동 기준**:
1. iOS 손님 FCM 수신률 **90% 미만** (acknowledgeNotification CF 호출 카운트 / 발송 카운트 비율)
2. 손님 인터뷰 중 "콜 진행 알림이 안 온다" **월 3회 이상**

**대응책 (즉시 가능)**:
- CF `oncallassigned`, `notifyCustomerOnComplete` 등에 `apns: {payload: {aps: {'content-available': 1, 'mutable-content': 1}}}` 추가 (의제 4)
- 추가 보강 시 `firebase_messaging` background isolate 핸들러 검증

**도입 비용 추산**: CF 수정 반나절 + 검증 1주

---

### R1_OFFLINE_QUEUE — 콜 요청 오프라인 큐

**대상 기능**: 손님이 오프라인 상태에서 콜 요청 시 자동 재시도

**발동 기준**:
1. 손님 인터뷰 중 "콜 요청 실패" 원인이 네트워크였던 사례 **월 5건 이상**
2. Firestore 자동 큐잉으로 커버 안 되는 시나리오 발견

**도입 비용 추산**: 1주 (sqflite + connectivity_plus)

---

## R2 — 2차 보강

### R2_VOICE_INPUT — 음성 입력

**대상 기능**: 출발지/도착지 음성 입력 + 한국어 숫자 변환

**Flutter 패키지**: `speech_to_text` (현재 driver_app_flutter에서 commented out)

**발동 기준**:
1. 손님 설문 "음성 입력 원함" 30% 이상
2. Kotlin 손님앱 사용 시 음성 입력 사용률 데이터 (있다면)
3. 비즈니스 결정 (편의성 차별화 포인트)

**도입 비용 추산**: 1주 (speech_to_text + 한국어 숫자 정규화 로직)

---

### R2_BANNER_AD — 배너 광고

**대상 기능**: 홈 화면 배너 광고 영역

**Flutter 패키지**: `google_mobile_ads` 또는 자체 Firestore 기반 (Kotlin 패턴)

**발동 기준**:
1. 비즈니스 결정 (수익 모델)
2. Kotlin BannerAdData 활용도 검증

**도입 비용 추산**: 3일

---

### R2_PUSH_PHONE_NUMBER_CHANGE — 전화번호 변경 시 FCM 토큰 갱신

**대상 기능**: 손님이 전화번호 변경 시 customerInfo/{phone} 경로 마이그레이션

**발동 기준**: Kotlin 원본에 없는 기능. 사용자 요청 발생 시.

---

## D — iOS 구조적 불가

- 손님앱은 Background Modes 거의 불필요 (콜 요청 시점에만 활성)
- LocalBroadcastManager 등 Android 특수 기능은 Flutter에서 자연스럽게 Stream으로 대체

---

## 측정 주기

- **월간 체크인**: 위 트리거 지표 월 1회 점검
- **Mac 도착 시점**: R1_ATTRIBUTION_ACCURACY 자동 발동 (Phase 2 진입)

---

## 참조

- `flutter/customer_app/PLAN.md` — Phase 1/2 범위
- `flutter/FUNCTIONAL_INVENTORY.md` Part B — 손님앱 분류
- `flutter/WORKING_DOC.md` 의제 13 (Phase 2 App Clip)
