# 이슈 수정 이력 + 시뮬레이션 결과

## 수정 이력 요약 (59건 중 55건 완료)

### 주요 커밋
| 커밋 | 내용 |
|------|------|
| 58a23647 | CROSS-01, CROSS-07일부, Driver취소수신, CD-01 |
| 442a4c4e~6cbd203d | Phase 1~5 수정 (STL, NEW, BUG, CUST 다수) |
| e08e9f72 | 3~5차 시뮬 21건 블라인드+검증 전건 수정 |
| 339af2e6 | 7차 교차검증 7건 (CANCELLED_BY_CUSTOMER 도입, 타임아웃 FCM) |
| 79740b69 | 8차 실전 시뮬 10건 (중복콜방지, 기사복구, FCM통일, 고객앱복구) |
| 12116b80 | 10차 스트레스 테스트 2건 (더블탭 방지, cancelCall 트랜잭션화) |
| ddc2c2e3 | 12차 다중사무실 4건 (공유콜 target필드, depositRatio 동적조회) |
| fdd01be2 | 수동 새콜 중복체크 + 공유콜 재공유 차단 |
| 56d71830 | 기사승인 UX + RTDB 인증 해결 (IAM 역할 추가) + 지역필터 |
| 9cc1cd8b | 내부호출 팝업 통합 + fromCallManager Room DB 컬럼 |
| c8bc3955 | 이중팝업 수정 + 시간필터 세분화 + TTL 30일 자동삭제 |
| 232e4f73 | 업무마감↔로그아웃 분리 + 매니저 거절/재제출 기능 추가 |

### 오탐/해소 (7건)
| 이슈 | 사유 |
|------|------|
| CROSS-02 | Detector enum 데드코드만 |
| STL-04 | CF 합계 로직 정상 |
| STL-06 | 기사별/사무실별 경로 의도적 분리 |
| STL-07 | CONFIRMED 후 새 세션은 정상 동작 |
| CUST-07 | 토큰 자동 재시도로 해소 |
| R1 | 3대 Detector 중복 → 오탐 (각각 다른 번호, 1대만 울림) |
| FCM priority | oncallassigned priority:"high" 정상 설정 확인 |

## 시뮬레이션 검증 이력

| 차수 | 유형 | 결과 |
|------|------|------|
| 1~6차 | 코드 로직 (단일사무실) | 이슈 발견 → 전건 수정 |
| 7차 | 교차검증 (팀원 5명 메시지 교환) | 7건 발견 → 전건 수정 |
| 8차 | 실전 시뮬 | 10건 발견 → 전건 수정 |
| 10차 | 스트레스 (350콜) | 2건 발견 → 전건 수정 |
| 11차 | 검증 (150콜) | 0건, 수정 확인 |
| 12차 | 다중사무실 | 4건 발견 → 전건 수정 |
| 13차 | 7일 블라인드 (206콜, 3사무실, 6기사) | **0건**, 10대 시나리오 전체 PASS |
| 14차 | 실기기 환경 (8건 코드경로) | **0건**, 8건 전체 PASS |

## CF 배포 상태
- 마지막 배포: 3/12 전체 재배포 완료 (40개 함수, 스케줄러 1개 삭제)
- `checkSettlementDiscrepanciesScheduled` 삭제됨 (불필요한 비용 방지)

---

## 날짜별 작업 상세 (MEMORY.md에서 이관)

### 2026-03-24 — 손님앱/기사앱 콜 취소 개선
- **손님앱 콜 취소 확장**: CallService cancelCall()에 ACCEPTED/PREPARING 허용, HomeScreen 취소 버튼 DRIVER_ARRIVING까지 표시
- **기사앱 cancelTrip() HOLD→CANCELLED_BY_DRIVER**: 대면 취소 시 확정 취소, 포인트 환불, 손님 FCM 전송
- **3개 앱 취소 알림**: 손님앱 showCallCancelledNotification, 콜매니저 STATUS_CHANGE_CHANNEL, 기사앱 항상 알림 표시
- **기사앱 취소 UX 개선**: handleCallCancelled에 errorMessage(Snackbar) 추가, handleNotificationCallId에서 취소 콜 홈 이동
- **손님앱 익명인증 복원**: MainActivity LaunchedEffect에서 signInAnonymously() 자동 수행, phoneNumber 체크 제거
- **Firestore 보안 규칙 2건**: 고객 CANCELLED_BY_CUSTOMER 허용 확장 + 기사 CANCELLED_BY_DRIVER 허용
- **CF onCallCancelledByDriver**: 기사 FCM에 title/body 추가 + 매니저 FCM 전송 추가
- **손님앱 테스트 환경**: ADB SharedPreferences 주입으로 QR 없이 debug 설치 가능 (서명 불일치 해결)

### 2026-03-18 — 기사앱 UX + CF 보정
- **기사앱 네트워크 끊김 배너**: PresenceManager `.info/connected` → isConnected StateFlow → HomeScreen 빨간 배너
- **기사앱 배차 알림음**: LockScreenActivity 3초 간격 반복 (코루틴), NewCallPopup은 1회 (포그라운드는 수락 버튼이 바로 보이므로)
- **기사앱 LockScreenActivity 재디자인**: 이모지 삭제, 거절/수락 → 확인 버튼, 앱 컬러(검정+골드)
- **기사앱 VIBRATE 권한 추가**: AndroidManifest.xml (기존 누락 버그)
- **콜매니저 기사탭 중복 제거**: FCM에 lastLoginTime 추가 → Room DB 직접 반영 → refreshDriverData() 전체 재로드 제거
- **콜매니저 죽은 코드 삭제**: 미사용 로그인/로그아웃 인앱 팝업 인프라 제거
- **CF #1 offline 즉시 복귀 → 타임아웃 적용**: `presenceStatus === "offline"` → `presenceStatus === "offline" && isTimedOut` (1분 유예로 FCM 도달 기회 보장)
- **CF oncallassigned 이모지 삭제**: FCM title에서 🚨 제거
- **배포 완료**: `onDriverStatusChange`, `checkAssignedTimeout`, `oncallassigned`

### 2026-03-17 — Presence 시스템 전환
- **Realtime DB 리스너 방식 시도 → 롤백**: 오감지, 2~4분 지연, onDisconnect 덮어쓰기 → 폐기
- **CF 스케줄러 presence 보호 구현**:
  - #1 ASSIGNED + offline + 타임아웃 → WAITING 복귀 + 관리자 FCM (3/18 수정: 즉시→타임아웃)
  - #2 ASSIGNED + online + 타임아웃 → WAITING 복귀 + 관리자 FCM
  - #3 ACCEPTED/PREPARING + offline → 관리자 FCM
  - #4 IN_PROGRESS + 10분 연속 offline → firstOfflineAt 기록 → 관리자 FCM
