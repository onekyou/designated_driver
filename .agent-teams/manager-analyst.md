# manager-analyst 지속성 문서

> 마지막 업데이트: 2026-02-24

## 1. 담당 범위

**Call Manager 앱 전체** - 관리자용 Android 앱으로 콜 접수/배정, 기사 관리, 정산, 공유콜, 내장 Detector 기능을 담당.

---

## 2. 핵심 파일 목록

### 앱 진입점
| 파일 | 경로 | 역할 |
|------|------|------|
| `MainActivity.kt` | `call_manager/.../callmanager/MainActivity.kt` | 화면 라우팅, 전체 Navigation (Screen enum) |
| `CallManagerApplication.kt` | `call_manager/.../callmanager/CallManagerApplication.kt` | Application 초기화 |

### 핵심 ViewModel (비즈니스 로직)
| 파일 | 경로 | 역할 |
|------|------|------|
| `DashboardViewModel.kt` | `call_manager/.../ui/dashboard/DashboardViewModel.kt` | **가장 중요** - 콜 관리, 배차(`assignCallToDriver` L689), 취소(`cancelCall` L809), 완료(`completeCall` L870), 자동배차, 기사 상태 관리 |
| `SettlementViewModel.kt` | `call_manager/.../ui/settlement/SettlementViewModel.kt` | 정산/마감 처리 |
| `LoginViewModel.kt` | `call_manager/.../ui/login/LoginViewModel.kt` | 인증, 사무실 정보 로드 |
| `PendingDriversViewModel.kt` | `call_manager/.../ui/pendingdrivers/PendingDriversViewModel.kt` | 기사 가입 승인 (`approveDriver` L118) |
| `DriverManagementViewModel.kt` | `call_manager/.../ui/drivermanagement/DriverManagementViewModel.kt` | 기사 관리 (`approveDriver` L53) |
| `ExcludeNumberViewModel.kt` | `call_manager/.../ui/excludenumber/ExcludeNumberViewModel.kt` | 제외번호 관리 |

### 데이터 모델
| 파일 | 경로 | 역할 |
|------|------|------|
| `CallStatus.kt` | `call_manager/.../data/CallStatus.kt` | 콜 상태 enum 15개 (WAITING, SHARED_WAITING, CLAIMED, PENDING, ASSIGNED, ACCEPTED, PICKUP_COMPLETE, IN_PROGRESS, AWAITING_SETTLEMENT, COMPLETED, SHARED_OUT, **CANCELED**, **CANCELLED**, HOLD, UNKNOWN) |
| `Constants.kt` | `call_manager/.../data/Constants.kt` | 상수 정의 |
| `CallInfo.kt` | `call_manager/.../data/CallInfo.kt` | 콜 데이터 모델 |
| `DriverInfo.kt` | `call_manager/.../data/DriverInfo.kt` | 기사 데이터 모델 |
| `DriverStatus.kt` | `call_manager/.../data/DriverStatus.kt` | 기사 상태 enum |
| `SharedCallInfo.kt` | `call_manager/.../data/SharedCallInfo.kt` | 공유콜 데이터 모델 |
| `ExcludeNumberManager.kt` | `call_manager/.../data/ExcludeNumberManager.kt` | 제외번호 SharedPreferences 관리 |

### 서비스 (백그라운드)
| 파일 | 경로 | 역할 |
|------|------|------|
| `MyFirebaseMessagingService.kt` | `call_manager/.../service/MyFirebaseMessagingService.kt` | FCM 수신 - **ignoredTypes L97** (`DRIVER_ACCEPT`, `DRIVER_REJECT`, `SETTLED`, `AWAITING_SETTLEMENT`), `CALL_STATUS_UPDATE` 처리, 공유콜 취소 팝업 |
| `CallDetectorService.kt` | `call_manager/.../service/CallDetectorService.kt` | **내장 Detector** - 전화 감지 + Firestore 저장 |
| `CallReceiver.kt` | `call_manager/.../receiver/CallReceiver.kt` | BroadcastReceiver - 전화 상태 감지 → CallDetectorService 전달 |
| `PresenceManager.kt` | `call_manager/.../service/PresenceManager.kt` | RTDB 기반 연결 상태 관리 |
| `DeviceMonitoringService.kt` | `call_manager/.../service/DeviceMonitoringService.kt` | 기기 상태 모니터링 |
| `CallScreeningService.kt` | `call_manager/.../service/CallScreeningService.kt` | Android 10+ 콜 스크리닝 |

