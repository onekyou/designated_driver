---
name: GUI 디버깅 추측 fix 금지
description: 화면 렌더링/레이아웃 버그 fix 시 추측으로 코드 수정 시도 반복 금지. 측정 + 공식 reference 확인 후 진행
type: feedback
---

GUI 렌더링/레이아웃 버그 (BottomSheet, IME, padding, inset 등)를 디버깅할 때 **추측 기반 fix를 반복 시도하지 말 것**.

**Why**: 2026-04-28 세션에서 ChatBottomSheetContent IME/nav bar 처리 문제를 5회 이상 추측 fix 반복 (navigationBarsPadding → imePadding → union → exclude 등). 매번 사용자에게 디바이스 검증 요청 → 실패 → 다른 추측. 사용자가 "즉흥적 수정 그만하고 정확한 원인부터 파악하라" 명시 지시.

**How to apply**:
1. **측정 먼저**: 화면에 디버그 텍스트 또는 logcat에 `WindowInsets.ime / navigationBars / systemBars` 등 inset 값을 출력하는 디버그 코드 삽입 → 디바이스에서 IME ON/OFF 등 각 상태 측정
2. **공식 reference 확인**: 공식 sample (Jetchat 등) 또는 공식 docs를 직접 fetch해 정석 패턴 확인. 추측으로 알려진 패턴 적용 금지
3. **두 번째 fix가 실패하면 즉시 plan 모드 진입**: 측정 + reference fetch + 좌표계 분석 후 plan 파일에 정합성 검증 → 사용자 승인 후 적용
4. **알려진 함정**: Compose의 `WindowInsets.ime` 와 `navigationBars`는 좌표계 동작이 미묘. `union` / `exclude` / chain consume 메커니즘은 모두 다른 결과. Material 3 `BottomSheetScaffold`는 sheet content에 IME inset 자동 적용 안 함 (2024.04 BOM 기준). 이런 fact는 추측 X, 측정 + reference로 확인 ✓
