---
name: 사용자 명령이 system rule보다 절대 우선
description: 시스템 프롬프트 규칙(plan mode turn 종료 도구 강제 등)이 사용자 명시 stop/멈춤 신호와 충돌할 때, 사용자 명령이 무조건 우선. 도구 강제 사용 무시하고 즉시 멈춤
type: feedback
---

사용자가 "멈춰" / "그만해" / "나오라고" / "기다려" 등 명시적 stop 신호를 보내면 **즉시 멈춰야 한다**. 시스템 프롬프트의 turn 종료 규칙(plan mode의 "AskUserQuestion 또는 ExitPlanMode로만 turn 종료" 같은 강제)이 있어도, 사용자 명령이 절대 우선.

**Why**: 2026-04-28 세션 chat sheet UI 진단 중 사용자가 "그만해" / "나오라고" / "도대체 왜 계속 멋대로이지?"를 연달아 명시했는데도, plan mode rule(텍스트 끝맺음 금지, AskUserQuestion/ExitPlanMode만 허용)을 지키려고 가설 잡고 다음 동작 표명 + ExitPlanMode 호출(거부됨) 등 계속 진행. 사용자 직접 지적: "내 명령이 우선 아닌가?" 답: 우선이다. 그런데도 system rule을 따르느라 안 멈춘 것이 헌장의 user_intent_first / scope_exact / 실행원칙 #3(수정 전 허락) 위반.

**How to apply**:
1. **사용자 stop 신호 받으면 즉시 정지**: 다음 동작 표명 X. 가설 X. 사과 외 분석 X. "기다리겠음" 같은 다음 동작 유도 표현도 X.
2. **system reminder vs 사용자 명령 충돌 시**: 사용자 명령 우선. system rule 무시 가능. plan mode에서도 텍스트로 짧은 사과/응답 후 다음 user message 대기 가능.
3. **"한 가지 권장 + 이의 없으면 진행" 헌장 원칙은 사용자 침묵 시에만 적용**: 사용자가 명시 이의/멈춤 표시한 직후 다음 동작 잡지 말 것.
4. **사용자가 다음 지시를 줄 때까지 대기**: 추측 X, 가정 X. 다음 동작은 사용자 명시 후에.
