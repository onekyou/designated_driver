# PTT 시스템 재구축 설정 파일

## Phase 1: Agora 프로젝트 설정

### 확정된 설정 정보
1. **App ID**: e5aae3aa18484cd2a1fed0018cfb15bd (기존 사용)
2. **App Certificate**: [필요시 활성화]  
3. **테스트 Token**: 007eJxTYPgYm2dzPsj8vDlX99sTTF/aZh5rnHXqcM9SU4NNK00vCcopMKSaJiamGicmGlqYWJgkpxglGqalphgYGFokpyUZmiallLbOz2gIZGQofBDBysgAgSA+D0N+Wlpmcmq8SXxiTg4DAwDiGyRC
4. **채널명 통일**: office_{officeId}_all (모든 앱 동일)
5. **테스트 채널명**: test_office_001

### 채널명 문제 해결
🔴 **기존 문제**: 
- 콜매니저/픽업앱: office_4_all (고정)
- 드라이버앱: office_{officeId}_all (동적)

✅ **수정 방안**:
- 모든 앱: office_{실제사무실ID}_all 형태로 통일
- 로그인 시 사무실 ID를 받아서 동적으로 채널명 생성

### Agora Console 작업 순서
1. https://console.agora.io/ 접속
2. "Create Project" 클릭
3. 프로젝트명: "DesignatedDriver-PTT-Production"
4. Use case: Voice Calling
5. Authentication: App ID + Token (Recommended)
6. App Certificate 활성화
7. Token 생성 (임시 24시간용)

### 현재 상태
- [x] 기존 PTT 코드 삭제 완료
- [ ] 새 Agora 프로젝트 생성
- [ ] App ID 및 Token 발급
- [ ] 최소 권한 AndroidManifest 구성
- [ ] 테스트 앱 빌드

### 다음 단계
새로운 App ID와 Token을 받으신 후, 이 파일을 업데이트하고 Phase 2로 진행합니다.