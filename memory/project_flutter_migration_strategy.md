---
name: Flutter 기사앱 전환 전략 + 제작 계획서
description: 기사앱 Kotlin→Flutter 단계적 전환. v3.1 최종 확정 (2026-03-27). 6 Phase, ~6,020줄, 36개 파일 삭제, 최소 레이어 아키텍처.
type: project
---

## 전략 (2026-03-27 확정)

1단계: Kotlin 기사앱으로 첫 사무실 출시 (iOS 기사 없음)
2단계: Flutter 기사앱 완성 (파일럿 병행)
3단계: Flutter 검증 후 Kotlin 단종, Flutter 통합

**Why:** iOS 기사 1명이라도 있으면 정산 시스템 전체가 깨지고 배차도 아날로그/앱 이중 관리 혼선 → iOS 지원 필수. 기사앱은 54파일/12화면의 복잡한 앱으로 두 코드베이스 분리 유지 비현실적.

## 제작 계획서 v3.1 (최종)

**계획서 파일**: `C:\Users\kala1\.claude\plans\validated-pondering-teapot.md`

### 아키텍처
- 콜/기사/운행: Notifier → Firestore 직접 (2단, Kotlin과 동일)
- 정산/인증: Notifier → Repository (3단, 오프라인 로직 있음)
- UseCase 레이어 전체 삭제 (12개 보일러플레이트)
- GetIt → Riverpod 단일 DI
- Either/dartz → try-catch

### Phase 요약
| Phase | 내용 | 규모 |
|-------|------|------|
| 1 | 구조 정리 + 워크플로우 + Firebase 설정 | ~2,300줄 + 36파일 삭제 |
| 2 | 정산 시스템 (sqflite + Firestore) | ~1,900줄 |
| 3 | 서비스 + 네이티브 (잠금화면, Presence, 카카오) | ~700줄 |
| 4 | FCM 완성 + 딥링크 | ~370줄 |
| 5 | 나머지 화면 (회원가입, QR 등) | ~750줄 |
| 6 | Kotlin→Flutter 전환 준비 (패키지명, 토큰, iOS 배포) | 설정/문서 |

### 핵심 발견사항
- `google-services.json` 누락 → Android 빌드 불가
- iOS `GoogleService-Info.plist` Bundle ID 불일치
- Firestore 경로 `regions/offices` → `provinces/cities/offices` 전면 수정 필요
- 기존 acceptCall()이 트랜잭션 미사용 → 데이터 무결성 위험

### 패키지명
- Kotlin: `com.designated.driverapp.app` (내부테스트 등록)
- Flutter: `com.designated.driverapp` (미등록)

### How to apply
- Phase 1부터 순차 진행, Phase 2+3은 병행 가능
- 정산 계산은 Kotlin 단위 테스트 1:1 포팅 후 검증 필수
- 잠금화면은 네이티브 LockScreenActivity 유지 (fullScreenIntent 신뢰성 문제)
