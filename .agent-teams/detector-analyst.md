# detector-analyst 지속성 문서

**마지막 업데이트**: 2026-02-24
**담당**: Call Detector 앱 전체 분석 (callmadang-v2 팀)
**상태**: 모든 크로스 검증 완료, Task #1/#5/#10 완료

---

## 1. 담당 범위

Call Detector 앱: 대리운전 사무실의 전화 감지 + 배차 기능을 담당하는 Android 앱.
- 전화 수신 감지 (BroadcastReceiver + CallScreeningService)
- Firestore에 콜 문서 생성 (status: WAITING)
- 관리자가 기사 선택하여 배차 (status: ASSIGNED, driver: ON_TRIP)
- 공유콜 생성 (shared_calls 컬렉션)
- 크래시 감지 및 리포팅 (CrashReportService)

---

## 2. 핵심 파일 목록

### 콜 감지 및 생성
| 파일 | 경로 | 역할 | 핵심 라인 |
|------|------|------|----------|
| CallDetectorService.kt | `call_detector/app/src/main/java/com/designated/calldetector/CallDetectorService.kt` (986줄) | 메인 서비스. 전화 감지, 콜 생성, 공유콜 생성 | createNormalCall: 710-798, shared_calls RINGING: 916-950, IDLE: 803-836, wasRinging: 49, ExcludeNumber: 65,84,123-129, temp callId: 787 |
| CallReceiver.kt | `call_detector/.../CallReceiver.kt` (165줄) | BroadcastReceiver. PHONE_STATE + NEW_OUTGOING_CALL 처리 | staticIsIncoming: RINGING시 true, NEW_OUTGOING_CALL시 false |
| CallScreeningService.kt | `call_detector/.../CallScreeningService.kt` (67줄) | Android 10+ 전화번호 추출 | latestIncomingNumber static volatile |
| ExcludeNumberManager.kt | `call_detector/.../ExcludeNumberManager.kt` (414줄) | 개인번호 제외 관리 | SharedPreferences "exclude_numbers" |

### 배차 UI
| 파일 | 경로 | 역할 | 핵심 라인 |
|------|------|------|----------|
| DispatchActivity.kt | `call_detector/.../ui/DispatchActivity.kt` (383줄) | 배차 UI + Firestore 작업 | updateCallWithDriver: 141-199 (정상경로), createCallWithDriver: 204-276 (fallback), deleteCall: 283-300, createSharedCall: 302-331 |
| DispatchDialog.kt | `call_detector/.../ui/DispatchDialog.kt` | Composable 배차 다이얼로그 | DriverInfo data class: 16-22, CallInfo: 25-28 |

### 데이터/설정
| 파일 | 경로 | 역할 |
|------|------|------|
| CallStatus.kt | `call_detector/.../data/CallStatus.kt` | enum 6개: WAITING, PENDING, MATCHED, DISPATCHED, COMPLETED, CANCELLED |
| DetectorConfigViewModel.kt | `call_detector/.../ui/DetectorConfigViewModel.kt` (261줄) | 설정 ViewModel. SharedPreferences "CallDetectorPrefs" |
| LoginViewModel.kt | `call_detector/.../ui/login/LoginViewModel.kt` (157줄) | Firebase Auth 관리자 로그인. admins/{uid} 확인 |
| LoginScreen.kt | `call_detector/.../ui/login/LoginScreen.kt` | 로그인 UI |
| MainActivity.kt | `call_detector/.../MainActivity.kt` | 메인 Activity. 로그인 성공 시 "detector_config" + "call_detector_auth" 저장 |

### 시스템
| 파일 | 경로 | 역할 |
|------|------|------|
| CrashReportService.kt | `call_detector/.../CrashReportService.kt` (586줄) | 크래시 감지. device_status/device_alerts/emergency_alerts에 클라이언트 SDK로 직접 쓰기. provinceId/cityId/officeId 필드명 사용 |
| CallDetectorApplication.kt | `call_detector/.../CallDetectorApplication.kt` (126줄) | Application 클래스. Firebase+Sentry+Crashlytics 초기화 |
| BootCompletedReceiver.kt | `call_detector/.../BootCompletedReceiver.kt` (59줄) | 부팅 시 서비스 재시작. SharedPreferences "call_detector_prefs" (device_name 키) |

