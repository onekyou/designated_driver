# 콜디텍터 CallScreeningService 전환 수정계획서

**작성일**: 2025-11-17
**목적**: READ_CALL_LOG 없이 전화번호 확보 (구글 플레이 심사 통과)
**핵심 전략**: CallScreeningService (스팸 차단 앱) 사용

---

## 📋 현재 문제점

### 1. 전화번호 null 문제
```
Android 9+ (API 28+)에서 EXTRA_INCOMING_NUMBER가 null 반환
→ READ_CALL_LOG 권한 필요
→ 구글 플레이 심사 거부 위험
```

### 2. InCallService 작동 불가
```
IN_CALL_SERVICE_UI = false (백그라운드 모드)
→ 삼성 기기에서 바인딩 안 됨
→ onCallAdded/onCallRemoved 호출 안 됨
```

---

## ✅ 해결 방안: CallScreeningService

### 장점
- ✅ READ_CALL_LOG 불필요
- ✅ ROLE_CALL_SCREENING (스팸 차단 앱) 권한 사용
- ✅ 전화번호 확보 가능
- ✅ 구글 플레이 심사 통과 가능

### 단점
- ⚠️ Android 10+ (API 29+) 전용
- ⚠️ **Android 9 (API 28)는 지원 불가** (정책 공백)
- ⚠️ Race Condition 가능성 (CallScreeningService vs CallReceiver)

---

## 🔧 수정 대상 파일

### ✅ 이미 완료
1. **AndroidManifest.xml** - CallScreeningService 등록됨
2. **CallScreeningService.kt** - 생성됨

### 🔨 수정 필요
1. **CallScreeningService.kt** - 단순화 (companion object)
2. **CallReceiver.kt** - 전화번호 가져오는 로직 변경
3. **MainActivity.kt** - ROLE_CALL_SCREENING 권한 요청 UI 추가
4. **CallDetectorInCallService.kt** - 삭제

### ❌ 수정 불필요
- **CallDetectorService.kt** - 그대로 사용
- **Firebase 업로드 로직** - 변경 없음
- **배차 팝업 로직** - 변경 없음

---

## 📝 단계별 수정 계획

### Step 1: CallScreeningService.kt 수정

**목표**: Companion object로 전화번호만 저장

**수정 내용**:
```kotlin
@RequiresApi(Build.VERSION_CODES.Q)
class CallScreeningService : CallScreeningService() {

    companion object {
        @Volatile
        var latestIncomingNumber: String? = null
    }

    override fun onScreenCall(callDetails: Call.Details) {
        // 전화번호 저장
        latestIncomingNumber = callDetails.handle?.schemeSpecificPart

        // 모든 전화 허용 (스팸 차단 기능 없음)
        val response = CallResponse.Builder()
            .setDisallowCall(false)
            .setRejectCall(false)
            .setSkipCallLog(false)
            .setSkipNotification(false)
            .build()

        respondToCall(callDetails, response)
    }
}
```

---

### Step 2: CallReceiver.kt 수정

**목표**: CallScreeningService에서 전화번호 가져오기

**수정 위치**: Line 46, 74

**기존 코드**:
```kotlin
val numberFromIntentExtras = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
```

**수정 코드**:
```kotlin
val numberFromIntentExtras = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
    // Android 10+: CallScreeningService에서 가져오기
    CallScreeningService.latestIncomingNumber
} else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
    // Android 8.1 이하: Intent에서 가져오기 (작동함)
    intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
} else {
    // Android 9 (API 28): 정책 공백 - 지원 불가
    null
}
```

**IDLE 상태에서 번호 초기화 추가**:
```kotlin
when (stateStr) {
    TelephonyManager.EXTRA_STATE_IDLE -> {
        // ... 기존 로직

        // 전화번호 초기화
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            CallScreeningService.latestIncomingNumber = null
        }
    }
}
```

---

### Step 3: MainActivity.kt 수정

**목표**: ROLE_CALL_SCREENING 권한 요청 UI 추가

**추가할 함수**:
```kotlin
// Activity Result Launcher 등록
private val callScreeningRoleLauncher = registerForActivityResult(
    ActivityResultContracts.StartActivityForResult()
) { result ->
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val roleManager = getSystemService(Context.ROLE_SERVICE) as RoleManager
        if (roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) {
            showToast("스팸 차단 앱으로 설정되었습니다")
        }
    }
}

// 권한 요청 함수
fun requestCallScreeningRole() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val roleManager = getSystemService(Context.ROLE_SERVICE) as RoleManager
        if (!roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) {
            val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
            callScreeningRoleLauncher.launch(intent)
        }
    }
}

// 권한 확인 함수
fun isCallScreeningRoleHeld(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val roleManager = getSystemService(Context.ROLE_SERVICE) as RoleManager
        roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
    } else {
        true // Android 9 이하는 체크 안 함
    }
}
```

