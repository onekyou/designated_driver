---
name: 손님앱 코드 → 식당업소앱 전환 (보류 트랙 갱신)
description: 2026-04-27 결정. customer_app(Kotlin)/customer_app_flutter(Flutter) 둘 다 백업 후 식당업소앱으로 변환. 손님앱 자체 트랙은 양평 자기추진 입증 후 백업본 기점으로 부활.
type: project
---

# 손님앱 코드베이스 → 식당업소앱 전환 (2026-04-27)

## 손님앱 트랙 현재 상태

손님앱 두 코드베이스가 식당업소앱으로 in-place 변환됨. 진화 트랙(T1→T최종)은 보류.

**Why**: 마스터 §13.3 디버깅 우선순위 1~5에서 식당 영업이 1순위. 식당업소앱이 N=1 무기에 직접 의존. 손님앱은 T0 상태로 충분히 양평 파일럿 가능 (Kotlin 운영 중).

**How to apply**: 손님앱 트랙 부활 결정 시 본 문서 + 백업 폴더(`customer_app_backup_2026-04-27/`, `customer_app_flutter_backup_2026-04-27/`)를 기점으로 재개.

## 보류 사유

- 양평 자기추진 입증 전 손님앱 진화에 자원 분산 비효율
- 식당업소앱이 마스터 N=1 무기 직접 의존 (마스터 §4.3 비대칭 임계점)
- 식당앱이 손님앱 인프라 재사용으로 가장 빠른 출시 경로

## 부활 트리거 (조건)

다음 중 하나 충족 시 손님앱 트랙 부활 검토:

- 양평 식당 누름 빈도 주 3회 이상 (마스터 R3 모델 정합 분기)
- N=10 사건 누적 (마스터 R2 영업 무기 정합)
- T1 진입 결정 (식당 거점 + 포인트 + 단골 무료 대리 손님앱 측 기능)

## 백업 위치 (예정)

- `customer_app_backup_2026-04-27/` — Kotlin Android 손님앱 (운영 중 시점 스냅샷)
- `customer_app_flutter_backup_2026-04-27/` — Flutter 손님앱 (보류 시점 스냅샷)

## 관련 문서

- 변환 결정 본체: `memory/restaurant/owner_app_pivot_2026-04-27.md`
- 마스터: `memory/callmadang_master_2026-04-27.md` §3.6 (손님앱 진화), §10.2 (업소용 앱)
- 임시 SUPERSEDED iOS 손님앱 전략: `memory/designated_drive/ios_phase6_paused/plan_ios_customer_app.md`