### UI 화면
| 파일 | 경로 | 역할 |
|------|------|------|
| `DashboardScreen.kt` | `call_manager/.../ui/dashboard/DashboardScreen.kt` | 메인 대시보드 - 콜 목록, 배차 UI, 상태별 색상 |
| `SharedCallSettingsScreen.kt` | `call_manager/.../ui/shared/SharedCallSettingsScreen.kt` | 공유콜 설정 |
| `SharedCallAcceptActivity.kt` | `call_manager/.../ui/SharedCallAcceptActivity.kt` | 공유콜 수락 화면 |

---

## 3. 크로스 검증 대상

### detector-analyst 확인 포인트
- **CROSS-05**: 내장 Detector(`CallDetectorService.kt`) vs 독립 Detector(`call_detector/`) 기능 차이
- **NEW-11**: 양쪽 배차 시 status 체크 없음 → 이중 배차 가능성
- 배차 후 기사 상태값 통일 (CROSS-01: 수정 완료)

### driver-analyst 확인 포인트
- **CROSS-07**: `cancelCall()` 수정 후 Driver App 수신 처리 (`call_cancelled` FCM)
- **CROSS-07**: `rejectCall()` Driver App에서 빈 함수 (L459-462)
- **CROSS-02**: CallStatus enum 값 매칭 (15개 vs 7개)
- `assignCallToDriver` → FCM `call_assigned` → Driver App 수신 경로

### customer-analyst 확인 포인트
- `cancelCall()` 후 고객앱 알림 여부
- 고객앱 `"CANCELLED"` vs Manager `"CANCELED"` 철자 차이

### firebase-analyst 확인 포인트
- **NEW-12**: Callable Functions 인증 체크 없음 (14개)
- **NEW-13**: `"CANCELED"` vs `"CANCELLED"` 철자 불일치 → cleanup 함수 누락
- `onCallStatusChanged` 기사 상태 자동 복구 로직 추가 제안
- Firestore 보안 규칙 `request.auth == null` 패턴

---

## 4. 발견한 이슈 목록

### 수정 완료
| 이슈 | 설명 | 상태 | 수정 위치 |
|------|------|------|----------|
| **CROSS-01** | 기사 상태 4자 불일치 → "ASSIGNED"로 통일 | 수정완료 | `DispatchActivity.kt:172,275` / `index.ts:1198` |
| **CROSS-07 일부** | `cancelCall()` 기사 WAITING 복구 + FCM 전송 | 수정완료 | `DashboardViewModel.kt:809-868` |
| **BUG-D05** | cancelCall FCM 전송 추가 | 해소 | `index.ts:4368-4431` (notifyDriverCancellation) |