**UI 수정 (StatusScreen)**:
```kotlin
// InCallService 안내 제거
// CallScreeningService 안내 추가
if (!isCallScreeningRoleHeld() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFF5722))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("📞 스팸 차단 앱 설정 필요", style = MaterialTheme.typography.titleMedium)
            Text("전화번호를 확인하려면 이 앱을 '스팸 차단 앱'으로 설정해야 합니다.")
            Button(onClick = onRequestCallScreeningRole) {
                Text("스팸 차단 앱으로 설정")
            }
        }
    }
}
```

---

### Step 4: CallDetectorInCallService.kt 삭제

**목표**: 사용하지 않는 파일 제거

**작업**:
```bash
rm CallDetectorInCallService.kt
```

---

## 🎯 예상 결과

### Android 10+ (API 29+)
✅ **완벽 작동**
- CallScreeningService에서 전화번호 확보
- Firebase 업로드 성공
- 배차 팝업 표시

### Android 9 (API 28)
❌ **지원 불가**
- 전화번호 null
- Firebase 업로드 안 됨
- 정책 공백으로 해결 불가

### Android 8.1 이하 (API 27-)
✅ **작동**
- Intent에서 전화번호 확보 (권한 없이 가능)
- Firebase 업로드 성공
- 배차 팝업 표시

---

## ⚠️ 주의사항

### 1. Race Condition 대비
- CallScreeningService와 CallReceiver의 실행 순서 불확실
- `@Volatile` 키워드로 스레드 안정성 확보
- 5초 이내 최신 번호만 사용

### 2. Android 9 지원 포기
- 정책 공백으로 해결 불가능
- 구글 플레이 정책 준수 우선
- 사용자에게 Android 10+ 권장

### 3. 구글 플레이 심사
- ROLE_CALL_SCREENING은 합법적인 권한
- 스팸 차단 앱 카테고리로 정당화
- 개인정보 처리방침 명확히 작성

---

## 📊 테스트 계획

### 테스트 시나리오

1. **권한 설정**
   - 앱 실행
   - "스팸 차단 앱으로 설정" 버튼 클릭
   - 설정 완료 확인

2. **전화 수신 테스트**
   - 테스트 전화 수신
   - 로그 확인: `CallScreeningService: latestIncomingNumber = xxx`
   - 로그 확인: `CallReceiver: numberFromIntentExtras = xxx`

3. **Firebase 업로드 확인**
   - 통화 종료
   - Firebase Console에서 calls 컬렉션 확인
   - 전화번호, 타임스탬프 등 정상 저장 확인

4. **배차 팝업 확인**
   - 통화 종료 즉시 DispatchActivity 표시
   - 전화번호, 연락처 정보 정상 표시

---

## 🚀 배포 계획

### 1. 개발 빌드 테스트
- 로컬 APK 빌드
- 실제 기기 테스트
- 버그 수정

### 2. 내부 테스트 배포
- Google Play Console - 내부 테스트 트랙
- 기사님들 테스트
- 피드백 수집

### 3. 프로덕션 배포
- 개인정보 처리방침 업데이트
- 버전 업데이트 (v1.x.x)
- Google Play 출시

---

## 📞 지원 종료 안내

### Android 9 사용자 대응
```
앱 설명:
"이 앱은 Android 10 이상을 권장합니다.
Android 9에서는 일부 기능이 제한될 수 있습니다."

앱 내 안내:
"현재 기기(Android 9)에서는 자동 전화번호 감지가 지원되지 않습니다.
Android 10 이상으로 업데이트해주세요."
```

---

## ✅ 체크리스트

### 수정 전 확인사항
- [ ] Git 백업 완료
- [ ] 현재 APK 백업
- [ ] 테스트 기기 준비 (Android 10+)

### 수정 작업
- [ ] Step 1: CallScreeningService.kt 수정
- [ ] Step 2: CallReceiver.kt 수정
- [ ] Step 3: MainActivity.kt 수정
- [ ] Step 4: CallDetectorInCallService.kt 삭제

### 빌드 & 테스트
- [ ] APK 빌드 성공
- [ ] 로그 확인: CallScreeningService 실행
- [ ] 로그 확인: 전화번호 확보
- [ ] Firebase 업로드 확인
- [ ] 배차 팝업 확인

### 배포
- [ ] 개인정보 처리방침 업데이트
- [ ] 버전 번호 업데이트
- [ ] Google Play 제출
- [ ] 심사 통과

---

## 📚 참고 자료

- [Android CallScreeningService 공식 문서](https://developer.android.com/reference/android/telecom/CallScreeningService)
- [RoleManager API 문서](https://developer.android.com/reference/android/app/role/RoleManager)
- [Google Play 권한 정책](https://support.google.com/googleplay/android-developer/answer/9888170)