### 비교 대상 (Manager 내장 Detector)
| 파일 | 경로 | 역할 |
|------|------|------|
| CallDetectorService.kt (Manager) | `call_manager/.../service/CallDetectorService.kt` (955줄) | 내장 Detector. wasRinging 없음, ExcludeNumber 미사용, fromCallManager=true |
| CallStatus.kt (Manager) | `call_manager/.../data/CallStatus.kt` | enum 15개 (ASSIGNED, ACCEPTED, IN_PROGRESS 등 포함) |

### Cloud Functions
| 파일 | 경로 | 핵심 |
|------|------|------|
| index.ts | `functions/src/index.ts` | notifyDriverAssignment: line 4304 (onCall, auth 체크 없음) |

---

## 3. 크로스 검증 대상 및 완료 상태

### manager-analyst (Call Manager)
| 검증 항목 | 상태 | 결과 |
|----------|------|------|
| Detector 배차 필드 vs Manager CallInfo 매핑 | 완료 | 대부분 일치. refreshData에서 일부 필드 누락 (비필수) |
| assignedDriverId: authUid vs authUid.ifEmpty{doc.id} | 완료 | Detector fallback 존재. 실제로는 doc.id==authUid (False Positive) |
| assignedTimestamp: serverTimestamp vs Timestamp.now() | 완료 | 차이 존재. 시간 정렬 불일치 가능 |
| updatedAt 필드: Manager만 설정 | 완료 | Detector 배차 콜에 updatedAt 없음 |
| fromCallDetector 처리 (팝업/알림/알림음 억제) | 완료 | 3곳에서 의도적 억제. Fallback path 누락 시 팝업 오표시 |
| CallStatus enum 비교 (6 vs 15) | 완료 | CROSS-02 확인. Detector 실사용 1개(WAITING), 나머지 문자열 리터럴 |
| CROSS-05 독립 vs 내장 Detector | 완료 | wasRinging, ExcludeNumber, SharedPrefs 차이 확인 |
| shared_calls OPEN vs SHARED 필터 | 완료 | Manager가 "OPEN"만 필터 → 수동 공유콜 안 보임 |
| 이중 배차 경쟁 (NEW-11) | 완료 | 양쪽 모두 상태 검증 없이 배차. Race condition 확인 |
| 관리자 등록 경로 (top-level admins) | 완료 | 동기화 문제 없음 확인 |

### driver-analyst (Driver App)
| 검증 항목 | 상태 | 결과 |
|----------|------|------|
| FCM payload 필드 매칭 | 완료 | callId, type, title, body 일치. notificationId 누락 → ACK 불가 |
| departure 미사용 | 완료 | Driver App이 FCM의 departure 파싱 안 함 (문제 없음) |
| CROSS-01 기사 상태 4-Way 불일치 | 완료 | Detector=ON_TRIP, Manager=ASSIGNED, CF=배차중, Driver=PREPARING |
| 상태 역행 ON_TRIP→PREPARING | 완료 | acceptCall()에서 기사 상태 미체크. 역행 발생 확인 |
| rejectCall() 비어있음 + Detector 배차 | 완료 | CRITICAL: 기사 ON_TRIP 영구 잠김, 콜 ASSIGNED 좀비화 |
| deleteCall() 후 기사 상태 미복구 | 완료 | CRITICAL: 기사 ON_TRIP에 갇힘 |
| doc.id vs authUid | 완료 | False Positive (doc.id == authUid 보장) |
| phoneNumber vs phone 필드명 | 완료 | Detector는 "phoneNumber" 읽기. 실제 문서 필드명 확인 필요 |

