# 볼륨버튼 PTT 원칙 — 실제 무전기 UX 동등성

**확정**: 2026-05-25
**관련 plan**: `C:\Users\kala1\.claude\plans\harmonic-sparking-hedgehog.md`
**관련 메모**: [[ptt_market_replacement_card]], [[ptt_operation_scenario]], [[ptt_pivot_other_tracks_2026-05-25]]
**상위 원칙**: [[principle_user_action_minimum]]

## 한 줄

볼륨다운 한 번에 *화면 꺼진 상태에서도 즉시 발화*. 이게 PTT 도입의 진짜 목표 — 실제 무전기와 *이질감 0*.

## 왜 시중 PTT 앱(Zello 등)으로 시범 불가

- 화면 켜기 + 앱 열기 + PTT 버튼 = 3단계 마찰
- 사용자 행동 최소 원칙 정면 위반 ([[principle_user_action_minimum]])
- 운영체제 레벨 키 가로채기 + Accessibility 권한 + Foreground Service 조합은 *native 코드로만 가능*
- 본인 5/25 발화: "Zello는 물리 버튼까지 도달이 어려운 걸로 아는데... 내가 화면 꺼짐에서도 다운볼륨으로 바로 활성화시킨 건 최대한 실재 무전기를 구현하기 위함이야. 이질감 없이. 다른 무전 앱은 상당히 번거로워서"

## 2025-08 도달 자리 (commit `6ae77f74`)

- `dispatchKeyEvent`로 VOLUME_DOWN/UP 시스템 볼륨 변경까지 완전 차단
- 화면 꺼진 상태에서 무전 트리거 작동
- MainActivity.kt +568/-68 (대폭 재작성)
- 본인이 5개월간 도달한 자리 = *시장에 없는 자리*

추가 참고 commit:
- `95cbdb08` (2025-08-18) PTTManager 서비스 통합 +1054줄
- `17bcd8b3` (2025-08-21) BackgroundPTTService + VolumeKeyHandler + AccessibilityPermissionHelper +1356줄
- `c08e9002` (2025-09-04) BeepSoundManager + PTTLockManager Phase 6 안정화 +9476줄

**현재 상태**: `e5685e66` (2026-01-23)에서 5,419줄 공식 제거. 현재 잔존은 `ppt_start.m4a` 효과음 + 알림 채널 사운드 참조만.

## 원칙 (재구현 시 타협 불가)

- 볼륨버튼 hook은 *타협 불가* 핵심
- 화면 켜는 PTT는 콜마당 패러다임 위반
- 신규 구현이 깔끔 (5개월 경과 + RTM 채널 자동 참여 미완성 + Trigger Join 모델은 별개)

## 차별점

시장 어떤 PTT 앱도 *볼륨버튼 + 화면 꺼짐 + 즉시 발화* 자리를 구현 안 함. 콜마당 native 자체 구현만이 *실제 무전기 동등성* 도달. 영업 차별점 핵심 ([[ptt_market_replacement_card]] 참조).
