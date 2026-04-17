---
name: customer_app 인증 방식 분석
description: 손님앱 Anonymous Auth(익명인증) 사용 확정 + QR 전용 온보딩 (2026-03-24 수정)
type: project
---

## 인증 방식: Anonymous Auth (익명인증)
- 커밋 `1fff08cd` (2025-10-26)에서 익명인증으로 전환
- 앱 시작 시 자동 로그인 (사용자 조작 불필요)
- PhoneAuthViewModel.kt는 잔존하지만 메인 인증은 Anonymous Auth
- phoneNumber는 프로필 설정에서 수집 (SMS 인증 아님)

**Why:** 이전 분석에서 Phone Auth 유지로 결론냈으나, 실제 코드는 익명인증으로 구현됨. 기기 로그에서 `AnonymousAuth: 이미 로그인됨` 확인.

## QR 전용 온보딩 확정
- 개인 사무실 귀속형 앱 → QR 전용이 올바른 설계
- OfficeSelectionScreen 제거 완료 (345d9e6e)
- QR 없이 설치 시 → QR_REQUIRED 안내 화면

## 테스트 환경 구성 방법
- Play Store 버전(release 서명) ≠ debug 빌드 → 덮어쓰기 불가
- ADB install 후 SharedPreferences 수동 주입으로 해결:
  - `customer_app_prefs.xml`에 province_id, city_id, office_id 주입
  - 앱 재시작 후 약관동의 → 프로필설정 진행