### firebase-analyst (Firestore + Cloud Functions)
| 검증 항목 | 상태 | 결과 |
|----------|------|------|
| 9개 쿼리 패턴 vs 보안 규칙 대조 | 완료 | 모든 작업 PASS (관리자 인증) |
| Detector 인증 방식 확인 | 완료 | Firebase Auth 관리자 계정 로그인 확인 |
| notifyDriverAssignment 존재 확인 | 완료 | index.ts:4304에 onCall로 존재 |
| device_status/device_alerts/emergency_alerts 보안 규칙 | 완료 | 규칙 없음 → 클라이언트 쓰기 거부 (NEW-10) |
| crashlytics-monitor.js regionId vs provinceId | 완료 | 필드명 불일치 확인 (NEW-08) |
| isOfficeAdmin 경로 (top-level vs nested) | 완료 | top-level 사용 확인. 경로 불일치 없음 (False Positive) |
| onCall 함수 auth 체크 | 완료 | 14개 callable 함수 전부 auth 미검증 |
| WriteBatch 트리거 타이밍 | 완료 | batch 내 개별 문서 트리거 순서 미보장 |
| shared_calls create 규칙 | 완료 | isAnyAdmin() → PASS |
| 데드 코드 규칙 (line 171-172) | 완료 | 확인 (Detector 관련 아님) |
| 콜 취소 규칙 소유권 미검증 | 완료 | 확인. Detector는 customerAuthUid 미설정 |

---

## 4. 발견한 이슈 목록

### CROSS 이슈 (1차 보고서 검증)
| ID | 심각도 | 내용 | 상태 | 코드 위치 |
|----|--------|------|------|----------|
| CROSS-01 | Critical | 기사 상태 4-Way 불일치: Detector=ON_TRIP, Manager=ASSIGNED, CF=배차중, Driver=PREPARING | **미착수** (수정 필요: DispatchActivity.kt:172,275 → "ASSIGNED"로 통일) | DispatchActivity.kt:172,275 |
| CROSS-02 | High | CallStatus enum 불일치 (6 vs 15 vs 7). Detector 실사용 1개, 나머지 문자열 리터럴 | **미착수** | CallStatus.kt 전체, DispatchActivity.kt:153,225,319 |
| CROSS-05 | High | 독립 vs 내장 Detector: wasRinging 없음, ExcludeNumber 미사용, SharedPrefs 차이 | **미착수** | CallDetectorService.kt (양쪽) |
| CROSS-06 | High | 비원자적 3단계 배차 (콜 업데이트 + 기사 업데이트 + FCM) | **미착수** | DispatchActivity.kt:160-198 |
| CROSS-07 | Critical | reject/cancel 미구현. 배차 후 취소 시 기사 ON_TRIP 복구 불가 | **미착수** | DispatchActivity.kt (기능 부재) |

### NEW 이슈 (이번 분석 신규 발견)
| ID | 심각도 | 내용 | 상태 | 코드 위치 |
|----|--------|------|------|----------|
| NEW-01 | Medium | FCM customerName 빈 문자열 (updateCallWithDriver에서 contactName 파라미터 없음) | **미착수** | DispatchActivity.kt:184 |
| NEW-03 | Medium | temp_${timestamp} callId 중복 위험 (네트워크 복구 시) | **미착수** | CallDetectorService.kt:787 |
| NEW-04 | Medium | SharedPreferences 8파일 파편화 (detector_config, CallDetectorPrefs, call_detector_login_prefs, call_detector_auth, call_detector_prefs, exclude_numbers, crash_cache, call_detector_prefs) | **미착수** | 전체 앱 |
| NEW-05 (CD-01) | High | shared_calls RINGING+IDLE 중복 생성 (사무실 CLOSED 시 동일 통화에 2개 문서) | **수정 완료** | CallDetectorService.kt:50,190,691-700,907 (sharedCallCreatedFromRinging 플래그) |
| NEW-06 | High | shared_calls status 불일치: 자동="OPEN", 수동="SHARED". Manager가 "OPEN"만 필터 → 수동 공유콜 안 보임 | **미착수** | CallDetectorService.kt:637,818,931 vs DispatchActivity.kt:319 |
| NEW-07 | High | Fallback createCallWithDriver 필드 누락: fromCallDetector, isAppCustomer, customerId, createdFrom 없음 | **미착수** | DispatchActivity.kt:204-276 |
| NEW-08 | Critical | crashlytics-monitor.js regionId vs CrashReportService provinceId 필드명 불일치 → 크래시 알림 미전달 | **미착수** | CrashReportService.kt:274-276 vs crashlytics-monitor.js |
| NEW-10 | Critical | 크래시 감지 시스템 보안 규칙 부재: device_status(3곳), device_alerts(5곳), emergency_alerts(1곳) 클라이언트 SDK 쓰기 거부 | **미착수** | CrashReportService.kt:176,310,526 (device_status), 148,440,480,544,567 (device_alerts), 510 (emergency_alerts) |
| NEW-11 | Critical | 이중 배차 경쟁: Detector+Manager 동시 배차 시 상태 검증 없음. Last-write-wins | **미착수** | DispatchActivity.kt (전체 배차 로직) |
| NEW-12 | Medium (Security) | 평문 비밀번호 SharedPreferences 저장 | **미착수** | LoginViewModel.kt:130-133 |

