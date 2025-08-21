package com.designated.walkietalkie

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.KeyEvent
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.agora.rtc2.Constants
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.RtcEngineConfig
import android.widget.Toast
import com.designated.walkietalkie.databinding.ActivityMainBinding
import android.media.MediaRecorder
import android.os.Environment
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import android.util.Log
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build
import io.agora.rtc2.IRtcEngineEventHandler.AudioVolumeInfo
import com.google.firebase.database.ktx.database
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.ktx.Firebase

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var rtcEngine: RtcEngine? = null
    private var isConnected = false
    private var isSpeaking = false
    
    // --- Agora 설정값 ---
    private val appId = "a719c12f1d884f778cb768be0a59f819"
    private val channelName = "driver_channel"
    // --- 토큰 값 업데이트 (앱 인증서 비활성화 상태에서 유효) ---
    private val token = "007eJxTYIi80yOu4P0+5kPnhe+HXSdmfFh/qKNugqep3IGjvkeYWJ8pMCSaG1omGxqlGaZYWJikmZtbJCeZm1kkpRokmlqmWRhabrf+lt4QyMjQqd3EzMgAgSA+L0NYfmZyqoJzYk5OZl46AwMA+o8j3g=="

    // 더블 클릭 감지를 위한 변수들
    private var lastClickTime: Long = 0
    private val DOUBLE_CLICK_TIME_DELTA: Long = 300 // 더블 클릭 간격 (300ms)

    private val requiredPermissions = arrayOf(
        Manifest.permission.RECORD_AUDIO
    )

    private val userList = mutableListOf<Int>()

    // --- SoundPool 관련 변수 ---
    private lateinit var soundPool: SoundPool
    private var soundIdButtonClick: Int = 0
    private var soundIdPttEffect: Int = 0 // 시작/종료음 공통 사용
    private var soundPoolLoaded = false

    // --- 현재 볼륨 변수 (0-200 범위 사용) ---
    private var currentVolume: Int = 50 // 초기값 50으로 설정됨

    // --- Firebase Database 관련 변수 추가 ---
    private lateinit var database: FirebaseDatabase
    private lateinit var presenceRef: DatabaseReference // /presence/{channelName}/{userId}
    private lateinit var connectionsRef: DatabaseReference // /connections/{userId}
    private lateinit var connectedRef: DatabaseReference // .info/connected
    private var myConnectionsKey: String? = null // 이 기기의 고유 연결 키
    private lateinit var connectedListener: ValueEventListener

    // !! 중요: 임시 사용자 ID. 실제 앱에서는 Firebase Auth UID 등을 사용 !!
    private val myUserId = "walkieUser_" + UUID.randomUUID().toString().substring(0, 6)

    private val rtcEventHandler = object : IRtcEngineEventHandler() {
        override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
            runOnUiThread {
                isConnected = true
                updateStatus("채널 연결됨")
                Log.i("AgoraStatus", "Channel joined successfully, uid: $uid")
                if(awaitingDoubleClickRelease) { // PTT 시작 요청 중이었다면
                    startSpeakingActual() // 실제 송신 시작 함수 호출
                }
            }
        }

        override fun onRejoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
            runOnUiThread {
                isConnected = true
                updateStatus("채널 재연결됨")
                Log.i("AgoraStatus", "Channel rejoined successfully, uid: $uid")
            }
        }

        override fun onLeaveChannel(stats: RtcStats?) {
            runOnUiThread {
                isConnected = false
                updateStatus("채널 퇴장함")
                Log.i("AgoraStatus", "Left channel")
            }
        }

        override fun onUserJoined(uid: Int, elapsed: Int) {
            runOnUiThread {
                if (!userList.contains(uid)) {
                userList.add(uid)
                }
                Log.i("AgoraUser", "User joined: $uid")
                // updateUserListUI() // UI 주석 처리됨
            }
        }

        override fun onUserOffline(uid: Int, reason: Int) {
            runOnUiThread {
                userList.remove(uid)
                 Log.i("AgoraUser", "User offline: $uid, Reason: $reason")
                // updateUserListUI() // UI 주석 처리됨
            }
        }

        override fun onAudioVolumeIndication(speakers: Array<out AudioVolumeInfo>?, totalVolume: Int) {
            // 너무 자주 호출될 수 있으므로, UI 업데이트는 신중하게
            var someoneIsSpeaking = false
                speakers?.forEach { speaker ->
                // uid 0 is local user. volume > 5 is a reasonable threshold.
                if (speaker.uid != 0 && speaker.volume > 10) { // 임계값 약간 높임
                    someoneIsSpeaking = true
                    // Log.d("AgoraAudio", "User $speaker.uid is speaking with volume $speaker.volume")
                    return@forEach // 한 명이라도 말하면 더 이상 체크 불필요
                }
            }

            // Update status only if connected and not speaking myself
            if (isConnected && !isSpeaking) {
                 runOnUiThread {
                     if (someoneIsSpeaking) {
                         updateStatus("수신 중...")
                     } else {
                         // If no one is speaking, revert to a base connected status
                         updateStatus("수신 대기 중...")
                    }
                }
            }
        }

        override fun onError(err: Int) {
            runOnUiThread {
                updateStatus("에러 발생: ${agoraErrorToString(err)}") // 에러 코드 문자열 변환
                 Log.e("AgoraError", "Agora SDK error code: $err, message: ${agoraErrorToString(err)}")
                // 특정 에러 코드에 대한 처리 추가 가능 (예: 토큰 만료 시 재발급 요청 등)
                if (err == Constants.ERR_TOKEN_EXPIRED || err == Constants.ERR_INVALID_TOKEN) {
                    // TODO: 토큰 갱신 로직 필요
                    leaveAgoraChannel() // 일단 채널 나가기
                }
            }
        }

        override fun onConnectionStateChanged(state: Int, reason: Int) {
            runOnUiThread {
                 Log.i("AgoraStatus", "Connection state changed to $state (${agoraConnStateToString(state)}), Reason: $reason (${agoraConnReasonToString(reason)})")
                val wasConnected = isConnected
                isConnected = (state == Constants.CONNECTION_STATE_CONNECTED || state == Constants.CONNECTION_STATE_RECONNECTING)

                // 연결이 끊겼을 때 (Failed 또는 Disconnected) + 내가 말하고 있었다면 강제 종료
                if (!isConnected && wasConnected && isSpeaking) {
                    Log.w("AgoraStatus", "Connection lost while speaking, forcing PTT stop.")
                    forceStopSpeaking() // 강제 종료 함수 호출
                }

                when (state) {
                    Constants.CONNECTION_STATE_DISCONNECTED -> updateStatus("연결 끊김")
                    Constants.CONNECTION_STATE_CONNECTING -> updateStatus("연결 중...")
                    Constants.CONNECTION_STATE_CONNECTED -> updateStatus("연결됨") // Join 성공 시 여기서도 호출됨
                    Constants.CONNECTION_STATE_RECONNECTING -> updateStatus("재연결 중...")
                    Constants.CONNECTION_STATE_FAILED -> updateStatus("연결 실패")
                }
            }
        }
    }

    private var isReadyToSpeak = false

    // 녹음 기능 추가
    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private lateinit var outputFile: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // --- 추가: onCreate 시작 로그 ---
        Log.i("MainActivityLifecycle", "onCreate START") 
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Log.i("MainActivity", "onCreate called for user: $myUserId") // 기존 로그

        // setupFirebasePresence() // 순서 변경: 권한 확인 후

        // --- 추가: setupSoundPool 호출 전 로그 ---
        Log.d("MainActivityLifecycle", "Calling setupSoundPool...") 
        setupSoundPool() // SoundPool 초기화 및 로드

        if (checkPermissions()) {
            // 권한 있으면 바로 Firebase 및 Agora 초기화 시도
            setupFirebasePresence() // Firebase 먼저 설정
            initializeAgoraEngine() // Agora 엔진 초기화
        } else {
             Log.w("Permissions", "Required permissions not granted at onCreate.")
             updateStatus("권한 필요") // 초기 상태
             // 권한 요청은 checkPermissions 내부에서 호출됨
        }

        setupButtonClickListeners() // UI 버튼 리스너 설정
        // 초기 상태 텍스트는 Firebase 연결 후 업데이트되므로 여기서 설정 안함
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            // 키를 꾹 누르고 있을 때 반복 호출되는 것 방지
            if (event?.repeatCount ?: 0 > 0) {
                return true
            }

            val clickTime = System.currentTimeMillis()
            
            // 더블 클릭 감지 (300ms 이내)
            if (clickTime - lastClickTime < DOUBLE_CLICK_TIME_DELTA) {
                // 더블 클릭 성공
                if (!isSpeaking) {
                    Log.i("PTT", "Double click DETECTED. Attempting to join channel and speak.")
                    playSound(soundIdPttEffect) // 효과음 1
                    // playSound(soundIdPttEffect) // 효과음 2 (필요시 Handler로 딜레이)

                    awaitingDoubleClickRelease = true // 버튼 뗄 때까지 대기 상태 설정 (중요: 채널 참여 전에 설정)
                    joinAgoraChannelAndSpeak()      // 채널 참여 및 송신 시작 시도

            } else {
                    Log.w("PTT", "Double click detected but already speaking.")
                }
            } else {
                // 첫 번째 클릭이거나 시간 초과된 클릭
                Log.d("PTT", "First click or interval too long.")
            }

            lastClickTime = clickTime // 마지막 클릭 시간 업데이트
            return true // 볼륨 키 이벤트 소비
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            // 두 번째 클릭 후 떼는 것을 기다리던 중이었다면
            if (awaitingDoubleClickRelease) {
                Log.i("PTT", "Key RELEASED after double click. Stopping PTT and leaving channel.")
                stopSpeakingAndLeaveChannel() // 송신 종료 및 채널 퇴장 함수 호출
                awaitingDoubleClickRelease = false
            } else {
                // 첫 번째 클릭 후 뗀 경우 또는 다른 상황
                Log.d("PTT", "Key released (not waiting for double click release).")
            }
            return true // 볼륨 키 이벤트 소비
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun startSpeaking() {
        if (!isConnected) {
             Log.w("AgoraAction", "Cannot start speaking, not connected.")
             return
        }
        if (!isSpeaking) { // 중복 시작 방지
            isSpeaking = true
            val result = rtcEngine?.enableLocalAudio(true) // 오디오 송신 활성화
            Log.i("AgoraAction", "Attempting to ENABLE local audio (start speaking). Result: $result")
            if (result == 0) {
                updateStatus("송신 중...")
            binding.root.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            } else {
                 Log.e("AgoraAction", "Failed to enable local audio, error code: $result")
                 isSpeaking = false // 실패 시 상태 복구
                 updateStatus("송신 시작 실패")
            }
        } else {
             Log.w("AgoraAction", "startSpeaking called but already speaking.")
        }
    }

    private fun stopSpeaking() {
        if (isSpeaking) { // 중복 종료 방지
            isSpeaking = false // 상태 먼저 변경 (중요: 효과음/UI 전에)
            val result = rtcEngine?.enableLocalAudio(false) // 오디오 송신 비활성화
            Log.i("AgoraAction", "Attempting to DISABLE local audio (stop speaking). Result: $result")
            playSound(soundIdPttEffect) // PTT 종료 시 효과음 재생 (시작과 동일)

            if (result != 0) {
                 Log.e("AgoraAction", "Failed to disable local audio, error code: $result")
                 // 에러 발생해도 일단 UI는 복구 시도
            }
            // 연결 상태에 따라 기본 상태 업데이트
            if(isConnected) updateStatus("수신 대기 중...") else updateStatus("연결 끊김")
            binding.root.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)

        } else {
            // PTT 버튼을 떼는 동작(onKeyUp)은 isSpeaking=false일 때도 호출될 수 있으므로, 경고 대신 debug 레벨로 변경
            Log.d("AgoraAction", "stopSpeaking called but not in speaking state.")
        }
    }

    private fun initializeAgoraEngine() {
        // Ensure this is called only once, or properly handled if called multiple times
        if (rtcEngine != null) {
            Log.w("AgoraInit", "Agora Engine already initialized.")
            return
        }
        try {
            Log.i("AgoraInit", "Initializing Agora Engine...")
            val config = RtcEngineConfig()
            config.mContext = applicationContext // Use application context
            config.mAppId = appId
            config.mEventHandler = rtcEventHandler
             // config.mLogConfig = RtcEngineConfig.LogConfig().apply { level = Constants.LogLevel.LOG_LEVEL_INFO } // 필요시 로그 레벨 설정

            rtcEngine = RtcEngine.create(config)
            // !! IMPORTANT: Set channel profile BEFORE joining channel !!
            rtcEngine?.setChannelProfile(Constants.CHANNEL_PROFILE_COMMUNICATION)
            Log.d("AgoraSetup", "Channel Profile: COMMUNICATION")

            rtcEngine?.apply {
                enableAudio() // Enable the audio module
                setEnableSpeakerphone(true) // Use speakerphone
                setDefaultAudioRoutetoSpeakerphone(true) // Default route to speakerphone
                 Log.d("AgoraSetup", "Audio enabled, Speakerphone enabled")

                // Set audio profile for voice quality
                setAudioProfile(
                    // Constants.AUDIO_PROFILE_SPEECH_STANDARD,
                    Constants.AUDIO_PROFILE_DEFAULT, // DEFAULT가 SPEECH_STANDARD보다 나을 수 있음 (문서 확인)
                    Constants.AUDIO_SCENARIO_CHATROOM // Use CHATROOM scenario for AEC/NS etc.
                )
                 Log.d("AgoraSetup", "Audio Profile: DEFAULT, Scenario: CHATROOM")

                // Optional: Fine-tune audio processing parameters
                try {
                    // Example: Adjust AGC target level (default is usually -3 dBFS)
                    val audioParams = """{"che.audio.agc.targetlevel": -6, "che.audio.aec.enable": true, "che.audio.ns.enable": true, "che.audio.agc.enable": true}"""
                    val paramResult = setParameters(audioParams)
                    Log.i("AgoraConfig", "Applied audio parameters: $audioParams. Result: $paramResult")
                } catch (e: Exception) {
                    Log.e("AgoraConfig", "Failed to set audio parameters: ${e.message}")
                }

                // Set initial playback volume based on currentVolume variable
                try {
                    val volResult = adjustPlaybackSignalVolume(currentVolume)
                    Log.i("AgoraConfig", "Initial playback signal volume set to $currentVolume. Result: $volResult")
                } catch (e: Exception) {
                    Log.e("AgoraConfig", "Failed to set initial playback volume: ${e.message}")
                }

                 // Disable local audio capture initially
                 val enableResult = enableLocalAudio(false)
                 Log.d("AgoraSetup", "Local audio initially DISABLED. Result: $enableResult")
            }

            updateStatus("엔진 준비됨 (대기)") // 엔진 초기화 완료 상태

        } catch (e: Exception) {
            Toast.makeText(this, "Agora 엔진 초기화 오류", Toast.LENGTH_LONG).show()
            Log.e("AgoraInit", "Agora engine initialization failed", e)
            updateStatus("엔진 초기화 실패")
            // Clean up if engine creation failed mid-way
            RtcEngine.destroy()
            rtcEngine = null
        }
    }

    private fun joinChannel() {
        if (rtcEngine == null) {
             Log.e("AgoraAction", "Cannot join channel, rtcEngine is null.")
             updateStatus("엔진 없음")
             return
        }
        Log.i("AgoraAction", "Attempting to join channel: $channelName")
        // Use token, channel name. Pass null for optional info. uid 0 for auto-assignment.
        val joinResult = rtcEngine?.joinChannel(token, channelName, null, 0)
        Log.i("AgoraAction", "joinChannel called. Result: $joinResult")

        // --- 수정된 부분: joinResult null 체크 추가 ---
        // joinResult가 null이 아니고 0도 아닌 경우에만 에러 처리
        if (joinResult != null && joinResult != 0) { 
            // 이제 이 블록 안에서 joinResult는 확실히 Int 타입임
            val errorString = agoraErrorToString(joinResult) 
            Log.e("AgoraAction", "Failed to join channel immediately, error code: $joinResult ($errorString)")
            updateStatus("채널 참여 실패: $errorString")
        } else if (joinResult == null) {
             // 만약 joinResult가 null이라면 (이론상 발생하기 어려움) 로그 기록
             Log.e("AgoraAction", "joinChannel result was null, unexpected.")
        }
        // joinResult가 0이면 성공이므로 별도 처리 안 함
        // --- 수정 끝 ---
    }

    private fun checkPermissions(): Boolean {
        Log.d("Permissions", "Checking required permissions...")
        val permissionsNeeded = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        return if (permissionsNeeded.isNotEmpty()) {
            Log.w("Permissions", "Requesting permissions: ${permissionsNeeded.joinToString()}")
            ActivityCompat.requestPermissions(this, permissionsNeeded.toTypedArray(), 100)
            false // Need to request permissions
        } else {
            Log.i("Permissions", "All required permissions are already granted.")
            true // Permissions are already granted
        }
    }

    private fun updateStatus(status: String) {
        // Ensure UI updates happen on the main thread
        if (Looper.myLooper() == Looper.getMainLooper()) {
            binding.statusText.text = status
            // Log.d("StatusUpdate", status) // Log frequently, consider level
        } else {
            runOnUiThread {
        binding.statusText.text = status
                // Log.d("StatusUpdate", "$status (from background thread)")
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Log.d("Permissions", "onRequestPermissionsResult: requestCode=$requestCode")
        if (requestCode == 100) {
            // Check if all requested permissions were granted
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Log.i("Permissions", "All permissions granted by user via dialog.")
                // 권한 획득 후 Firebase 및 Agora 초기화
                setupFirebasePresence() // Firebase 먼저
            initializeAgoraEngine()
        } else {
                Log.e("Permissions", "Permissions were denied by user.")
                Toast.makeText(this, "오디오 권한이 필요합니다.", Toast.LENGTH_LONG).show()
                updateStatus("권한 거부됨")
                // Handle permission denial (e.g., show explanation, disable features)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i("MainActivity", "onDestroy called")

        // --- Firebase Presence 정리 --- (순서 중요: 리스너 먼저 제거)
        if (::database.isInitialized) { 
            if (::connectedRef.isInitialized && ::connectedListener.isInitialized) {
                 try { // 리스너 제거 시 에러 방지
                    connectedRef.removeEventListener(connectedListener)
                    Log.d("FirebasePresence", ".info/connected listener removed.")
                 } catch (e: Exception) { Log.e("FirebasePresence", "Error removing listener", e) }
             }
            // 앱 종료 시 명시적으로 오프라인 상태로 설정
             presenceRef.setValue(false) { error, _ ->
                 if (error != null) Log.e("FirebasePresence", "Failed to set presence to false on destroy.", error.toException())
                 else Log.d("FirebasePresence", "Presence set to false on destroy.")
             }
            // 특정 연결 노드 즉시 삭제 (선택적이지만 권장)
            if (myConnectionsKey != null) {
                connectionsRef.child(myConnectionsKey!!).removeValue()
                Log.d("FirebasePresence", "Specific connection node removed: $myConnectionsKey")
            }
        }
        // --- Firebase 정리 끝 ---

        // --- 기존 정리 코드 ---
        leaveAgoraChannel() // 채널 나가기 시도
        RtcEngine.destroy() // 엔진 자원 해제 (SDK 가이드라인에 따라 destroy 호출 전에 leave 해야 함)
        soundPool.release() // SoundPool 자원 해제
        rtcEngine = null // 참조 해제
        Log.i("MainActivity", "Agora Engine and SoundPool resources released.")
    }

    // --- Helper functions for converting Agora constants to strings (for logging) ---
    private fun agoraErrorToString(err: Int): String {
        return when (err) {
            Constants.ERR_OK -> "No error"
            Constants.ERR_FAILED -> "General error"
            Constants.ERR_INVALID_ARGUMENT -> "Invalid argument"
            Constants.ERR_NOT_READY -> "Not ready"
            Constants.ERR_NOT_SUPPORTED -> "Not supported"
            Constants.ERR_REFUSED -> "Refused"
            Constants.ERR_BUFFER_TOO_SMALL -> "Buffer too small"
            Constants.ERR_NOT_INITIALIZED -> "Not initialized"
            Constants.ERR_INVALID_STATE -> "Invalid state"
            Constants.ERR_NO_PERMISSION -> "No permission"
            Constants.ERR_TIMEDOUT -> "Timed out"
            Constants.ERR_CANCELED -> "Canceled"
            Constants.ERR_TOO_OFTEN -> "Too often"
            Constants.ERR_BIND_SOCKET -> "Bind socket error"
            Constants.ERR_NET_DOWN -> "Network down"
            Constants.ERR_JOIN_CHANNEL_REJECTED -> "Join channel rejected"
            Constants.ERR_LEAVE_CHANNEL_REJECTED -> "Leave channel rejected"
            Constants.ERR_ALREADY_IN_USE -> "Already in use"
            Constants.ERR_INVALID_APP_ID -> "Invalid App ID"
            Constants.ERR_INVALID_CHANNEL_NAME -> "Invalid channel name"
            Constants.ERR_INVALID_TOKEN -> "Invalid token"
            Constants.ERR_TOKEN_EXPIRED -> "Token expired"
            // Add more specific error codes from Agora documentation if needed
            -7 -> "ERR_INVALID_TOKEN / ERR_TOKEN_EXPIRED (Compatibility)"
            else -> "Unknown error ($err)"
        }
    }

    private fun agoraConnStateToString(state: Int): String {
         return when (state) {
            Constants.CONNECTION_STATE_DISCONNECTED -> "Disconnected"
            Constants.CONNECTION_STATE_CONNECTING -> "Connecting"
            Constants.CONNECTION_STATE_CONNECTED -> "Connected"
            Constants.CONNECTION_STATE_RECONNECTING -> "Reconnecting"
            Constants.CONNECTION_STATE_FAILED -> "Failed"
            else -> "Unknown state ($state)"
        }
    }

     private fun agoraConnReasonToString(reason: Int): String {
         // Refer to Agora documentation for constant values
         return when (reason) {
            Constants.CONNECTION_CHANGED_CONNECTING -> "Connecting"
            Constants.CONNECTION_CHANGED_JOIN_SUCCESS -> "Join Success"
            Constants.CONNECTION_CHANGED_INTERRUPTED -> "Interrupted"
            Constants.CONNECTION_CHANGED_BANNED_BY_SERVER -> "Banned by Server"
            Constants.CONNECTION_CHANGED_JOIN_FAILED -> "Join Failed"
            Constants.CONNECTION_CHANGED_LEAVE_CHANNEL -> "Leave Channel"
            Constants.CONNECTION_CHANGED_INVALID_APP_ID -> "Invalid App ID"
            Constants.CONNECTION_CHANGED_INVALID_CHANNEL_NAME -> "Invalid Channel Name"
            Constants.CONNECTION_CHANGED_INVALID_TOKEN -> "Invalid Token"
            Constants.CONNECTION_CHANGED_TOKEN_EXPIRED -> "Token Expired"
            Constants.CONNECTION_CHANGED_REJECTED_BY_SERVER -> "Rejected by Server"
            Constants.CONNECTION_CHANGED_SETTING_PROXY_SERVER -> "Setting Proxy Server"
            Constants.CONNECTION_CHANGED_RENEW_TOKEN -> "Renew Token"
            Constants.CONNECTION_CHANGED_CLIENT_IP_ADDRESS_CHANGED -> "Client IP Address Changed"
            Constants.CONNECTION_CHANGED_KEEP_ALIVE_TIMEOUT -> "Keep Alive Timeout"
             Constants.CONNECTION_CHANGED_REJOIN_SUCCESS -> "Rejoin Success" // Add this if applicable
             Constants.CONNECTION_CHANGED_LOST -> "Connection Lost" // Add this if applicable
            else -> "Unknown reason ($reason)"
        }
    }

    private fun setupSoundPool() {
        // --- 추가: setupSoundPool 시작 로그 ---
        Log.i("SoundPoolSetup", "setupSoundPool START") 
        Log.d("SoundPool", "Setting up SoundPool...") // 기존 로그

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(3) // 동시에 재생 가능한 사운드 수
            .setAudioAttributes(audioAttributes)
            .build()

        // 로드 완료 리스너
        var loadedCount = 0
        val totalSounds = 2 // 로드할 사운드 개수 (버튼클릭1 + PTT효과1)
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) {
                loadedCount++
                Log.i("SoundPool", "Sound ID $sampleId loaded successfully ($loadedCount/$totalSounds).")
                if (loadedCount == totalSounds) {
                    soundPoolLoaded = true
                    Log.i("SoundPool", "All sounds loaded.")
                }
            } else {
                Log.e("SoundPool", "Failed to load sound ID $sampleId, status: $status")
                // 로드 실패 시에도 앱이 죽지 않도록 처리
                if (sampleId == soundIdPttEffect) soundIdPttEffect = 0 // PTT 효과음 로드 실패 시 ID 초기화
                if (sampleId == soundIdButtonClick) soundIdButtonClick = 0
            }
        }

        // 효과음 로드
        try {
            // 안드로이드 시스템 효과음 로드는 비추천 (기기/버전별 소리 다름, 없을 수 있음)
            // 대신 직접 제공하는 짧은 클릭음 사용 권장
            // soundIdButtonClick = soundPool.load(this, android.R.raw.some_system_sound, 1)
            // 임시로 PTT 사운드와 동일하게 설정하거나, 0으로 두어 소리 안 나게 함
             soundIdButtonClick = 0 // 버튼 클릭음 임시 비활성화
             Log.w("SoundPool", "Button click sound disabled temporarily. Provide a custom sound file.")
        } catch (e: Exception) {
             Log.e("SoundPool", "Failed to load button click sound", e)
             soundIdButtonClick = 0
        }

        // PTT 시작/종료 효과음 로드 (res/raw 폴더의 ptt_start.m4a)
        try {
             val resId = resources.getIdentifier("ptt_start", "raw", packageName)
             if (resId != 0) {
                 soundIdPttEffect = soundPool.load(this, resId, 1)
                 Log.i("SoundPool", "Loading PTT sound (ptt_start) with ID: $soundIdPttEffect")
             } else {
                 Log.e("SoundPool", "PTT sound resource (ptt_start) not found in res/raw.")
                 soundIdPttEffect = 0
             }
        } catch (e: Exception) {
            Log.e("SoundPool", "Exception while loading PTT sound.", e)
            Toast.makeText(this, "PTT 효과음 로딩 실패", Toast.LENGTH_SHORT).show()
            soundIdPttEffect = 0
        }
    }

    private fun setupButtonClickListeners() {
        Log.d("Setup", "Setting up button click listeners...")
        binding.buttonUp.setOnClickListener {
            Log.d("ButtonClick", "Volume Up clicked")
            playSound(soundIdButtonClick) // 클릭음 (현재 비활성화 상태)
            adjustVolume(increase = true)
        }

        binding.buttonDown.setOnClickListener {
            Log.d("ButtonClick", "Volume Down (UI Button) clicked")
            playSound(soundIdButtonClick) // 클릭음 (현재 비활성화 상태)
            adjustVolume(increase = false)
        }

        // --- 다른 기능 버튼들에 대한 리스너 (클릭 로그 및 효과음만) ---
        val placeholderClickListener = android.view.View.OnClickListener { view ->
            playSound(soundIdButtonClick) // 클릭음 (현재 비활성화 상태)
            val buttonName = resources.getResourceEntryName(view.id)
            Log.d("ButtonClick", "$buttonName clicked (placeholder)")
            // Toast.makeText(this, "$buttonName clicked (no action)", Toast.LENGTH_SHORT).show() // 필요시 Toast 표시
        }
        binding.buttonMusic.setOnClickListener(placeholderClickListener)
        binding.buttonGrpMenu.setOnClickListener(placeholderClickListener)
        binding.buttonMon.setOnClickListener(placeholderClickListener)
        binding.buttonSel.setOnClickListener(placeholderClickListener)
        // --- 다른 기능 버튼들에 대한 리스너 끝 ---
    }

    private fun adjustVolume(increase: Boolean) {
        if (!isConnected) {
            Log.w("VolumeControl", "Cannot adjust volume, not connected to channel.")
            // Toast.makeText(this, "채널에 연결되지 않았습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        val step = 20 // 볼륨 조절 단계 (0-400 범위 기준)
        val oldVolume = currentVolume
        if (increase) {
            currentVolume = (currentVolume + step).coerceAtMost(400) // Agora 기본 최대값 400
        } else {
            currentVolume = (currentVolume - step).coerceAtLeast(0) // 최소값 0
        }

        if (oldVolume != currentVolume) {
             val result = rtcEngine?.adjustPlaybackSignalVolume(currentVolume)
             Log.i("VolumeControl", "Adjusting playback volume from $oldVolume to $currentVolume. Result: $result")
             if (result != 0) {
                 Log.e("VolumeControl", "Failed to adjust playback volume, error code: $result")
             }
             // 필요시 볼륨 상태 UI 업데이트
             // updateStatus("볼륨: ${currentVolume * 100 / 400}%")
             // Handler(Looper.getMainLooper()).postDelayed({ updateCurrentUiStatus() }, 1000)
        } else {
            Log.d("VolumeControl", "Volume already at min/max ($currentVolume).")
        }
    }

    // 필요시 현재 상태에 따라 UI 업데이트하는 함수
    /*
    private fun updateCurrentUiStatus() {
        if (isSpeaking) {
            updateStatus("송신 중...")
        } else if (isConnected) {
             updateStatus("수신 대기 중...")
        } else {
            updateStatus("연결 끊김") // 또는 다른 상태
        }
    }
    */

    private fun playSound(soundID: Int) {
        // --- 추가: playSound 함수 시작 로그 ---
        Log.d("SoundPoolPlayback", "Attempting to play sound ID: $soundID. Loaded: $soundPoolLoaded")
        if (!soundPoolLoaded) {
            Log.w("SoundPool", "SoundPool not loaded yet, cannot play sound ID $soundID.")
            return
        }
        if (soundID != 0) {
            // 파라미터: soundID, leftVolume, rightVolume, priority, loop, rate
            soundPool.play(soundID, 0.7f, 0.7f, 1, 0, 1.0f)
            Log.d("SoundPool", "Playing sound ID $soundID")
        } else {
             Log.w("SoundPool", "Attempted to play invalid or unloadable sound ID 0.")
        }
    }

    private var awaitingDoubleClickRelease = false // 두 번째 클릭 후 떼기 대기 상태

    // --- 새 함수: 채널 참여 및 송신 시작 --- (Agora 관련 로직 리팩토링 필요)
    private fun joinAgoraChannelAndSpeak() {
        if (isConnected) { // 이미 연결되어 있다면 바로 송신 시작
            Log.d("Hybrid", "Already connected to Agora channel, starting speaking directly.")
            startSpeakingActual()
        } else if (rtcEngine != null) { // 엔진은 있지만 연결 안된 경우
            Log.i("Hybrid", "Not connected to Agora channel. Attempting to join...")
            updateStatus("채널 연결 중...")
            // 채널 참여 시도 -> 성공 시 onJoinChannelSuccess 콜백에서 startSpeakingActual 호출됨
            val joinResult = rtcEngine?.joinChannel(token, channelName, null, 0)
            Log.i("AgoraAction", "joinChannel called from joinAgoraChannelAndSpeak. Result: $joinResult")
            if (joinResult != 0) {
                val errorString = agoraErrorToString(joinResult ?: -1)
                Log.e("AgoraAction", "Failed to join channel immediately: $errorString")
                updateStatus("채널 참여 실패: $errorString")
                awaitingDoubleClickRelease = false // 참여 실패 시 대기 상태 해제
            }
        } else {
            Log.e("Hybrid", "Cannot join channel, rtcEngine is null.")
            updateStatus("엔진 오류")
            awaitingDoubleClickRelease = false // 참여 불가 시 대기 상태 해제
        }
    }

    private fun startSpeakingActual() {
        if (!isSpeaking) { // 중복 시작 방지
            isSpeaking = true
            val result = rtcEngine?.enableLocalAudio(true)
            Log.i("AgoraAction", "Attempting to ENABLE local audio (start speaking). Result: $result")
            if (result == 0) {
                updateStatus("송신 중...")
                binding.root.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            } else {
                 Log.e("AgoraAction", "Failed to enable local audio, error code: $result")
                 isSpeaking = false
                 updateStatus("송신 시작 실패")
                 // 실패 시 채널 퇴장 고려?
                 // leaveAgoraChannel()
            }
        } else {
             Log.w("AgoraAction", "startSpeakingActual called but already speaking.")
        }
    }

    // PTT 종료 시 호출 (송신 중단 및 채널 퇴장) - 이름 변경 및 로직 수정
    private fun stopSpeakingAndLeaveChannel() {
        if (isSpeaking) {
            isSpeaking = false
            val result = rtcEngine?.enableLocalAudio(false)
            Log.i("AgoraAction", "Attempting to DISABLE local audio (stop speaking). Result: $result")
            playSound(soundIdPttEffect) // 종료 효과음
            if (result != 0) Log.e("AgoraAction", "Failed to disable local audio, error code: $result")
            // 송신 중단 후 즉시 채널 퇴장
            leaveAgoraChannel()
             // UI는 leave 이후 또는 onLeaveChannel 콜백에서 업데이트
            updateStatus("채널 퇴장 중...")
        } else {
            // 이미 송신 중이 아닐 때 키를 떼는 경우 (예: 첫 클릭 후 뗄 때)
            // 채널에 연결되어 있다면 나갈 필요 없음 (수신 대기)
            Log.d("Hybrid", "stopSpeakingAndLeaveChannel called but was not speaking.")
            // leaveAgoraChannel() // -> 여기서 나가면 안됨. 수신해야 함.
        }
    }
    
    // 연결 끊김 등 예외 상황에서 강제로 송신 중단 (채널 퇴장은 별개)
    private fun forceStopSpeaking() {
        if (isSpeaking) {
            Log.w("AgoraAction", "Forcing PTT stop due to connection issue or error.")
            isSpeaking = false
            rtcEngine?.enableLocalAudio(false)
            playSound(soundIdPttEffect) // 종료 효과음
            if (isConnected) updateStatus("수신 대기 중...") else updateStatus("연결 끊김")
            binding.root.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            awaitingDoubleClickRelease = false // 강제 종료 시 대기 상태 해제
        }
    }

    // Agora 채널 퇴장 함수 (재사용 가능하게 분리)
    private fun leaveAgoraChannel() {
        if (isConnected) { // 또는 rtcEngine != null && isConnected 로 더 명확히?
            Log.i("AgoraAction", "Leaving Agora channel...")
            val leaveResult = rtcEngine?.leaveChannel()
            Log.d("AgoraAction", "leaveChannel called. Result: $leaveResult")
            // isConnected = false; // onLeaveChannel 콜백에서 처리
            // updateStatus("채널 퇴장함") // onLeaveChannel 콜백에서 처리
        } else {
             Log.d("AgoraAction", "Not connected to Agora channel, no need to leave.")
        }
    }
    // --- Agora 채널 참여/퇴장 및 송신 시작/종료 로직 끝 ---

    // --- Firebase Presence 시스템 설정 함수 --- (수정됨)
    private fun setupFirebasePresence() {
        if (::database.isInitialized) { // 중복 초기화 방지
            Log.w("FirebasePresence", "Firebase Presence already set up.")
            return
        }
        try {
            database = Firebase.database
            connectedRef = database.getReference(".info/connected")
            connectionsRef = database.getReference("connections/$myUserId")
            presenceRef = database.getReference("presence/$channelName/$myUserId")
            Log.d("FirebasePresence", "Setting up Firebase Presence for user: $myUserId at channel: $channelName")

            connectedListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val connected = snapshot.getValue(Boolean::class.java) ?: false
                    if (connected) {
                        Log.i("FirebasePresence", "Firebase connected.")
                        // 새 연결 노드 생성
                        val con = connectionsRef.push()
                        myConnectionsKey = con.key

                        // 연결 끊김 시 해당 연결 노드 자동 삭제 설정
                        con.onDisconnect().removeValue { error, _ ->
                            if (error != null) Log.e("FirebasePresence", "onDisconnect().removeValue failed for connection node.", error.toException())
                            else Log.d("FirebasePresence", "onDisconnect().removeValue set for connection node: $myConnectionsKey")
                        }
                        // 현재 연결 정보 기록 (단순 true)
                        con.setValue(true)
                        // 사용자 Presence 상태를 온라인(true)으로 설정, 연결 끊김 시 자동 false 설정
                        presenceRef.onDisconnect().setValue(false) { error, _ ->
                             if (error != null) Log.e("FirebasePresence", "onDisconnect().setValue(false) failed for presence node.", error.toException())
                             else Log.d("FirebasePresence", "onDisconnect().setValue(false) set for presence node.")
                        }
                        presenceRef.setValue(true) { error, _ ->
                             if (error != null) Log.e("FirebasePresence", "Failed to set presence to true.", error.toException())
                             else Log.d("FirebasePresence", "Presence set to true.")
                        }
                        // Firebase 연결 시 기본 상태 업데이트 (Agora 연결 상태와는 별개)
                         if (!isConnected) updateStatus("온라인 (채널 대기)")

                    } else {
                        Log.w("FirebasePresence", "Firebase disconnected.")
                        // 명시적으로 오프라인 설정 (앱이 백그라운드로 가거나 할 때도 호출될 수 있음)
                        presenceRef.setValue(false)
                         if (!isConnected) updateStatus("오프라인")
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("FirebasePresence", "Listener for .info/connected was cancelled", error.toException())
                     updateStatus("Firebase 오류")
                }
            }
            // 연결 상태 리스너 부착
            connectedRef.addValueEventListener(connectedListener)

        } catch (e: Exception) {
            Log.e("FirebaseSetup", "Failed to initialize Firebase Database or Presence", e)
            Toast.makeText(this, "Firebase 초기화 실패", Toast.LENGTH_SHORT).show()
            updateStatus("Firebase 오류")
        }
    }
    // --- Firebase Presence 함수 끝 ---
} 