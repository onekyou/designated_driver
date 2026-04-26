---
name: iOS 손님앱 전략
description: Swift + App Clip으로 iOS 손님앱 구현 — 귀속 문제 해결, 순수 대리운전 기능만. 2026-04-27 임시 SUPERSEDED (손님앱 코드가 식당업소앱으로 변환됨).
type: project
---

> ⚠️ **2026-04-27 임시 SUPERSEDED**: 손님앱 코드베이스가 식당업소앱으로 변환됨 (Flutter 우선 → 맥미니 후 Swift). 식당앱은 귀속 메커니즘 불필요해 App Clip 전략 자체가 무의미. 손님앱 트랙 부활 시 본 전략 재활성화 가능 → 문서 보존. 상세: `memory/restaurant/owner_app_pivot_2026-04-27.md`.

# iOS 손님앱: Swift + App Clip (2026-03-27 확정)

## 결정 배경
- iOS에 Install Referrer 없음 → 핑거프린트(기각), 클립보드(제한적), QR 재스캔(대안), PWA(푸시 불안정)
- App Clip = "설치 없이 귀속" → 문제 자체를 우회
- 손님앱은 단순(콜 요청+프로필+상태확인)하므로 Flutter 불필요 → 플랫폼별 네이티브가 효율적

**Why:** 기사앱과 달리 손님앱은 단순하고, App Clip이 iOS 귀속 문제를 근본적으로 해결.
Flutter로 먼저 만들고 나중에 App Clip 추가하면 두 번 작업이므로 Swift로 한 번에 진행.

**How to apply:** iOS 손님앱 관련 작업 시 이 전략 참조. Flutter 전환 제안 금지.

## 최종 아키텍처 (3/27 최종 확정 — Flutter 전략 철회)
| 앱 | Android | iOS | 비고 |
|---|---|---|---|
| 기사앱 | Kotlin (기존 유지) | Swift (신규) | Flutter 아닌 네이티브 |
| 손님앱 | Kotlin (기존 유지) | Swift + App Clip (신규) | App Clip으로 귀속 해결 |

**Why:** 검증된 Kotlin을 버리고 Flutter로 가는 것은 90% Android 기사를 리스크에 노출. 언어가 아닌 로직이 핵심.

## 기능 범위
- 포함: 콜 요청, 실시간 상태, 프로필, 이용내역, 포인트/등급, 푸시
- 제외: 만보기, 술먹기 게임, 기타 부가기능

## 핵심 기술 포인트
- App Clip → 풀앱: App Group (`group.com.designated.customer`)으로 데이터 공유
- Firebase Auth 토큰 이관: Custom Token CF 또는 Anonymous Auth UID 공유
- QR URL 기존 구조 유지 + AASA 파일로 App Clip 매핑

## 타임라인
- Android 3대 마일스톤과 병렬 진행
- 클라우드 환경에서 개발

## 선행 조건
- Apple Developer 계정 ($99/year)
- Xcode + Mac
- APNs 인증서
- Associated Domains (AASA)
