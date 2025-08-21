# PTT 접근성 서비스 대체 방안 검토

## 📅 작성일: 2025-08-19

## 🎯 목표
접근성 서비스 설정의 복잡함을 해결하고 사용자 경험을 개선하는 대체 방안 검토

---

## 🔄 대체 구현 방법들

### 1. **MediaSession + MediaButton 방식** ⭐⭐⭐⭐
**장점:**
- 접근성 권한 불필요
- 헤드셋/블루투스 버튼과 동일한 메커니즘 사용
- Android 표준 방식

**단점:**
- 볼륨키가 아닌 미디어 버튼만 감지 가능
- 일부 기기에서 동작 차이

**구현 방법:**
```kotlin
// MediaSessionService로 구현
class PTTMediaSessionService : MediaBrowserServiceCompat() {
    private lateinit var mediaSession: MediaSessionCompat
    
    private val mediaButtonCallback = object : MediaSessionCompat.Callback() {
        override fun onMediaButtonEvent(mediaButtonEvent: Intent): Boolean {
            val keyEvent = mediaButtonEvent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
            // 헤드셋 버튼으로 PTT 처리
            return handlePTTAction(keyEvent)
        }
    }
}
```

### 2. **Notification Action 방식** ⭐⭐⭐
**장점:**
- 권한 불필요
- 알림 패널에서 바로 접근
- 구현 간단

**단점:**
- 물리 버튼이 아닌 터치 방식
- PTT 특성상 부자연스러움

**구현 방법:**
```kotlin
val pttStartAction = NotificationCompat.Action(
    R.drawable.ic_mic_on,
    "PTT 시작",
    getPTTStartPendingIntent()
)

val notification = NotificationCompat.Builder(context, CHANNEL_ID)
    .setContentTitle("PTT 서비스 실행 중")
    .addAction(pttStartAction)
    .setOngoing(true)
    .build()
```

### 3. **Floating Action Button (오버레이)** ⭐⭐
**장점:**
- 화면 위에 항상 표시
- 직관적인 PTT 버튼

**단점:**
- SYSTEM_ALERT_WINDOW 권한 필요 (접근성만큼 복잡)
- 사용자 방해 요소
- Android 10+ 제약 많음

### 4. **Quick Settings Tile** ⭐⭐⭐⭐
**장점:**
- 빠른 설정에서 즉시 접근
- 권한 불필요
- 사용자 친화적

**단점:**
- 토글 방식으로 push-to-talk 구현 어려움
- 연속 송신 시 불편

**구현 방법:**
```kotlin
@TargetApi(Build.VERSION_CODES.N)
class PTTTileService : TileService() {
    override fun onTileAdded() {
        updateTile("PTT 준비")
    }
    
    override fun onClick() {
        // PTT 토글 처리
        togglePTT()
    }
}
```

### 5. **Voice Recognition + Hotword** ⭐⭐
**장점:**
- 핸즈프리 방식
- "헤이, PTT" 같은 음성 명령

**단점:**
- 배터리 소모 큰
- 오인식 가능성
- 주변 소음에 민감

---

## 🎯 권장 하이브리드 접근법

### **Phase 1: 원클릭 접근성 가이드** (즉시 구현)
- 복잡한 설정 과정을 단계별 가이드로 단순화
- 직접 설정 화면 이동 기능
- 시각적 가이드 제공

### **Phase 2: MediaSession 백업 방식** (중기)
- 접근성 서비스 실패 시 MediaSession 방식으로 폴백
- 헤드셋/블루투스 버튼으로 PTT 가능
- 사용자 선택권 제공

### **Phase 3: Quick Settings Tile 추가** (장기)
- 빠른 설정에 PTT 타일 추가
- 긴급 상황 시 대체 수단

---

## 📋 구현 우선순위

1. **🥇 높음**: 원클릭 접근성 가이드 구현
2. **🥈 중간**: MediaSession 백업 방식 추가  
3. **🥉 낮음**: Quick Settings Tile 구현
4. **💡 검토**: Notification Action 방식

---

## 🚨 주의사항

### Android 버전별 제약
- **Android 10+**: 백그라운드 앱 제한 강화
- **Android 12+**: 정확한 알람 권한 추가 필요
- **Android 13+**: 알림 권한 명시적 요청
- **Android 14+**: Foreground Service 타입 세분화
- **Android 15**: 접근성 서비스 자동 비활성화

### 제조사별 차이점
- **Samsung**: One UI 추가 제약
- **Xiaomi**: MIUI 백그라운드 앱 킬러
- **Huawei**: EMUI 배터리 최적화
- **OnePlus**: OxygenOS 성능 모드

---

## 💡 최종 권장사항

**현재 접근성 서비스 방식을 유지하되, 사용자 경험을 대폭 개선:**

1. **AccessibilityGuideHelper 즉시 적용**
2. **앱 첫 실행 시 자동 가이드 표시**
3. **설정 화면에 "PTT 설정 도우미" 메뉴 추가**
4. **주기적 상태 점검 및 재안내**

이 방식으로 기존 기능의 안정성은 유지하면서도 사용자가 느끼는 복잡함을 최소화할 수 있습니다.