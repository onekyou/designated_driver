---
name: user_ios_experience
description: 사용자는 iOS/App Store 생태계 경험이 없음. Android/Play Store는 익숙. iOS 관련 설명은 기초부터 + Android 비유 활용
type: user
---

사용자는 iOS/App Store 출시 경험이 전무하다. Android (Play Store, Firebase) 생태계는 숙련.

## 설명 방침
- Apple Developer Program, App Store Connect, TestFlight, Bundle ID, Provisioning Profile, Code Signing 등 iOS 용어는 **처음 등장 시 반드시 설명**
- 가능한 **Android 대응 개념과 대비**해서 이해 돕기 (예: Bundle ID ≈ applicationId, TestFlight ≈ Play Console 내부 테스트, Provisioning Profile ≈ Play App Signing)
- 기술 용어를 쓸 때는 "무엇을/왜/어디서" 3요소로
- Codemagic 같은 제3자 서비스는 "왜 필요한가" (Mac 없이 iOS 빌드 불가 해결)부터

## 이미 한 것 (2026-04-17)
- Apple Developer Program 연회원 가입 완료 ($99/년)
- Apple Developer Portal에서 App ID 등록 완료 (`com.designated.driverapp.app`)
- App Store Connect 앱 생성 완료 (이름: `콜마당 기사`)
- Codemagic 계정 생성 완료

## 아직 안 한 것
- Codemagic GitHub 연동 상태 확인
- `codemagic.yaml` 작성
- App Store Connect API Key 발급 (Codemagic 인증용)
- 앱 메타데이터 입력 (아이콘/스크린샷/설명/개인정보 URL)
- 기사 테스터 Apple ID 수집 (5~10명)
- Flutter iOS Runner.xcodeproj의 PRODUCT_BUNDLE_IDENTIFIER 변경 (`com.designated.driverAppFlutter` → `com.designated.driverapp.app`)

## 아직 없는 것
- Bundle ID 확정, 앱 메타데이터, 스크린샷, 개인정보 정책 URL, 테스터 Apple ID
