# PropertyName 충돌 해결 작업내역

## 작업 일시
2025-09-24

## 문제 상황
- 기사앱 릴리즈 버전에서 배차팝업 생성 실패
- 알림은 정상 작동하지만 팝업이 생성되지 않음
- 오류 메시지: "found two getters or fields with conflicting case sensitivity for property: a"
- Firebase 직렬화 과정에서 PropertyName 충돌 발생

## 근본 원인 발견
`CallInfo.kt` 파일에서 `@get:PropertyName("assignedTimestamp")`와 실제 속성명 `assignedTimestamp`가 동일해서 Firebase 직렬화 시 충돌 발생

## 수정 작업 완료
1. **CallInfo.kt 수정** (driver_app/app/src/main/java/com/designated/driverapp/model/CallInfo.kt)
   ```kotlin
   // 수정 전
   @get:PropertyName("assignedTimestamp")
   val assignedTimestamp: @RawValue Any? = null,

   // 수정 후
   val assignedTimestamp: @RawValue Any? = null,
   ```

2. **ProGuard 규칙 정리** (driver_app/app/proguard-rules.pro)
   - 과도한 규칙들 제거
   - 기본적인 Firebase 관련 keep 규칙만 유지

## 현재 상태
- CallInfo.kt 수정 완료
- ProGuard 규칙 정리 완료
- 빌드 진행 중 (minifyReleaseWithR8 단계에서 중단됨)

## 다음 작업해야 할 일

### 1. 빌드 재시도
```bash
cd /c/app_dev/designated_driver/driver_app
./gradlew clean assembleRelease
```

### 2. 빌드 성공 후 APK 확인
```bash
ls -la app/build/outputs/apk/release/
```

### 3. Firebase 호스팅에 업로드
```bash
cd /c/app_dev/designated_driver/releases
cp ../driver_app/app/build/outputs/apk/release/app-release.apk ./apk/driver_app.apk
firebase deploy --only hosting
```

### 4. 테스트 확인사항
- 배차 알림 수신 확인
- 배차 팝업 정상 생성 확인
- PropertyName 충돌 오류 해결 확인

## 수정된 파일 목록
1. `driver_app/app/src/main/java/com/designated/driverapp/model/CallInfo.kt`
2. `driver_app/app/proguard-rules.pro`

## 주의사항
- 콜매니저에서는 동일한 PropertyName 문제가 없음을 확인함
- 이 수정으로 Firebase 직렬화 충돌 문제가 해결될 것으로 예상
- 빌드 시 R8 최소화 단계에서 시간이 오래 걸릴 수 있음

## 백업 정보
- 현재 브랜치: debug-log-cleanup-backup
- 메인 브랜치: master
- Firebase 프로젝트: calldetector-5d61e
- 웹사이트: https://calldetector-5d61e.web.app/

## 실행규칙 준수사항
1. 모든 대답은 속도에 연연하지말고 심사숙고해서 두번 이상 검토해서 내놓을것
2. 하드코딩은 절대 안돼 항상 정석으로 진행할것
3. 수정전에는 항상 허락을 구할 것
4. 요구한것 이상의 수정을 하지말것. 요구한것에 도움이 되는것은 제안을 하고 허락을 구할 것. 임의로 수정하지말것.