# Agora App Certificate 설정 가이드

## 1. Agora Console에서 App Certificate 확인
- URL: https://console.agora.io/
- App ID: e5aae3aa18484cd2a1fed0018cfb15bd
- App Certificate: 32자리 문자열 (예: a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6)

## 2. Firebase Functions Secret 설정

### 방법 1: Firebase CLI 사용 (권장)
```bash
cd C:\app_dev\designated_driver\functions
firebase functions:secrets:set AGORA_APP_CERTIFICATE
```
프롬프트가 나타나면 Agora Console에서 복사한 32자리 App Certificate를 입력

### 방법 2: Firebase Console 사용
1. https://console.firebase.google.com/ 접속
2. 프로젝트 선택
3. Functions > Secret Manager 탭
4. "Create Secret" 클릭
5. Name: AGORA_APP_CERTIFICATE
6. Value: [32자리 App Certificate 입력]
7. "Create Secret" 클릭

## 3. Functions 재배포
```bash
cd C:\app_dev\designated_driver\functions
firebase deploy --only functions:generateAgoraToken
```

## 4. 확인
Secret이 제대로 설정되었는지 확인:
```bash
firebase functions:secrets:access AGORA_APP_CERTIFICATE
```

## 주의사항
- App Certificate는 정확히 32자리여야 함
- 공백이나 특수문자가 포함되지 않도록 주의
- Secret 설정 후 Functions 재배포 필수