### 오탐 (False Positive)
| ID | 내용 | 확인자 | 이유 |
|----|------|--------|------|
| NEW-09 | doc.id vs authUid 불일치 | manager-analyst | 기사 등록 시 document(authUid) 사용 → doc.id == authUid 보장 |
| isOfficeAdmin 경로 | top-level vs nested admins 불일치 | team-lead, manager-analyst, firebase-analyst | isOfficeAdmin()은 top-level admins/{uid} 사용 확인 |

---

## 5. 수정 이력

| 수정 | 파일 | 내용 |
|------|------|------|
| CD-01 (NEW-05) | CallDetectorService.kt:50,190,691-700,907 | `sharedCallCreatedFromRinging` 플래그 추가. RINGING에서 공유콜 생성 시 true 설정, IDLE의 CLOSED 분기에서 true이면 중복 생성 방지 |

---

## 6. 크로스 검증 요청/응답 요약

### 보낸 주요 검증 요청
| 수신자 | 내용 | 결과 |
|--------|------|------|
| manager-analyst | Detector 배차 필드 + CallStatus enum | Manager CallInfo 매핑 확인, CROSS-01/02/05 상호 확인 |
| manager-analyst | shared_calls OPEN vs SHARED 필터 | Manager가 "OPEN"만 필터 → 수동 공유콜 누락 버그 확인 |
| manager-analyst | 관리자 등록 경로 (top-level vs nested) | 동기화 문제 없음 확인 |
| driver-analyst | FCM payload + driver status + CROSS-01 | 4-Way 불일치 확인, rejectCall 비어있음 확인 |
| driver-analyst | doc.id vs authUid | False Positive 확인 |
| firebase-analyst | 9개 쿼리 패턴 + 보안 규칙 대조 | 모든 작업 PASS (관리자 인증) |
| firebase-analyst | device_status 클라이언트 SDK 쓰기 | 보안 규칙 부재 → 쓰기 거부 확인 (NEW-10) |
| firebase-analyst | crashlytics-monitor.js 필드명 | provinceId vs regionId 불일치 확인 (NEW-08) |
| firebase-analyst | notifyDriverAssignment 존재 여부 | index.ts:4304에 onCall 함수 존재 확인 |
| firebase-analyst | Detector 인증 방식 (관리자 계정) | 관리자 로그인 확인 → 모든 보안 규칙 PASS |

### 받은 주요 검증 요청
| 발신자 | 내용 | 응답 |
|--------|------|------|
| manager-analyst | Detector CallStatus enum 전체 | enum 6개 + 실사용 4개 상태 (문자열 리터럴 포함) 전달 |
| manager-analyst | CROSS-05 wasRinging/ExcludeNumber 확인 | 독립 Detector에 wasRinging+ExcludeNumber 있음, Manager에 없음 확인 |
| driver-analyst | doc.id vs authUid 관계 | doc.id(Firestore 문서 ID) vs authUid(필드) 별도 관리이나 실제로는 동일값 |
| driver-analyst | FCM payload 상세 | callId, type, title, body, departure 전달. notificationId 없음 |
| firebase-analyst | device_status 클라이언트 SDK 직접 쓰기 여부 | 3개 컬렉션 9곳에서 클라이언트 SDK 직접 쓰기 확인 |
| firebase-analyst | CrashReportService 필드명 | provinceId/cityId/officeId 사용 확인 |
| firebase-analyst | Detector Firestore 쿼리 패턴 9개 | 전체 목록 + 인증 상태 전달 |
| firebase-analyst | Detector 인증 방식 확인 | Firebase Auth 관리자 계정 로그인 + admins/{uid} 확인 |

---

## 7. 미해결 이슈 Next Steps

