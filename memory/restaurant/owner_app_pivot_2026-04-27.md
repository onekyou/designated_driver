---
name: 업소용 앱 피벗 (손님앱 코드 → 식당업소앱)
description: 2026-04-27 결정. customer_app(Kotlin) + customer_app_flutter(Flutter) 둘 다 백업 후 식당업소앱으로 in-place 변환. Flutter 우선 + 맥미니 도착 후 Swift 교체. MVP = 단순호출(대리+택시) + 포인트 표시.
type: project
---

# 업소용 앱 피벗 — 손님앱 코드베이스 → 식당업소앱 (2026-04-27)

## 결정
손님앱 두 코드베이스(Kotlin/Flutter)를 모두 백업하고, in-place로 식당업소앱(=마스터 §10.2 업소용 앱)으로 변환한다.

**Why**:
- 식당앱은 귀속(QR Install Referrer) 메커니즘 불필요 → iOS App Clip 전략 자체가 무의미
- Flutter 한 코드베이스로 Android+iOS 양 플랫폼 즉시 커버 가능
- 처음부터 짜는 것보다 검증된 손님앱 인프라(Auth, Firestore 경로, FCM, UI 골조) 재사용이 빠름
- Kotlin은 운영 중이라 즉시 Android 필드 테스트 가능 (Flutter 빌드 대기 0)
- 손님앱 진화 트랙(T1→T최종)은 양평 자기추진 입증 후 백업본에서 부활

**How to apply**:
- 식당앱 작업 지시 시 본 문서를 1차 참조 (마스터 §10.2보다 우선)
- 손님앱 트랙 부활 시 백업본을 기점으로 재개

## 백업 + 변환 매트릭스

| 원본 | 백업 위치 | 변환 후 (in-place) |
|---|---|---|
| `customer_app/` (Kotlin Android, 운영 중, versionCode 12) | `customer_app_backup_2026-04-27/` | `customer_app/` 식당앱 Kotlin Android |
| `customer_app_flutter/` (Flutter, 보류) | `customer_app_flutter_backup_2026-04-27/` | `customer_app_flutter/` 식당앱 Flutter Android+iOS |

**개발 순서**:
1. **Flutter 우선** — 귀속 불필요, iOS 제약 0, 양 플랫폼 동시 커버
2. **Kotlin 동일 개념** — Android 빠른 필드 테스트 / 병행 운영 (운영 중 코드 활용)
3. **맥미니 도착 후** — Swift 네이티브로 iOS 교체 (Flutter iOS 부분 → Swift iOS)

## MVP 기능 (단순화 원칙)

**철학**: 호출버튼 1탭 후 더 이상 어떤 작업도 필요 없게.

| 화면 | 기능 |
|---|---|
| 호출 화면 (메인) | ① 대리 호출 1탭 ② 택시 호출 1탭 ③ 포인트 잔액 표시 |
| 설정 화면 (가입 시 1회) | 식당명, 전화번호, 위치(주소) |

**제외**: 손님앱 잔여 기능 전부 (이용내역·등급·푸시 설정·로그아웃·온보딩 등 — 사장님이 매일 보는 화면이 단순호출 1개여야 함)

## 택시 호출 진화 (마스터 §6.2 정합)

```
[v0]  콜센터(콜택시 사무실) 번호로 단순 전화 연결
      → 식당 위치 매핑된 회사 번호로 즉시 dial
      → API 연동 0, 협상 0, 비용 0
[v1]  콜택시 관제에 식당 주소·식당명 SMS/푸시 자동 전송
      → 1차 협력 콜택시 1곳 선정 (양평콜택시 vs 터미널택시부)
[v2]  자동 배차 + 차량/기사 정보 + 상태 알림
      → API 연동, 조합 또는 회사 협상 단계
```

## 포인트 표시 — MVP 필수 (영업 도구)

**Why**: 영업 멘트 핵심 = "포인트 쌓이면 사장님 본인 대리 무료, 단골 무료 대리 선물". 포인트가 화면에 안 보이면 영업 무기로 약함.

**How to apply**:
- 호출 화면에 포인트 잔액 표시 (마스터 §3.5 환금성 0 구조 — 본인 대리 / 단골 선물만)
- 사용처 흐름(본인 대리 / 단골 선물 선택 UI)은 v1+ 추가. MVP는 잔액 표시만

## SUPERSEDED 처리 (임시)

본 결정으로 다음 문서·트랙은 임시 무효:

- `memory/designated_drive/ios_phase6_paused/plan_ios_customer_app.md` (Swift+App Clip 손님앱 전략) — **임시 SUPERSEDED**
  - 이유: 식당앱은 App Clip 불필요. 손님앱 트랙 부활 시 다시 활성화 가능 → 문서 보존
- 마스터 §3.6 손님앱 진화 단계 (T0→T최종) — **현 시점 보류**, 백업본 기점으로 부활 가능

## 다음 액션

코드 변경 작업은 별도 플랜 + 사용자 허락 필요. 현재는 메모리 결정만 기록.

- [ ] 백업 명령 실행 허락 받기 (`cp -r customer_app customer_app_backup_2026-04-27/` 등)
- [ ] 변환 작업 플랜 별도 작성 (코드 변경 범위 — 가입 흐름, UI, Firestore 경로, FCM payload, 권한 등)
- [ ] Firestore 식당 데이터 모델 결정 (`offices/{o}/restaurants/{rid}` ?, 포인트 잔액 위치 등)
