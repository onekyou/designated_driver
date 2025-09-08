# CallLog 기반 부재중전화 자동처리 시스템 구현

## 작업 개요
사무실 마감 후 부재중 전화에 대한 자동 공유콜 생성 및 SMS 발송 시스템을 CallLog 모니터링 방식으로 구현

## 배경 및 문제점

### 초기 요구사항
- 사무실 운영마감 후 자동 공유시스템 필요
- 마감 시간에는 직원이 모두 퇴근하여 전화를 받을 수 없는 상황
- 고객이 전화를 걸어도 응답할 수 없으므로 자동으로 다른 사무실에 공유하고 SMS 발송 필요

### 기술적 제약사항
- Android 보안 정책으로 인해 RINGING 상태에서 전화번호 접근 불가
- 기존 OFFHOOK → IDLE 로직은 전화를 받아야만 작동하므로 마감시간에 부적합
- 부재중 전화에 대한 실시간 감지 및 처리 필요

### 선택된 해결방안: CallLog 모니터링
- ContentObserver를 이용한 CallLog 변경사항 실시간 감지
- 부재중 전화(MISSED_TYPE) 발생 시 즉시 처리
- 사무실 상태 확인 후 자동 공유콜 생성 및 SMS 발송

## 구현 대상 앱

### 1. call_detector_final
- 독립적인 콜 감지 전용 앱
- 파일: `C:\app_dev\designated_driver\call_detector_final\app\src\main\java\com\example\calldetector\CallDetectorService.kt`

### 2. call_manager_no_ptt  
- 콜매니저 앱 내장 콜 감지 기능
- 파일: `C:\app_dev\designated_driver\call_manager_no_ptt\app\src\main\java\com\designated\callmanager\service\CallDetectorService.kt`

## 구현된 핵심 기능

### 1. CallLogObserver 강화
```kotlin
inner class CallLogObserver(handler: Handler) : ContentObserver(handler) {
    override fun onChange(change: Boolean) {
        super.onChange(change)
        Log.i(TAG, "📞 CallLog 변경 감지 - 최근 통화 확인 중...")
        checkLastCallLog()
    }
}
```

### 2. checkLastCallLog() - 부재중 전화 감지
```kotlin
private fun checkLastCallLog() {
    serviceScope.launch {
        try {
            // READ_CALL_LOG 권한 확인
            if (ContextCompat.checkSelfPermission(this@CallDetectorService, Manifest.permission.READ_CALL_LOG) 
                != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "READ_CALL_LOG 권한이 없습니다.")
                return@launch
            }
            
            val projection = arrayOf(
                CallLog.Calls.NUMBER,
                CallLog.Calls.TYPE,
                CallLog.Calls.DATE,
                CallLog.Calls.CACHED_NAME
            )
            
            val cursor = contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                null,
                null,
                "${CallLog.Calls.DATE} DESC LIMIT 1"
            )
            
            cursor?.use {
                if (it.moveToFirst()) {
                    val number = it.getString(it.getColumnIndexOrThrow(CallLog.Calls.NUMBER))
                    val type = it.getInt(it.getColumnIndexOrThrow(CallLog.Calls.TYPE))
                    val date = it.getLong(it.getColumnIndexOrThrow(CallLog.Calls.DATE))
                    val cachedName = it.getString(it.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME))
                    
                    // 부재중 전화이고, 최근 5초 이내이며, 서비스 시작 이후인지 확인
                    if (type == CallLog.Calls.MISSED_TYPE && 
                        (System.currentTimeMillis() - date) < 5000 &&
                        date > serviceStartTime) {
                        
                        Log.i(TAG, "📞 부재중 전화 감지: $number")
                        
                        // 사무실 상태 확인 후 처리
                        val regionId = sharedPreferences.getString("regionId", null)
                        val officeId = sharedPreferences.getString("officeId", null)
                        
                        if (regionId != null && officeId != null) {
                            checkOfficeStatusForMissedCall(regionId, officeId, number, cachedName)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ CallLog 확인 실패", e)
        }
    }
}
```

### 3. checkOfficeStatusForMissedCall() - 사무실 상태 확인
```kotlin
private suspend fun checkOfficeStatusForMissedCall(
    regionId: String,
    officeId: String,
    phoneNumber: String,
    contactName: String?
) {
    try {
        val firestore = FirebaseFirestore.getInstance()
        val document = firestore.collection("regions").document(regionId)
            .collection("offices").document(officeId)
            .get()
            .await()
        
        val officeStatus = document.getString("status") ?: "OPEN"
        val officeName = document.getString("name") ?: "사무실"
        
        Log.i(TAG, "📞 부재중 전화 - 사무실 상태: $officeStatus")
        
        if (officeStatus == "CLOSED") {
            Log.i(TAG, "🌙 마감 상태에서 부재중 전화 처리 시작")
            
            val (fullContactName, contactAddress) = getContactInfo(applicationContext, phoneNumber)
            val deviceName = sharedPreferences.getString("deviceName", android.os.Build.MODEL) ?: android.os.Build.MODEL
            val finalContactName = fullContactName ?: contactName
            
            // 공유콜 생성
            createSharedCallFromMissed(regionId, officeId, phoneNumber, finalContactName, contactAddress, deviceName)
            
            // SMS 발송
            sendAutoSMS(phoneNumber, officeName)
            
            Log.i(TAG, "✅ 부재중 전화 처리 완료 - 공유콜 생성 및 SMS 발송")
        } else {
            Log.i(TAG, "🏢 운영중 - 부재중 전화 무시")
        }
    } catch (e: Exception) {
        Log.e(TAG, "❌ 부재중 전화 처리 실패", e)
    }
}
```

