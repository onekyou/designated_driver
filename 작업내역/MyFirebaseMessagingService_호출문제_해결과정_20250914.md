# MyFirebaseMessagingService 호출 문제 해결 과정

**작성일**: 2025-01-14
**상태**: 진행 중 - 알림 소리 문제 해결 필요
**담당**: Claude Code Assistant

---

## 🎯 문제 상황 요약

### 초기 문제점
- **MyFirebaseMessagingService가 전혀 호출되지 않음** (onCreate, onMessageReceived 모두)
- 두 기기 모두 FCM 메시지는 전송되지만 시스템에서 자동 처리됨
- 서로 다른 아이콘과 알림음으로 표시됨

### 사용자 요구사항
- **공유콜은 긴급 알림** (큰 벨소리)
- **이중 알림 제거** (하나만 표시)
- **일관된 알림 표시**

---

## 🔍 문제 원인 분석

### 1. 핵심 발견사항
**로그 제거 스크립트 실행 후 MyFirebaseMessagingService가 작동하지 않음**

- **이전 상태** (문서 확인): MyFirebaseMessagingService 정상 호출됨 + 이중 알림 문제
- **현재 상태**: MyFirebaseMessagingService 호출 안됨 + 시스템 자동 알림만

### 2. 실제 원인
로그 제거 스크립트가 실행되었지만 **MyFirebaseMessagingService 자체에는 큰 문제가 없었음**

실제로는 다음과 같은 문제들이 복합적으로 작용:
- 이중 알림 문제 (super.onMessageReceived() + 포그라운드 조건 완화)
- 알림 채널 설정이 제대로 적용되지 않는 문제

---

## 🛠️ 해결 과정

### 1단계: MyFirebaseMessagingService 호출 확인

#### 문제 진단
```bash
# 기본 설정 확인 완료
✅ AndroidManifest.xml: MyFirebaseMessagingService 정상 등록
✅ build.gradle: Firebase 의존성 포함
✅ google-services.json: 존재
✅ Application 클래스: Firebase 초기화 정상
```

#### 해결책: TestFirebaseMessagingService 생성
```kotlin
// 최소한의 테스트 서비스 생성
class TestFirebaseMessagingService : FirebaseMessagingService() {
    override fun onCreate() {
        super.onCreate()
        Log.d("TEST_FCM", "🚨🚨🚨 TestFirebaseMessagingService onCreate 호출됨!!! 🚨🚨🚨")
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d("TEST_FCM", "🚨🚨🚨 TestFirebaseMessagingService onMessageReceived 호출됨!!! 🚨🚨🚨")
    }
}
```

**결과**: ✅ TestFirebaseMessagingService 정상 호출됨

### 2단계: MyFirebaseMessagingService 복구

#### MyFirebaseMessagingService에 추적 로그 추가
```kotlin
override fun onCreate() {
    super.onCreate()
    Log.d("TEST_ORIGINAL", "🚨🚨🚨 MyFirebaseMessagingService onCreate 호출됨!!! 🚨🚨🚨")
    println("🚨🚨🚨 MyFirebaseMessagingService onCreate 호출됨!!! 🚨🚨🚨")
    // 기존 코드...
}

override fun onMessageReceived(remoteMessage: RemoteMessage) {
    super.onMessageReceived(remoteMessage)
    Log.d("TEST_ORIGINAL", "🚨🚨🚨 MyFirebaseMessagingService onMessageReceived 호출됨!!! 🚨🚨🚨")
    println("🚨🚨🚨 MyFirebaseMessagingService onMessageReceived 호출됨!!! 🚨🚨🚨")
    // 기존 코드...
}
```

**결과**: ✅ MyFirebaseMessagingService 정상 호출됨

### 3단계: 이중 알림 문제 해결

#### 문제 분석
```
FCM 메시지 수신
    ↓
super.onMessageReceived() → 시스템 자동 알림 (기본 아이콘, 일반 소리)
    ↓
포그라운드 조건 통과 → 커스텀 알림 (커스텀 아이콘, 긴급 소리)
    ↓
이중 알림 완성
```

#### 해결책
```kotlin
override fun onMessageReceived(remoteMessage: RemoteMessage) {
    // super.onMessageReceived(remoteMessage) 제거 - 이중알림 방지
    // 커스텀 처리만 수행
}
```

**결과**: ✅ 이중 알림 해결됨

---

## 🚨 현재 남은 문제

### 1. 알림 소리 문제
- **현상**: 공유콜이 긴급 알림 대신 기본 알림음으로 재생됨
- **발생 위치**: 포그라운드/백그라운드 모두
- **원인**: `super.onMessageReceived()` 제거로 시스템 자동 알림이 사라졌는데, 커스텀 알림의 채널 설정이 제대로 적용되지 않음

