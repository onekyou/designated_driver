---
name: 손님앱 ADB 설치 금지
description: 손님앱은 QR 귀속 온보딩이 필수이므로 ADB install로 설치하면 작동하지 않음
type: feedback
---

손님앱(customer_app)은 ADB install로 설치하면 안 된다.
QR → Play Store → Install Referrer 흐름으로 사무실 귀속 데이터가 설정되어야 정상 작동한다.

**Why:** ADB install은 Install Referrer 데이터를 제공하지 않으므로 사무실 연결이 안 됨. 사용자가 매번 QR을 다시 찍어야 하는 불편함 발생.

**How to apply:** 손님앱 업데이트 시 서명키 일치 여부를 먼저 확인하여 `adb install -r`(데이터 유지 덮어쓰기)가 가능한지 확인. 서명 불일치면 해결 방안을 먼저 마련한 후 설치. 절대 무작정 uninstall + install 하지 말 것.
