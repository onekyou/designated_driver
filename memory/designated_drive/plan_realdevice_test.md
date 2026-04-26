---
name: 실기기 테스트 체크리스트
description: 스크립트로 불가능한 실기기 전용 테스트 항목 — BootReceiver, FCM 백그라운드, 앱 킬→복구
type: project
---

# 실기기 테스트 체크리스트 (2026-03-17)

## 전제 조건
- driver_app 최신 빌드 설치 완료 (f5ab8ddc, 3대)
- Firestore 테스트 데이터 정리 완료
- 기사 2명 (양세훈, 고양이) 로그인 상태

---

## 1. BootReceiver 테스트

### 1a. 로그인 상태 → 재부팅 → 서비스 자동 시작
- [ ] 기사앱 로그인 + 대기 화면 확인
- [ ] 폰 재부팅
- [ ] 앱 아이콘 안 누르고 대기
- [ ] logcat 확인: `BootReceiver` 태그 → "로그인 상태 — DriverForegroundService 시작"
- [ ] 알림바에 "대리운전 기사앱 서비스 실행 중" 표시 확인
- [ ] 스크립트로 배차 생성 → FCM 수신 확인 (앱 안 열어도)

### 1b. 로그아웃 상태 → 재부팅 → 아무것도 안 함
- [ ] 기사앱 로그아웃 → 앱 종료
- [ ] 폰 재부팅
- [ ] logcat 확인: `BootReceiver` 태그 → "로그아웃 상태 — 서비스 시작 안 함"
- [ ] 알림바에 서비스 없음 확인

### logcat 명령어
```bash
ADB="$ANDROID_HOME/platform-tools/adb"
$ADB -s R3CT80K78NP logcat -s BootReceiver,DriverForegroundService
```

---

## 2. 배차확인 버튼 테스트

### 2a. 버튼 항상 활성화 확인
- [ ] 기사앱 대기 화면 → "배차 확인" 버튼이 항상 클릭 가능
- [ ] 배차 없는 상태에서 클릭 → "현재 진행 중인 콜이 없습니다" 메시지

### 2b. FCM 유실 시 수동 복구
- [ ] 스크립트로 콜 생성 + 기사에게 ASSIGNED (FCM 무시)
- [ ] 기사앱에서 "배차 확인" 클릭
- [ ] 배차 수락 팝업 표시 확인

### 2c. 운행 중 화면 복구
- [ ] 기사앱에서 콜 수락 → IN_PROGRESS 상태
- [ ] 앱 강제종료 (최근 앱에서 스와이프)
- [ ] 앱 재실행 → 자동으로 운행 중 화면 복구 확인
- [ ] 만약 대기 화면이면 → "배차 확인" 클릭 → 운행 중 화면 이동 확인

---

## 3. FCM 백그라운드 수신

### 3a. 화면 꺼진 상태
- [ ] 기사앱 로그인 + ONLINE 상태
- [ ] 폰 화면 끄기 (전원 버튼)
- [ ] 스크립트로 콜 생성 + 배차
- [ ] 화면 자동 켜짐 + LockScreenActivity 팝업 확인
- [ ] 알림음 + 진동 확인

### 3b. 앱 백그라운드 (다른 앱 사용 중)
- [ ] 기사앱 홈 버튼 → 다른 앱 사용
- [ ] 스크립트로 콜 생성 + 배차
- [ ] 상단 알림 또는 FullScreenIntent 팝업 확인

### 배차 생성 스크립트 (수동 테스트용)
```bash
cd functions
# 양세훈에게 배차
node -e "
const {createAndCompleteCall} = require('./scripts/sequential-flow-test.js');
// 또는 firestore-util.js로 직접 콜 생성 + ASSIGNED 업데이트
"
```
※ 실제로는 콜매니저 앱에서 직접 배차하는 게 가장 정확함

---

## 4. 앱 킬 → 복구

### 4a. 운행 중 앱 강제종료
- [ ] 콜 수락 → IN_PROGRESS
- [ ] 최근 앱에서 기사앱 스와이프 (강제종료)
- [ ] 앱 아이콘 클릭 → auto-login → 운행 중 화면 복구

### 4b. 정산 입력 중 앱 강제종료
- [ ] 운행 완료 → AWAITING_SETTLEMENT
- [ ] 앱 강제종료
- [ ] 앱 재실행 → 정산 입력 화면 복구

### 4c. 배터리 방전 시뮬레이션
- [ ] IN_PROGRESS 상태에서 폰 전원 끄기
- [ ] 전원 켜기 → BootReceiver 작동 확인
- [ ] 앱 열기 → 운행 중 화면 복구

---

## 테스트 기기

| 테스트 | 기기 | 이유 |
|--------|------|------|
| BootReceiver | Z Flip4 (R3CT80K78NP) | driver_app 전용 |
| FCM 백그라운드 | S22 (R5CT41TJZFP) | detector + driver 함께 테스트 |
| 앱 킬→복구 | S21+ (R3CR312MB1L) | manager에서 배차 → driver에서 확인 |

---

## 완료 후
- [ ] 전체 PASS → 메모리 업데이트
- [ ] 실패 항목 → 코드 수정 → 재테스트
