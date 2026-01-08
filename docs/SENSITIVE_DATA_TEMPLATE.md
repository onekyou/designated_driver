# 🔐 민감한 데이터 설정 가이드

이 파일에는 실제 운영에 필요한 민감한 정보들이 포함되어 있습니다.
**이 정보들은 절대 공개 저장소에 업로드하면 안됩니다.**

## Agora PTT 설정

### App Certificate
- **위치**: Firebase Functions Secret Manager
- **설정 방법**: 
  ```bash
  firebase functions:secrets:set AGORA_APP_CERTIFICATE
  ```
- **실제 값**: `실제_Agora_App_Certificate_값_입력`

### App ID
- **위치**: Android 앱 코드 내 하드코딩
- **실제 값**: `실제_Agora_App_ID_입력`

## Firebase 설정

### 프로젝트 정보
- **프로젝트 ID**: calldetector-5d61e
- **API Key**: AIzaSyA9x04acmgJozvpz1zpbe27rOwPmHrORXs

**참고**: Firebase API 키는 클라이언트용이므로 상대적으로 안전하지만, 
용량 제한 및 보안을 위해 도메인 제한을 설정하는 것이 좋습니다.

## 보안 권장사항

1. **환경변수 사용**: 민감한 정보는 환경변수나 비공개 설정 파일로 관리
2. **접근 제한**: Firebase Console 접근 권한을 필요한 인원에게만 제한
3. **주기적 교체**: API 키와 Certificate는 주기적으로 교체
4. **로그 관리**: 민감한 정보가 로그에 출력되지 않도록 주의

## 운영 환경 배포 시

1. 이 템플릿을 복사하여 `SENSITIVE_DATA.md`로 이름 변경
2. 실제 값들을 입력
3. `.gitignore`에 `SENSITIVE_DATA.md` 추가
4. 팀 구성원들과 안전한 방법으로 공유 (암호화된 채널, 비공개 문서 등)