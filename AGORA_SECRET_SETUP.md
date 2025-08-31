# Agora App Certificate 설정 방법

## App Certificate 정보
- App ID: `e5aae3aa18484cd2a1fed0018cfb15bd`
- App Certificate: `[YOUR_AGORA_APP_CERTIFICATE]`

## 설정 방법

### 옵션 1: Command Prompt 또는 PowerShell 사용

1. **Command Prompt** 또는 **PowerShell**을 관리자 권한으로 실행

2. Functions 디렉토리로 이동:
```cmd
cd C:\app_dev\designated_driver\functions
```

3. Secret 설정 (아래 명령 중 하나 선택):

**방법 A: 직접 입력 (권장)**
```cmd
firebase functions:secrets:set AGORA_APP_CERTIFICATE
```
프롬프트가 나타나면 `[YOUR_AGORA_APP_CERTIFICATE]` 입력 후 Enter

**방법 B: 한 줄 명령**
```powershell
echo [YOUR_AGORA_APP_CERTIFICATE] | firebase functions:secrets:set AGORA_APP_CERTIFICATE --force
```

### 옵션 2: Firebase Console 사용

1. Firebase Console 접속: https://console.firebase.google.com/
2. 프로젝트 선택 (calldetector-5d61e)
3. 좌측 메뉴에서 **Functions** 클릭
4. 상단 탭에서 **Secret Manager** 클릭
5. **Create secret** 버튼 클릭
6. 다음 정보 입력:
   - Secret ID: `AGORA_APP_CERTIFICATE`
   - Secret value: `[YOUR_AGORA_APP_CERTIFICATE]`
7. **Create secret** 클릭

## Functions 재배포

Secret 설정 후 반드시 Functions를 재배포해야 합니다:

```cmd
cd C:\app_dev\designated_driver\functions
firebase deploy --only functions:generateAgoraToken
```

## 설정 확인

Secret이 제대로 설정되었는지 확인:

```cmd
firebase functions:secrets:list
```

AGORA_APP_CERTIFICATE가 목록에 나타나면 성공!

## 문제 해결

만약 "Secret Payload cannot be empty" 에러가 발생하면:
1. Firebase Console에서 직접 설정 (옵션 2 사용)
2. 또는 텍스트 파일을 만들어서 설정:
   ```cmd
   echo [YOUR_AGORA_APP_CERTIFICATE] > secret.txt
   firebase functions:secrets:set AGORA_APP_CERTIFICATE < secret.txt
   del secret.txt
   ```