### 미착수
| 이슈 | 심각도 | 설명 | Next Step |
|------|--------|------|-----------|
| **CROSS-07 나머지** | Critical | `rejectCall()` 빈 함수 (Driver App L459-462) - 3중 차단 (코드 비어있음 + FCM 무시 + 보안규칙 미지원) | Driver App `rejectCall()` 구현 + Firestore rules에 ASSIGNED→HOLD 전이 추가 + FCM 알림 경로 구축 |
| **NEW-11** | Critical | 이중 배차 경쟁 - `assignCallToDriver` (L689)에서 콜 status 체크 없음 | Firestore transaction + `status=="WAITING"` 검증 추가 |
| **CROSS-02** | High | CallStatus enum 불일치 (Manager 15개 vs Driver 7개 vs Detector 6개) | 공통 enum 정의 또는 사용하는 값들의 문자열 일치 검증 |
| **CROSS-05** | Critical | 내장 vs 독립 Detector 동기화 (아래 섹션 참조) | 기능 동기화 또는 내장 Detector를 독립 앱과 동일하게 업그레이드 |
| **CROSS-06** | Medium | 비원자적 업데이트 - 콜 상태와 기사 상태를 별도 update()로 수행 | Firestore batch 또는 transaction으로 원자적 업데이트 |
| **NEW-13** | Medium | CANCELED/CANCELLED 철자 불일치 → cleanup 함수에서 `"CANCELED"` 누락 | cleanup에 `"CANCELED"` 쿼리 추가 (즉시) + 전체 통일 (중기) |
| **공유콜 필터** | High | shared_calls에서 `"OPEN"` vs `"SHARED_WAITING"` 불일치 - DashboardViewModel L367에서 `"OPEN"` 조회, 공유 시 L1530에서 `"SHARED_WAITING"` 설정 | 상태값 통일 또는 쿼리 수정 |

### 오탐 확정
| 이슈 | 사유 |
|------|------|
| **approveDriver 한글 문자열** | `_showApprovalPopup` (L173) 미사용 데드코드 → Low로 하향 |
| **NEW-09 (doc.id vs authUid)** | 등록 흐름에서 보장됨 |

---

## 5. 수정 이력

### cancelCall() 확장 (2026-02-24)

**파일**: `call_manager/app/src/main/java/com/designated/callmanager/ui/dashboard/DashboardViewModel.kt`
**라인**: 809-868

**수정 전**: 콜 상태를 CANCELED로 변경만 함
**수정 후**:
1. 콜 상태를 `CallStatus.CANCELED.firestoreValue` ("CANCELED")로 변경 (L822)
2. 배정된 기사가 있으면 `assignedDriverAuthUid` 조회 (L820)
3. 기사 상태를 `"WAITING"`으로 복구 (L838)
4. Cloud Functions `notifyDriverCancellation` 호출하여 기사에게 FCM 전송 (L851)

```kotlin
// L809-868 핵심 흐름
fun cancelCall(callId: String) {
    callRef.update("status", CallStatus.CANCELED.firestoreValue)  // L822
    if (!assignedDriverAuthUid.isNullOrBlank()) {
        driverDoc.reference.update("status", "WAITING")  // L838
        functions.getHttpsCallable("notifyDriverCancellation").call(data)  // L851
    }
}
```

### Cloud Functions: notifyDriverCancellation 추가 (2026-02-24)

**파일**: `functions/src/index.ts`
**라인**: 4368-4431

기사에게 `type: "call_cancelled"` FCM 전송. Driver App의 `MyFirebaseMessagingService`에서 수신 처리.

---

## 6. 내장 Detector vs 독립 Detector 차이점 (CROSS-05)

| 기능 | 독립 Detector (`call_detector/`) | 내장 Detector (`CallDetectorService.kt`) |
|------|--------------------------------|----------------------------------------|
| **wasRinging 플래그** | 있음 - 수신 전화만 감지하여 발신 콜 오생성 방지 | **없음** - 발신 전화도 감지될 수 있음 |
| **ExcludeNumber 연동** | 있음 - Firestore에서 제외번호 동기화 | **부분적** - `ExcludeNumberManager` 존재하나 CallDetectorService에서 사용하지 않음 |
| **CallScreeningService** | 없음 | 있음 (Android 10+) - 별도 경로로 전화 감지 |
| **배차 기능** | DispatchActivity로 즉시 배차 가능 | DashboardViewModel에서 배차 |
| **콜 생성 위치** | Firestore 직접 저장 | Firestore 직접 저장 (동일) |
| **이중 콜 방지** | 전화번호 + 시간 기반 중복 체크 | **확인 필요** - CallDetectorService 내부 로직 검증 필요 |

