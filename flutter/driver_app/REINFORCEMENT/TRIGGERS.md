# Flutter 기사앱 보강 발동 기준 (Triggers)

> **목적**: 각 보강 기능(R1/R2)을 **언제 도입할지** 측정 가능한 기준 미리 정의.
> **전제**: Phase 1 MVP iOS 출시 후 TestFlight 또는 정식 운영 중 측정. 트리거 발동 시 해당 보강 도입.
> **작성일**: 2026-04-16
> **참조**: `ios/driver_app/REINFORCEMENT/TRIGGERS.md` (Swift 트랙 동일 구조)

---

## 측정 인프라 선결 조건 (의제 11)

`memory/pending-work.md` — AND 이슈 참조. 현재 FCM 도달률 대시보드 없음.

**Phase 1 출시 전 최소한 구축**:
1. `acknowledgeNotification` Cloud Function 호출 기록 → Firestore에 저장 (서버 측)
2. iOS 기사 vs Android 기사 월별 수임 콜 수 집계 (CF·Firestore 쿼리로 도출)
3. **이벤트 로깅 추가**: ASSIGNED 수신 / ACCEPTED 클릭 / Time-To-Accept 측정 (수락률 R1 발동 기준 평가용)
4. 관리자 피드백 채널 (카카오톡 오픈채팅 또는 Google Form)

**측정 인프라 없으면 R1 발동 평가 불가능** → 의제 11 우선 결정.

---

## R1 — 1차 보강 (우선 도입 대상)

### R1_LOCKSCREEN — VoIP Push + CallKit + Live Activity (Flutter)

**대상 기능**: iOS 잠금화면 풀스크린 콜 수신 UI, 반복 벨소리

**Flutter 패키지**:
- `flutter_callkit_incoming` (CallKit + ConnectionService 통합)
- `flutter_voip_push_notification` (PushKit/PKPushRegistry, **maintenance 상태 재확인 필수**)
- 또는 자체 MethodChannel + 네이티브 Swift 코드 (~200줄)

**발동 기준 (하나라도 충족 시 도입 검토)**:
1. **콜 놓침률**: iOS 기사의 배차 수락률(ACCEPTED / ASSIGNED)이 Android 기사 대비 **5%p 이상 낮음** (월 단위, 표본 100콜 이상)
2. **관리자 피드백**: "iOS 기사가 콜 안 받는다" 피드백 **월 3회 이상**
3. **기사 피드백**: TestFlight 참여 기사 중 **50% 이상 "알림 놓친 적 있다"**
4. **iOS 기사 이탈**: iOS 기사 마지막 콜 수락 후 **7일 이상 무활동** 비율이 Android 대비 유의미하게 높음

**도입 비용 추산**:
- 개발: 2~3주 (CF 측 APNs VoIP 직접 호출 + Flutter 측 CallKit 핸들러)
- **App Store 심사 리스크**: CallKit은 "VoIP 통신 앱"에 한정되므로 가이드라인 4.5.4 위반 소지. 첫 제출 리젝 시 즉시 Time Sensitive로 롤백
- 심사 대응: App Review Notes에 "대리운전 배차 수신 알림. VoIP 아님. CallKit 사용 정당화" 명시

---

### R1_APP_KILLED — VoIP Push로 강제 종료 앱 깨움

**대상 기능**: 사용자가 앱을 스와이프 종료한 상태에서도 콜 도달

**발동 기준**:
1. **앱 종료 시 콜 누락**: 파일럿 중 "앱 종료 상태에서 콜 놓침" **월 5건 이상**
2. **사용자 습관 관찰**: 기사 인터뷰 중 **30% 이상 "정기적으로 앱 종료한다"**
3. 또는 R1_LOCKSCREEN과 함께 발동 (같은 인프라 사용)

**도입 비용 추산**: R1_LOCKSCREEN과 패키지로 구현 (단독 도입 시 1주)

---

### R1_PENDING_SYNC — sqflite/drift 오프라인 정산 큐

**대상 기능**: 장기 오프라인 시 정산 제출이 Firestore 자동 큐로 커버 안 되는 경우 보강

**Flutter 패키지**: `sqflite` 또는 `drift` (의제 12에서 결정)

**발동 기준**:
1. **오프라인 정산 실패**: 파일럿 중 "오프라인 상태에서 정산 제출 → 온라인 복귀 후 미반영" **1건이라도 발생**
2. **장기 오프라인 시나리오**: 기사가 **30분 이상 오프라인** 운행 후 정산 필요한 사례가 월 3회 이상
3. Android 측 `SettlementRepository.addPendingSync` 활성화 검증 후 (AND-02 해소) iOS에도 동등 도입

**도입 비용 추산**:
- 개발: 1주 (sqflite 테이블 + Riverpod Notifier + connectivity_plus 연동)

---

