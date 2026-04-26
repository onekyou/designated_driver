---
name: 자동로그인 구현 완료
description: 콜매니저+기사앱 자동로그인 패턴 (체크→로그아웃=앱종료→재실행시 자동로그인) - 구현 완료
type: project
---

# 자동로그인 구현 (콜매니저 + 기사앱) — 완료 (2026-03-13)

## 패턴
자동로그인 체크 → 로그아웃=앱종료(finishAffinity) → 재실행 시 자동로그인

## 콜매니저 (8d4f8aaa)
- `toggleAutoLogin()`: SharedPreferences에 즉시 저장
- 로그아웃: 종료 확인 다이얼로그 + signOut + finishAffinity (auto_login 유지)
- 업무마감: 로그아웃 로직 제거, 마감 후 대시보드로 복귀
- AllTripsScreen에 onHome 콜백 전달

## 기사앱 (3bbbce7b)
- `SecurePreferencesManager.setAutoLoginEnabled()` 추가
- `LoginViewModel.init()`: autoLogin+자격증명 있으면 `login()` 자동 호출
- `toggleAutoLogin()`: SecurePreferences에 즉시 저장
- `logout()`: `clearAutoLoginCredentials()` 제거 (자격증명 유지)
- HomeScreen 3곳 (로그아웃 버튼/확인 다이얼로그/업무마감 다이얼로그): navController.navigate 제거, signOut+finishAffinity로 통일
- `performSignOut()`: driver_login_prefs 체크 제거, 단순화

## 미구현 잔여 항목
- 정산 알림 딥링크 (퇴근 후 정산 알림 클릭 → 자동 로그인 → 정산 화면 이동)
- LoginScreen Loading 시 로딩 인디케이터 표시 (폼 숨김)

**Why:** 앱 재시작 시 자동로그인이 안 되던 문제 해결. 근본 원인은 toggleAutoLogin이 메모리만 변경하고, logout 시 자격증명을 삭제하던 것.
**How to apply:** 향후 로그아웃 경로 추가 시 반드시 signOut+finishAffinity 패턴 사용, navController.navigate(LOGIN) 하지 않을 것.
