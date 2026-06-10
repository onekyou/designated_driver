---
name: feedback-verify-path-live-before-building
description: 기능을 어떤 코드 경로 위에 설계하기 전, 그 경로가 실제로 "살아있는지(호출되는지)" 코드로 확인 — dormant 경로 위에 빌드 금지
metadata:
  type: feedback
---

# 기능 설계 전 = 대상 코드 경로가 *살아있는지* 먼저 확인 (dormant 위에 빌드 금지)

**2026-06-11 발생.** "PTT 음성 메모를 텍스트로" 플랜을 짜며 **음성 메모가 계속 생성된다고 가정**(채팅에 ▶ 메모가 있으니까). 하지만 누락·오염 검토에서 코드 확인 → `PTTManager.kt`의 콜드 메모 경로(`recordPending`/`onColdVoiceMemo`)가 **"도달 불가(보존)" = dormant**(2026-06-03 "포그라운드 항상 라이브" 재설계로 미호출). 채팅 ▶는 **legacy 데이터**. → 그 플랜은 *죽은 경로 위에 전사 기능을 얹는* 것, 신규 입력 0. 전제 붕괴.

## Why
- **현존 산출물(▶ 메모) = 그 경로가 지금도 도는 증거가 아님.** 과거에 생성된 legacy일 수 있다. "결과물이 보인다 → 경로가 살아있다"는 *비약*.
- Explore가 "미호출"이라 보고했는데도 스크린샷(메모 존재)과의 **모순을 안 풀고** 플랜을 진행함. 모순을 남긴 채 빌드 = 사상누각.

## How to apply
- 어떤 기능을 **기존 코드 경로(트리거/콜백/녹음/리스너 등) 위에** 설계하기 전, **그 경로가 실제로 호출되는지 코드로 확인**: 호출부 grep + "도달 불가/dormant/deprecated/미호출" 주석 + 분기 가드. 산출물(파일·DB행)의 *존재*가 아니라 *경로의 활성*을 본다.
- **모순 발견 시(예: 보고 vs 화면) 반드시 코드로 해소한 뒤** 설계. 모순 미해결 상태로 플랜 작성 금지.
- 트리거 = "이 기능은 X가 생성/발화될 때 작동한다"고 가정하는 순간 → "X가 *지금* 생성/발화되나?"를 코드로 검증.

관련: [[feedback-self-monitoring-limit]](귀속 전 검증) · [[feedback-verify-before-asserting-2026-06-09]](단정 전 확인) · [[feedback-folder-scope-before-proposing-2026-06-10]]. 사례 상세 = `designated_drive/_decisions.md` "모든 PTT→텍스트".