### 2. 알림 채널 설정 문제
현재 SHARED_CALL_CHANNEL_ID 설정:
```kotlin
val sharedCallChannel = NotificationChannel(
    SHARED_CALL_CHANNEL_ID,
    "공유콜 알림 (긴급)",
    NotificationManager.IMPORTANCE_MAX  // 최고 우선순위
).apply {
    enableLights(true)
    lightColor = Color.YELLOW
    enableVibration(true)
    vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
    setBypassDnd(true)  // 방해금지 모드 우회
    setSound(Settings.System.DEFAULT_NOTIFICATION_URI, audioAttributes)
}
```

**문제**: 채널 설정은 정상이지만 실제 알림에서 적용되지 않음

---

## 🔧 해결 방안

### 방안 1: 명시적 소리 설정 강화
```kotlin
private fun showNotification(...) {
    val notificationBuilder = NotificationCompat.Builder(this, channelId)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle(title)
        .setContentText(content)
        .setPriority(NotificationCompat.PRIORITY_MAX)  // 최고 우선순위
        .setCategory(NotificationCompat.CATEGORY_CALL) // 통화 카테고리
        .setDefaults(NotificationCompat.DEFAULT_ALL)   // 모든 기본값

    // 공유콜의 경우 명시적 긴급 소리 설정
    if (isSharedCall) {
        val urgentSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) // 알람음 사용
        notificationBuilder.setSound(urgentSoundUri)
        notificationBuilder.setVibrate(longArrayOf(0, 1000, 500, 1000, 500, 1000))
    }
}
```

### 방안 2: 채널 재생성 및 검증
```kotlin
private fun createNotificationChannels() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 기존 채널 삭제 후 재생성
        try {
            notificationManager.deleteNotificationChannel(SHARED_CALL_CHANNEL_ID)
        } catch (e: Exception) {
            // 무시
        }

        // 새 채널 생성 with 알람 소리
        val sharedCallChannel = NotificationChannel(
            SHARED_CALL_CHANNEL_ID,
            "공유콜 알림 (긴급)",
            NotificationManager.IMPORTANCE_HIGH  // HIGH로 변경 테스트
        ).apply {
            description = "새로운 공유콜 도착 알림"
            enableLights(true)
            lightColor = Color.RED  // 더 눈에 띄는 색상
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 1000, 500, 1000, 500, 1000)
            setShowBadge(true)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            setBypassDnd(true)

            // 알람 소리 사용
            val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            setSound(alarmSound, AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_ALARM)  // ALARM 용도로 설정
                .build())
        }

        notificationManager.createNotificationChannel(sharedCallChannel)
    }
}
```

### 방안 3: 조건부 super 호출 (대안)
```kotlin
override fun onMessageReceived(remoteMessage: RemoteMessage) {
    val messageType = remoteMessage.data["type"] ?: return

    // 공유콜이 아닐 때만 시스템 처리 허용
    if (messageType != "NEW_SHARED_CALL") {
        super.onMessageReceived(remoteMessage)
    }

    // 공유콜은 완전히 커스텀 처리
    // ... 기존 로직
}
```

---

## 📋 추천 적용 순서

### 즉시 적용
1. **방안 2**: 채널 재생성 및 알람 소리 설정
2. **방안 1**: 명시적 소리 설정 강화
3. **테스트**: 공유콜 긴급 알림 확인

### 필요시 적용
4. **방안 3**: 조건부 super 호출 (위 방법들이 실패할 경우)

---

## 🎯 예상 결과

### 성공 시
- ✅ 공유콜: 긴급 알림 (알람 소리)
- ✅ 일반 알림: 기본 소리
- ✅ 이중 알림 없음
- ✅ 일관된 표시

### 실패 시
- Android 버전별 알림 정책 차이로 인한 제한
- 제조사별 알림 시스템 차이
- → Firestore 리스너 + 직접 알림 생성 방식 고려

---

## 📂 관련 파일들

### 수정된 파일들
- `MyFirebaseMessagingService.kt`: super 호출 제거, 추적 로그 추가
- `TestFirebaseMessagingService.kt`: 테스트용 서비스 (삭제 가능)

### 참고 문서들
- `FCM_이중알림_원인분석_최종결론_20250914.md`
- `FCM_알림시스템_교훈_및_해결과정_20250112.md`
- `FCM_데이터메시지_시스템처리_교훈_20250112.md`

---

## 📝 다음 단계

1. **알림 채널 재구성** (방안 2 적용)
2. **긴급 알림 테스트**
3. **성공 시 추적 로그 정리**
4. **포그라운드 조건 최적화**

**작성자**: Claude Code Assistant
**상태**: 🔄 진행 중 - 알림 소리 문제 해결 필요