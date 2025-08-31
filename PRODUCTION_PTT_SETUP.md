# 🚀 PTT 실전 모드 설정 가이드

## ✅ 완료된 작업
1. **테스트 모드 완전 제거**
   - Firebase Functions에서 테스트 모드 제거 완료
   - 클라이언트에서 generateTestToken() 함수 삭제 완료
   - 보안 검증 로직 복구 완료

2. **실전 모드 보안 강화**
   - UID 불일치 시 토큰 거부
   - App Certificate 없을 시 오류 발생
   - 채널명 검증 로직 유지

## 🔧 필수 설정 작업

### 1. App Certificate 설정 (필수!)
```bash
cd C:\app_dev\designated_driver\functions
firebase functions:secrets:set AGORA_APP_CERTIFICATE
```
**입력값**: `d4109290198749419a44bcb23a6a05c5`

### 2. Firebase Functions 재배포
```bash
firebase deploy --only functions:generateAgoraToken,functions:refreshAgoraToken
```

### 3. 설정 확인
```bash
firebase functions:secrets:list
```
`AGORA_APP_CERTIFICATE`가 목록에 있어야 함

## 📋 실전 모드 체크리스트

### 서버 사이드
- [x] App Certificate 필수 요구
- [x] 테스트 모드 제거
- [x] UID 전달 및 검증
- [x] 토큰 만료 시간 24시간 설정
- [x] 에러 로깅 강화

### 클라이언트 사이드
- [x] UID 일치성 검증
- [x] 캐시 토큰 UID 0 자동 무효화
- [x] 테스트 토큰 생성 함수 제거
- [x] 보안 검증 실패 시 토큰 거부

## 🔒 보안 검증 흐름

1. **클라이언트 UID 생성**
   - Firebase Auth UID → SHA-256 해시 → 32비트 정수

2. **서버 토큰 생성**
   - 클라이언트 UID 수신
   - App Certificate로 토큰 서명
   - 동일한 UID 반환

3. **클라이언트 검증**
   - 서버 UID == 클라이언트 UID 확인
   - 채널명 일치 확인
   - 검증 실패 시 토큰 거부

## 🚨 주의사항

1. **App Certificate 미설정 시**
   - 오류 발생: "서버 설정 오류: Agora App Certificate가 구성되지 않았습니다"
   - 해결: 위의 설정 작업 1번 실행

2. **UID 불일치 시**
   - 오류 발생: "보안상 위험하므로 토큰 사용 중단"
   - 원인: 서버/클라이언트 버전 불일치
   - 해결: Functions 재배포

3. **캐시 문제**
   - 증상: 계속 UID 0 사용
   - 해결: 앱 데이터 삭제 또는 재설치

## 📊 실전 모드 확인 방법

Logcat에서 다음 로그 확인:
- `[1] Client: Generated UID` - 음수 또는 양수 (0이 아님)
- `[2] Server: Received request for UID` - 동일한 UID
- `[3] Server: Building token with UID` - 동일한 UID  
- `[4] Server: Returning token for UID` - 동일한 UID
- `[5] Client <- Server: Received token` - 동일한 UID
- `[6] Client -> Agora: Joining channel` - 동일한 UID
- `[7] Agora -> Client: Successfully joined` - 동일한 UID

모든 UID가 동일하면 실전 모드 정상 작동!