# Google Play Console AAB 업로드 작업 진행 상황

## 📋 **현재 완료된 작업들**

### ✅ **1. Call Detector 앱 준비 완료**
- 패키지명 변경: `com.example.calldetector` → `com.designated.calldetector`
- Firebase 새 앱 등록 완료 (패키지명: com.designated.calldetector)
- google-services.json 교체 완료
- 새 키스토어 생성 완료: `C:\app_dev\designated_driver\keystore\upload-keystore.jks`
- 키스토어 비밀번호: `dnjsrb2367`
- AAB 파일 빌드 완료: `C:\app_dev\designated_driver\call_detector\app\release\app-release.aab`

### ✅ **2. Google Play Console 설정**
- 기존 앱 삭제 완료 (키스토어 불일치 문제로)
- 서명 키 변경 옵션 선택: "Java Keystore의 키 내보내기 및 업로드"

### ✅ **3. PEPK 도구 준비 완료**
- 암호화 공개 키 다운로드: `C:\Users\onekyou\Downloads\encryption_public_key.pem`
- PEPK 도구 다운로드: `C:\Users\onekyou\Downloads\pepk.jar`

## ⚠️ **현재 막힌 부분**
- **Java 실행 문제**: PEPK 도구 실행을 위해 Java가 필요하지만 실행되지 않음
- Android Studio Java 경로: `C:\Program Files\Android\Android Studio\jbr\bin\java.exe`

## 🔄 **컴퓨터 리셋 후 해야 할 작업**

### **1. Java 환경 확인 및 PEPK 실행**
```bash
# PowerShell에서 실행
cd C:\Users\onekyou\Downloads

# Java 경로 확인
"C:\Program Files\Android\Android Studio\jbr\bin\java.exe" -version

# PEPK 도구 실행 (한 줄로 입력)
& "C:\Program Files\Android\Android Studio\jbr\bin\java.exe" -jar pepk.jar --keystore="C:\app_dev\designated_driver\keystore\upload-keystore.jks" --alias=upload --output=output.zip --include-cert --rsa-aes-encryption --encryption-key-path="C:\Users\onekyou\Downloads\encryption_public_key.pem"
```

**키스토어 비밀번호 입력**: `dnjsrb2367`

### **2. Google Play Console에서 암호화된 키 업로드**
1. Google Play Console → Call Detector 앱 → 서명 키 변경
2. "생성된 ZIP 업로드" 단계에서 `output.zip` 파일 업로드
3. 검토 완료 후 새 키스토어로 설정 완료

### **3. AAB 파일 재업로드**
1. Google Play Console → 내부 테스트 → 새 릴리스 만들기
2. AAB 파일 업로드: `C:\app_dev\designated_driver\call_detector\app\release\app-release.aab`
3. 릴리스 노트 작성 후 출시

## 📁 **중요 파일 위치**
- **키스토어**: `C:\app_dev\designated_driver\keystore\upload-keystore.jks`
- **AAB 파일**: `C:\app_dev\designated_driver\call_detector\app\release\app-release.aab`
- **PEPK 도구**: `C:\Users\onekyou\Downloads\pepk.jar`
- **암호화 공개키**: `C:\Users\onekyou\Downloads\encryption_public_key.pem`

## 🔑 **중요 정보**
- **키스토어 비밀번호**: `dnjsrb2367`
- **패키지명**: `com.designated.calldetector`
- **키 알리아스**: `upload`

## 🚨 **주의사항**
1. 키스토어 파일과 비밀번호는 절대 분실하지 말 것
2. PEPK 실행 시 반드시 Downloads 폴더에서 실행
3. Java 실행 문제 시 Android Studio 먼저 실행 후 Terminal 사용 고려

## 📝 **다음 작업 예상 소요 시간**
- PEPK 실행: 5분
- Google Play Console 키 업로드: 10분
- AAB 파일 업로드: 10분
- **총 예상 시간**: 25분

---
**작성일**: 2025-09-21
**상태**: Java 실행 문제로 PEPK 단계에서 중단