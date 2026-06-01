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

## ★ 과거 PTT 실패의 정확한 정체 (2026-06-01 본인 확정)

본인 발화 (2026-06-01): "완전 꺼짐에서 볼륨다운버튼을 두번 클릭시 바로 PTT 전환까지 성공. 하지만 상대방이 꺼져있는 상태가 문제 되었음."

- ✅ **송신측 해결됨**: 완전 화면 꺼짐(블랙아웃)에서 볼륨다운 *2회* → PTT 송신 전환 성공. 키 캡처 + Foreground Service + 채널 join 작동. = 시장에 없는 자리, 큰 자산.
- ❌ **수신측 미해결 (진짜 난제)**: 상대방 단말이 꺼져 있으면(Doze/화면 꺼짐) 음성 수신·재생 안 됨. git 이력의 "Not connected"(`23a975b4`)가 바로 이 자리.

### 기술적 본질 + 해결 방향 (본인 교정 2026-06-01)
- 무전기 = 수신기 항상 ON. 스마트폰 Android **Doze 모드**가 화면 꺼짐 시 네트워크·백그라운드 오디오 억제.
- ★ **본인 교정**: 상시 채널 연결(always-join)은 콜마당 *listener 최소화 철학* 위반. 해법은 콜마당 전체 패러다임과 *동일* — **발화 시작 → high-priority FCM Data Push로 수신측 wake → RTC join → 음성** ([[ptt_operation_scenario]] §Data Push 패러다임 정합). "알림이 상대방을 깨우면 바로 연결."
- ⚠️ **클코 직전 오류 (본인 적발)**: "수신측 상시 join"을 메인으로 권장 → 콜마당 패러다임 거슬림. always-join은 *배제* (listener 최소화 위반 + Agora 분당 과금 폭탄).

### 과거 실패의 정확한 재해석
- 알림 wake *방향은 옳았다*. 실패 지점 = **wake → RTC join 핸드셰이크 신뢰성**. git 이력 "Not connected"(`23a975b4`) = 수신측이 FCM/세션 신호는 받았으나 Agora 채널 join 완료 *전에* 송신 시작 / 토큰 갱신 지연 / 세션 리스너 ↔ Agora join 타이밍 불일치.

### Phase 2 1순위 — FCM wake → join 신뢰성 (배제 아니라 정교화)
- high-priority FCM은 Doze에서도 단말 wake 가능 (Google 공식). 단 지연 변동 → 무전기 즉시성(이질감 0) 위해:
  - **송신측**: wake 신호 후 수신측 *join-ready ack* 받고 발화 시작, 또는 첫 0.5~1초 음성 버퍼링 후 전송 (첫 음절 손실 방지)
  - **수신측**: FCM 수신 → Foreground Service 기동 → Agora fast-join → ready 신호 회신
- **비용**: Trigger Join(발화 시에만 접속, Mute + 30s leave) → Agora 분당 과금 최소화. 콜마당 패러다임과 *충돌 아니라 정합*.

## 원칙 (재구현 시 타협 불가)

- 볼륨버튼 hook은 *타협 불가* 핵심
- 화면 켜는 PTT는 콜마당 패러다임 위반
- 신규 구현이 깔끔 (5개월 경과 + RTM 채널 자동 참여 미완성 + Trigger Join 모델은 별개)

## 차별점

시장 어떤 PTT 앱도 *볼륨버튼 + 화면 꺼짐 + 즉시 발화* 자리를 구현 안 함. 콜마당 native 자체 구현만이 *실제 무전기 동등성* 도달. 영업 차별점 핵심 ([[ptt_market_replacement_card]] 참조).