**결론**: 내장 Detector가 독립 버전보다 기능이 부족하여 운영 환경에서 문제 발생 가능.

---

## 7. 팀원 간 검증 요청/결과 요약

### 보낸 검증 요청
| 수신 | 내용 | 결과 |
|------|------|------|
| firebase-analyst | `onCallStatusChanged` 기사 상태 자동 복구 개선안 검토 | 동의 - 세 가지 취소 상태 모두 커버하는 방어 로직 합의 |
| firebase-analyst | CANCELED/CANCELLED 철자 불일치 cleanup 누락 확인 | 확인됨 - NEW-13으로 등록 |
| driver-analyst | AWAITING_SETTLEMENT ignoredTypes 데드코드 확인 | 확인됨 - 최상위 type 필드 불일치로 실제 작동 안 함 |
| driver-analyst | BUG-D07 (completeCall 기사 상태 미변경) 코드 검증 | 확인됨 - L604에서 콜만 변경, 기사 상태는 confirmAndFinalizeTrip L675에서 복구 |

### 받은 검증 요청
| 발신 | 내용 | 결과 |
|------|------|------|
| detector-analyst | CROSS-01 Manager 쪽 기사 상태값 확인 | 확인 → Manager는 "ASSIGNED" 사용, Detector는 "ON_TRIP" → 불일치 확정 → 수정 완료 |
| driver-analyst | rejectCall() Call Manager 쪽 대응 함수 존재 여부 | 확인 → Call Manager에 rejectCall 없음 |
| firebase-analyst | Driver App 실시간 리스너 부재 확인 | 확인 → FCM이 유일한 취소 통지 경로 |
| team-lead | cancelCall 수정 후 FCM 필드명 3자 대조 | 확인 → Manager→CF→Driver 필드명 모두 일치 |

---

## 8. 미해결 이슈 Next Step

### 즉시 수정 가능 (단순 변경)
1. **NEW-13 cleanup 누락**: `index.ts:3250` 부근에 `"CANCELED"` 쿼리 추가 (1줄)
2. **ignoredTypes 데드코드 정리**: `MyFirebaseMessagingService.kt:97`에서 `"AWAITING_SETTLEMENT"` 제거 (코드 정리)

### Phase 1 (보안)
3. **NEW-12**: `DashboardViewModel`에서 호출하는 Callable Functions에 `request.auth` 검증 추가

### Phase 2 (데이터 무결성)
4. **NEW-11**: `assignCallToDriver` (L689)에 Firestore transaction + status 체크 추가
5. **CROSS-07 나머지**: rejectCall 전체 경로 구현 (Driver App + Cloud Functions + Firestore Rules)
6. **NEW-13 통일**: 전체 코드베이스에서 `"CANCELED"` → `"CANCELLED"` 통일
7. **onCallStatusChanged 개선**: 취소 시 기사 상태 자동 복구 서버사이드 방어 로직

### Phase 3 (기능 개선)
8. **CROSS-05**: 내장 Detector에 wasRinging + ExcludeNumber 연동 추가
9. **CROSS-06**: cancelCall, completeCall 등 비원자적 업데이트를 transaction으로 전환
10. **공유콜 필터**: OPEN vs SHARED_WAITING 상태값 정리

---

## 9. 참고: 전체 파일 경로 기준

모든 경로의 루트: `C:\Users\kala1\designated_driver\`

- Call Manager 소스: `call_manager/app/src/main/java/com/designated/callmanager/`
- Cloud Functions: `functions/src/index.ts`
- Firestore Rules: `firestore.rules`
- Driver App 소스: `driver_app/app/src/main/java/com/designated/driverapp/`
- Call Detector 소스: `call_detector/app/src/main/java/com/designated/calldetector/`
- Customer App 소스: `customer_app/app/src/main/java/com/designated/customer/`
