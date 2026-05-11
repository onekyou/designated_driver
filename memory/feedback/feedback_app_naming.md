---
name: 앱 이름 — "기사앱" vs "픽업앱" 구분
description: 사용자 발화에서 "기사앱"은 driver_app(대리기사앱), "픽업앱"은 pickup_driver_app. 혼동하지 말 것.
type: feedback
---

## 규칙

사용자 발화에서:
- **"기사앱"** = `driver_app` (대리기사앱)
- **"픽업앱"** = `pickup_driver_app` (픽업기사앱)
- **"콜매니저"** = `call_manager`
- **"손님앱"** = `customer_app` 또는 `customer_app_flutter`
- **"업소용 앱"** = (개발 예정, restaurant 도메인)

## Why

2026-04-28 Step 6 chat 이식 시 사용자가 "기사앱 이식 준비"라고 명시했는데 클코가 pickup_driver_app(픽업앱)에 13개 파일 이식 후 사용자 경고 — "기사앱 아니야? 픽업앱이 아니고". driver_app과 pickup_driver_app 모두 "기사" 단어 들어가지만 한국어 일상 용법상 "기사" = 대리기사 default.

## How to apply

1. 사용자가 "기사앱" 발화 시 default = `driver_app`. pickup_driver_app은 명시적으로 "픽업기사" 또는 "픽업앱"으로 표현됨.
2. 두 앱 모두 작업 대상이면 둘 다 명시 ("driver_app + pickup_driver_app 모두 이식").
3. 모호하면 AskUserQuestion으로 명확화. 추측으로 진행 금지.
4. 메모리 + 코드의 영문 표현 (`driver_app` / `pickup_driver_app`)이 더 명확 — 한국어 발화 → 영문 식별자 매핑은 클코가 책임.