### 4. createSharedCallFromMissed() - 공유콜 생성
```kotlin
private fun createSharedCallFromMissed(
    regionId: String,
    officeId: String,
    phoneNumber: String,
    contactName: String?,
    contactAddress: String?,
    deviceName: String
) {
    val sharedCallData = hashMapOf<String, Any>(
        "phoneNumber" to phoneNumber,
        "sourceRegionId" to regionId,
        "sourceOfficeId" to officeId,
        "deviceName" to deviceName,
        "status" to "OPEN",
        "timestamp" to FieldValue.serverTimestamp(),
        "callType" to "MISSED_CALL", // 부재중 전화
        "timestampClient" to System.currentTimeMillis(),
        "fromMissedCall" to true, // 부재중 전화에서 생성됨을 표시
        "fromCallManager" to true // 콜매니저에서 생성된 콜임을 표시
    )
    
    contactName?.let { sharedCallData["customerName"] = it }
    contactAddress?.let { sharedCallData["customerAddress"] = it }
    
    val firestore = FirebaseFirestore.getInstance()
    firestore.collection("shared_calls")
        .add(sharedCallData)
        .addOnSuccessListener { documentReference ->
            Log.i(TAG, "✅ 부재중 전화 공유콜 생성 완료 - ID: ${documentReference.id}")
        }
        .addOnFailureListener { e ->
            Log.e(TAG, "❌ 부재중 전화 공유콜 생성 실패: ${e.message}", e)
        }
}
```

## 시스템 작동 흐름

### 1. 마감 시간 부재중 전화 발생
1. 고객이 사무실 전화번호로 전화
2. 직원이 없어서 전화를 받지 않음
3. CallLog에 부재중 전화(MISSED_TYPE) 기록

### 2. 자동 감지 및 처리
1. CallLogObserver가 CallLog 변경사항 감지
2. checkLastCallLog()에서 최근 5초 이내 부재중 전화 확인
3. checkOfficeStatusForMissedCall()에서 사무실 상태 확인

### 3. 마감 상태인 경우 자동 처리
1. shared_calls 컬렉션에 공유콜 생성 (status: "OPEN")
2. 고객에게 자동 SMS 발송: "[사무실명] 운영시간이 종료되었습니다. 잠시 후 다시 연락드리겠습니다."

### 4. 다른 사무실에서 콜 수락
1. 운영중인 다른 사무실에서 공유콜 확인
2. 콜 수락 시 해당 사무실 calls 컬렉션으로 복사
3. 정상적인 배차 및 운행 프로세스 진행

## 기술적 특징

### 장점
- **실시간 감지**: ContentObserver를 통한 즉시 감지
- **권한 최소화**: READ_CALL_LOG 권한만 필요
- **안정성**: Android 보안 정책 준수
- **중복 방지**: 서비스 시작 시간 이후 통화만 처리

### 검증 조건
- 부재중 전화만 처리 (type == CallLog.Calls.MISSED_TYPE)
- 최근 5초 이내 통화만 처리 (중복 방지)
- 서비스 시작 이후 통화만 처리 (기존 로그 무시)

## 필요 권한
```xml
<uses-permission android:name="android.permission.READ_CALL_LOG" />
<uses-permission android:name="android.permission.SEND_SMS" />
<uses-permission android:name="android.permission.READ_CONTACTS" />
```

## Firebase Firestore 구조

### shared_calls 컬렉션
```json
{
  "phoneNumber": "고객전화번호",
  "sourceRegionId": "발생지역ID",
  "sourceOfficeId": "발생사무실ID", 
  "deviceName": "감지기기명",
  "status": "OPEN",
  "timestamp": "서버타임스탬프",
  "callType": "MISSED_CALL",
  "timestampClient": "클라이언트타임스탬프",
  "fromMissedCall": true,
  "fromCallManager": true,
  "customerName": "고객명",
  "customerAddress": "고객주소"
}
```

## 로그 메시지 패턴
- `📞 CallLog 변경 감지 - 최근 통화 확인 중...`
- `📞 부재중 전화 감지: [전화번호]`
- `🌙 마감 상태에서 부재중 전화 처리 시작`
- `✅ 부재중 전화 처리 완료 - 공유콜 생성 및 SMS 발송`

## 구현 완료 일자
2025-09-05

## 관련 이슈 해결
- Android 보안 제약으로 인한 RINGING 상태 전화번호 접근 불가 문제 해결
- 마감시간 무응답 전화에 대한 자동 처리 로직 구현
- 두 개 앱(call_detector_final, call_manager_no_ptt) 모두에 동일 기능 적용

## 향후 개선 방향
- 공유콜 우선순위 시스템 추가
- 지역별 공유콜 라우팅 로직 고도화
- 통계 및 모니터링 기능 강화