### R1_IMMEDIATE_SYNC — 온라인 복귀 시 즉시 PendingSync drain

**대상 기능**: WorkManager `OnNetworkAvailable` 등가 — 네트워크 복구 즉시 큐 flush

**Flutter 패키지**: `connectivity_plus` Stream 구독 + sqflite drain

**발동 기준**: R1_PENDING_SYNC 도입과 동시. 독립적 도입 무의미.

**도입 비용 추산**: R1_PENDING_SYNC 내 포함

---

### R1_NETWORK_BANNER — 네트워크 끊김 UI 배너

**대상 기능**: 기사가 오프라인 상태임을 즉시 인지 (빨간 배너 or 아이콘)

**Flutter 패키지**: `connectivity_plus`

**발동 기준**:
1. **혼란 사례**: 파일럿 중 "왜 안 되지" 원인이 네트워크였던 사례 **월 3회 이상**
2. 또는 사용자 설문에서 "네트워크 문제 인지 어렵다" 30% 이상

**도입 비용 추산**: 반나절 (connectivity_plus + Riverpod StreamProvider + Material 배너)

---

## R2 — 2차 보강 (데이터 기반 결정)

### R2_BG_SYNC — workmanager 주기 sync

**대상 기능**: Android WorkManager 15분 주기 sync 등가

**Flutter 패키지**: `workmanager` (Android WorkManager + iOS BGTaskScheduler 래핑)

**발동 기준**:
1. **Foreground drain으로 부족한 케이스**: 기사가 앱을 장시간 열지 않아 동기화 지연
2. R1_PENDING_SYNC 도입 후 **onAppear drain + connectivity_plus drain만으로 부족**

**위험 신호**: iOS `workmanager`는 BGTaskScheduler 래핑이라 **실행 보장 없음**. Apple이 배터리·사용 패턴 기준으로 임의 스케줄. Android 측만 신뢰 가능.

**도입 비용 추산**: 2일 (workmanager 등록 + Info.plist BGTaskSchedulerPermittedIdentifiers + sqflite write)

---

### R2_REFERRAL_QR — 추천 QR 화면

**대상 기능**: 기사가 고객에게 QR 공유해 손님앱 가입 유도

**Flutter 패키지**: `qr_flutter`

**발동 기준**:
1. **비즈니스 결정**: 고객 유입 부족 판단 시
2. 안드로이드 측 추천 QR 전환율 데이터로 iOS에서도 필요성 입증

**도입 비용 추산**: 2일 (QR 생성 + 화면)

---

### R2_WAKE_LOCK 대체 — 화면 상시 점등

**대상 기능**: 운행 중 화면 꺼지지 않게 유지

**Flutter 구현**: `WidgetsBinding.instance.lifecycleListenable` + `SystemChannels.platform.invokeMethod('SystemChrome.setEnabledSystemUIMode')` 또는 `wakelock_plus` 패키지

**발동 기준**:
1. 기사 피드백 "운행 중 화면 꺼져서 불편" 월 3회 이상
2. R1_LOCKSCREEN 도입 시 CallKit이 부분 커버하므로 단독 필요성 재평가

**도입 비용 추산**: 1시간 (`wakelock_plus` 의존성 추가 + 운행 시작/종료 시 enable/disable)

---

## D — iOS 구조적 불가 (도입 불가능)

- **Foreground Service 영구 활성** — iOS 등가 없음. 이벤트 기반 wakeup(VoIP)으로 우회만 가능
- **BootReceiver 자동 재시작** — iOS 정책상 불가. 사용자 앱 1회 실행 전제
- **LockScreenActivity 커스텀 풀스크린 UI** — iOS 불가. CallKit UI 고정 + Live Activity 카드 보강 (R1에서)
- **무한 진동 패턴** — iOS는 시스템 한정 진동만 허용

---

## 측정 주기

- **월간 체크인**: 위 트리거 지표를 월 1회 점검
- **임계값 근접 시**: 해당 R1 또는 R2 패키지 도입 사전 준비
- **트리거 발동 시**: 즉시 도입 + 사용자 승인

---

## 발동 기준 재검토

- Phase 1 출시 후 **3개월마다** 본 파일 전체 검토
- 실 운영 데이터로 임계값 자체가 비현실적이면 조정 (예: "5%p"가 너무 엄격/관대)
- 새로운 보강 필요성 발견 시 새 섹션 추가

---

## 참조

- `flutter/driver_app/PLAN.md` — Phase 1 범위
- `flutter/FUNCTIONAL_INVENTORY.md` Part A — 기사앱 R1/R2/D 분류 근거
- `flutter/WORKING_DOC.md` 의제 7 (LockScreen 대체), 의제 11 (측정 인프라)
- `ios/driver_app/REINFORCEMENT/TRIGGERS.md` — Swift 트랙 원본 (대칭 구조)
