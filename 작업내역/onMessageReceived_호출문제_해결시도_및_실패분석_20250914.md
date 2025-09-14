# onMessageReceived 호출 문제 해결 시도 및 실패 분석

**작성일**: 2025-01-14
**상태**: 모든 시도 실패 - 마지막 커밋으로 복구 완료
**담당**: Claude Code Assistant

---

## 🎯 문제 상황 요약

### 원본 문제
- **Android 13, 15 두 기기 모두**: MyFirebaseMessagingService의 onMessageReceived가 호출되지 않음
- **시스템 자동 알림은 표시됨** (Android 15: 긴급소리, Android 13: 기본소리)
- **동일한 빌드**에서 Android 버전별로 알림 소리만 다름

---

## 🔍 시도한 해결 방법들

### 1단계: Functions에서 alertTitle/alertMessage 제거
**목적**: data-only 메시지로 변경하여 시스템 자동 알림 방지
**시도 내용**:
- Functions의 모든 FCM 페이로드에서 alertTitle, alertMessage 제거
- customTitle, customMessage로 변경하려다 중단
- click_action도 customAction으로 변경 시도

**결과**: ❌ 실패
**실패 원인**: FCM 토큰 무효로 테스트 불가능한 상태 도달

### 2단계: Merged Manifest에서 FCM 서비스 충돌 확인
**목적**: 제3자 라이브러리의 FirebaseMessagingService가 메시지를 가로채는지 확인
**시도 내용**:
- Android Studio Merged Manifest에서 MESSAGING_EVENT 검색
- MyFirebaseMessagingService만 등록되어 있음을 확인
- build.gradle에서 의심스러운 제3자 라이브러리 없음 확인

**결과**: ✅ 정상
**결론**: 서비스 충돌은 원인이 아님

### 3단계: TestFirebaseMessagingService 생성 및 단계적 접근
**목적**: 최소한의 서비스로 시작해서 단계적으로 로직 추가하며 문제 지점 찾기
**시도 내용**:
- TestFirebaseMessagingService 생성 (최소한의 코드)
- AndroidManifest.xml에서 MyFirebaseMessagingService 주석처리
- TestFirebaseMessagingService에 MyFirebaseMessagingService의 로직 단계적 이식
- onCreate, 채널 생성, onMessageReceived 초기 로직까지 이식

**결과**: ❌ 테스트 불가능
**실패 원인**: FCM 토큰 무효 상태

---

## 🚨 근본적인 문제 발견

### FCM 토큰 무효 문제
Firebase Console 로그에서 확인:
- "무효한 FCM 토큰 발견"
- "토큰 없음"
- "지정된 개인 무효한 FCM 토큰으로 지속 정리 안됨"

**결과**: Functions에서 FCM 전송 자체가 실패하여 어떤 FirebaseMessagingService도 호출되지 않음

### 오해한 부분들
1. **TestFirebaseMessagingService가 정상 작동한다고 잘못 기억**
   - 실제로는 TestFirebaseMessagingService도 onMessageReceived 호출되지 않음
   - FCM 토큰 무효로 인한 전송 실패가 원인

2. **Android 버전별 차이라고 잘못 분석**
   - 실제로는 동일한 빌드에서 FCM 토큰 상태의 차이

3. **Functions 코드 문제라고 과도하게 수정**
   - alertTitle/alertMessage 제거는 올바른 방향이었으나
   - FCM 토큰 문제를 먼저 해결했어야 함

---

## 🔧 실패한 수정 내용들

### Functions 파일 (index.ts)
- 여러 위치의 alertTitle, alertMessage 필드 제거
- customTitle, customMessage로 변경 시도
- click_action을 customAction으로 변경

### Android 파일들
- TestFirebaseMessagingService 생성 및 복잡한 로직 이식
- AndroidManifest.xml에서 서비스 전환
- MyFirebaseMessagingService에 과도한 디버깅 로그 추가

---

## 📋 교훈 및 다음 단계

### 교훈
1. **기본 조건부터 확인**: FCM 토큰 유효성을 먼저 확인했어야 함
2. **단계적 접근의 한계**: 토큰 문제로 인해 어떤 테스트도 의미 없었음
3. **원인 분석 부족**: 시스템 자동 알림 표시 + onMessageReceived 미호출의 정확한 메커니즘 이해 부족

### 올바른 다음 단계
1. **FCM 토큰 재생성**: 앱 재시작 또는 재설치
2. **Firebase Console에서 FCM 전송 성공 확인**
3. **그 후 onMessageReceived 호출 여부 테스트**
4. **호출되지 않으면 Functions 페이로드 분석**

---

## 🗂️ 복구된 파일들

### 복구 완료
- `functions/src/index.ts`: alertTitle/alertMessage 변경사항 복구
- `call_manager/app/src/main/AndroidManifest.xml`: MyFirebaseMessagingService로 복구
- `call_manager/app/src/main/java/com/designated/callmanager/service/TestFirebaseMessagingService.kt`: 기본 상태로 복구

### 유지된 내용
- MyFirebaseMessagingService의 디버깅 로그는 유지 (문제 해결에 도움)
- CLAUDE.md의 실행원칙 개선사항 유지

---

## 🎯 결론

**모든 시도가 실패한 근본 원인**: FCM 토큰 무효로 인한 전송 실패

**현재 상태**: 마지막 정상 커밋(80225af)으로 복구 완료

**다음 작업**: FCM 토큰 문제 해결 후 체계적 재접근 필요

---

**작성자**: Claude Code Assistant
**상태**: ❌ 모든 시도 실패 - 근본 원인 미해결