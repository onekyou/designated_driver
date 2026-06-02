---
name: adb-monkey-changes-autorotate
description: adb monkey 런치 도구가 시스템 자동회전을 강제로 켬 — 단말 테스트 시 am start 사용
metadata:
  type: feedback
---

# adb `monkey`는 시스템 자동회전(accelerometer_rotation)을 켠다 — 테스트 함정 (2026-06-02)

**증상**: PTT 콜드스타트 E2E 중, 원규씨가 끈 시스템 자동회전이 "빌드 후마다 다시 켜짐". 빌드/설치/앱 의심.

**진범 = `adb shell monkey`**. 통제 실험으로 격리:
- `settings put system accelerometer_rotation 0` → 0
- `adb install -r` 만 → **0 유지** (설치 무관)
- `am start -n pkg/Activity` 로 런치 → **0 유지** (정상 런치 무관)
- `monkey -p pkg -c LAUNCHER 1` 로 런치 → **1로 변함** (`SettingsProvider: PUT_ret(/system/accelerometer_rotation)` 로그 확인)

**원인**: 안드로이드 `monkey` 테스트 도구는 *테스트 일관성을 위해 시작 시 자동회전을 강제로 켠다*(부작용). 권한과 무관(monkey는 shell 권한).

**How to apply**:
- 단말에서 앱 재실행 시 **`monkey` 대신 `adb shell am start -n <pkg>/<Activity>`** 사용. monkey는 시스템 회전 설정을 오염시킨다.
- 사용자가 끈 시스템 설정이 "테스트 중 다시 켜진다"면 *내 테스트 명령*을 먼저 의심. 앱은 WRITE_SETTINGS 없으면 시스템 설정 변경 불가.
- 검증은 통제 실험(before값 → 단일 명령 → after값)으로 범인을 한 단계씩 격리.

**Why**: 사용자에게 "앱/빌드 문제 아님"이라 단언했다가 통제 실험에서 0→1 변화로 반증됨 → 단언 전 실측. 그리고 단언이 틀렸을 때 격리 실험으로 진짜 원인(monkey)까지 추적. [[principle_user_action_minimum]]

관련: [[ptt_implementation_2026-06-02]]