### 즉시 수정 필요 (Critical)
1. **CROSS-01**: DispatchActivity.kt:172,275의 `"ON_TRIP"` → `"ASSIGNED"`로 변경
2. **CROSS-07**: DispatchActivity에 cancelDispatch() 함수 추가 (콜 상태 복원 + 기사 상태 WAITING 복구)
3. **NEW-08**: crashlytics-monitor.js의 `regionId` → `provinceId`, `associatedRegionId` → `associatedProvinceId`로 변경
4. **NEW-10**: firestore.rules에 device_status, device_alerts, emergency_alerts 컬렉션 보안 규칙 추가 (인증된 사용자 쓰기 허용)
5. **NEW-11**: DispatchActivity.updateCallWithDriver()를 Firestore transaction으로 변경. status == "WAITING" 체크 후 ASSIGNED 설정

### 중요 수정 (High)
6. **CROSS-05**: Manager 내장 Detector에 wasRinging 플래그 + ExcludeNumberManager 적용
7. **CROSS-06**: DispatchActivity의 콜 업데이트 + 기사 상태 업데이트를 WriteBatch로 묶기
8. **NEW-05 (CD-01)**: ~~CallDetectorService에서 RINGING 시 shared_calls 생성 제거~~ → **수정 완료** (sharedCallCreatedFromRinging 플래그로 이중 생성 방지)
9. **NEW-06**: DispatchActivity.createSharedCall()의 status를 "SHARED" → "OPEN"으로 통일
10. **NEW-07**: createCallWithDriver()에 fromCallDetector, isAppCustomer, customerId, createdFrom 필드 추가

### 개선 권장 (Medium)
11. **CROSS-02**: CallStatus enum 정리 - MATCHED/DISPATCHED/PENDING 삭제, ASSIGNED 추가
12. **NEW-01**: updateCallWithDriver() 시그니처에 contactName 파라미터 추가
13. **NEW-03**: temp callId 사용 시 Firestore 복구 로직 추가 (네트워크 복구 시 temp 문서 삭제)
14. **NEW-04**: SharedPreferences 통합 (detector_config 하나로 통일)
15. **NEW-12**: LoginViewModel에서 EncryptedSharedPreferences 사용

---

## 8. 참고: Detector Firestore 작업 전체 목록

| 작업 | 컬렉션/경로 | 메서드 | 보안 규칙 |
|------|-----------|--------|----------|
| 콜 생성 | provinces/.../calls | add() | create: if true |
| 콜 업데이트 (배차) | provinces/.../calls/{id} | update() | isOfficeAdmin |
| 콜 삭제 | provinces/.../calls/{id} | delete() | isOfficeAdmin |
| 기사 목록 리스닝 | provinces/.../designated_drivers | addSnapshotListener | isOfficeAdmin |
| 기사 상태 업데이트 | provinces/.../designated_drivers/{id} | update() | isOfficeAdmin |
| 공유콜 생성 | shared_calls | add() | isAnyAdmin |
| 사무실 상태 조회 | provinces/.../offices/{id} | get() | read: if true |
| 고객정보 조회 | provinces/.../customerInfo/{phone} | get() | isAuthenticated |
| 크래시 상태 기록 | device_status/{deviceId} | set() | **규칙 없음 (거부됨)** |
| 크래시 알림 | device_alerts | add()/set() | **규칙 없음 (거부됨)** |
| 긴급 알림 | emergency_alerts/{id} | set() | **규칙 없음 (거부됨)** |
| FCM 발송 | Cloud Function 호출 | getHttpsCallable() | auth 체크 없음 |

---

## 9. 참고: Detector 인증 체인

```
LoginViewModel.kt:73 → auth.signInWithEmailAndPassword()
→ LoginViewModel.kt:90 → admins/{uid} 문서 확인
→ LoginViewModel.kt:93-95 → associatedProvinceId/cityId/officeId 읽기
→ MainActivity.kt:207-214 → SharedPreferences "detector_config"에 저장
→ CallDetectorService.kt:81 → "detector_config"에서 읽어 Firestore 경로 구성
→ DispatchActivity.kt → Intent extra로 전달받아 사용
```

보안 규칙 통과 체인:
- `isAuthenticated()`: Firebase Auth 로그인 → PASS
- `isAnyAdmin()`: top-level admins/{uid} 존재 → PASS
- `isOfficeAdmin()`: admins/{uid}.associatedProvinceId/cityId/officeId 일치 → PASS
