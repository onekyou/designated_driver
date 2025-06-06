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
import java.util.*
import android.util.Log
import android.media.AudioAttributes
import android.media.SoundPool
import android.view.View
import android.widget.SeekBar
import com.google.firebase.database.*
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import android.content.Context
import android.media.AudioManager
import android.os.Vibrator
import android.view.HapticFeedbackConstants

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var rtcEngine: RtcEngine? = null
    private var isConnected = false
    private var isSpeaking = false
    private var isAgoraConnected = false // 아고라 연결 상태 추적
    private var isFirstClick = true // 첫 번째 클릭 여부 추적
    private var isAutoConnected = false // 자동 연결 여부 추적
    private var currentVolume: Int = 50 // 현재 볼륨 (50% - 기본값으로 설정)
    
    private val appId = "a719c12f1d884f778cb768be0a59f819"
    private val channelName = "driver_channel"

    // 더블 클릭 감지를 위한 변수들
    private var lastClickTime: Long = 0
    private val doubleClickTimeDelta: Long = 300 // 더블 클릭 간격 (밀리초)
    
    // 효과음 관련 변수
    private lateinit var soundPool: SoundPool
    private var soundStart: Int = 0
    private var soundEnd: Int = 0
    private var effectsVolume: Float = 0.125f // 기본값 50%/150 = 0.333

    // Firebase 관련 변수
    private lateinit var database: FirebaseDatabase
    private lateinit var driverStatusRef: DatabaseReference
    private lateinit var callRequestsRef: DatabaseReference
    private lateinit var channelStatusRef: DatabaseReference // 채널 상태 참조
    private var userId: String = "driver_${UUID.randomUUID().toString().substring(0, 8)}" // 임시 기사 ID
    private var callListener: ValueEventListener? = null
    private var channelStatusListener: ValueEventListener? = null // 채널 상태 리스너

    private val requiredPermissions = arrayOf(
        Manifest.permission.RECORD_AUDIO
    )

    private val userList = mutableListOf<Int>()

    private val rtcEventHandler = object : IRtcEngineEventHandler() {
        override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
            runOnUiThread {
                isConnected = true
                isAgoraConnected = true
                updateStatus(getString(R.string.channel_connected))
                binding.connectButton.text = getString(R.string.disconnect_call)
                // 통화 시작 상태를 Firebase에 업데이트
                updateDriverStatus("on_call")
            }
        }

        override fun onLeaveChannel(stats: RtcStats?) {
            runOnUiThread {
                isConnected = false
                isAgoraConnected = false
                updateStatus(getString(R.string.channel_disconnected))
                binding.connectButton.text = getString(R.string.connect_call)
                // 대기 상태로 Firebase 업데이트
                updateDriverStatus("standby")
            }
        }

        override fun onUserJoined(uid: Int, elapsed: Int) {
            runOnUiThread {
                userList.add(uid)
                Log.d("AgoraUsers", "사용자 접속: $uid, 현재 사용자 목록: $userList")
                updateUserListUI()
            }
        }

        override fun onUserOffline(uid: Int, reason: Int) {
            runOnUiThread {
                userList.remove(uid)
                Log.d("AgoraUsers", "사용자 퇴장: $uid, 현재 사용자 목록: $userList")
                updateUserListUI()
            }
        }

        override fun onAudioVolumeIndication(speakers: Array<out AudioVolumeInfo>?, totalVolume: Int) {
            runOnUiThread {
                speakers?.forEach { speaker ->
                    if (speaker.uid != 0 && speaker.volume > 0) {
                        // 상대방이 말하고 있는 경우
                        if (!isSpeaking) {
                            updateStatus(getString(R.string.receiving))
                        }
                    }
                }
            }
        }

        override fun onError(err: Int) {
            runOnUiThread {
                val errorMsg = when (err) {
                    110 -> "토큰 인증 오류 (110): 앱 ID 또는 토큰이 잘못되었습니다"
                    Constants.ERR_INVALID_APP_ID -> "잘못된 앱 ID"
                    Constants.ERR_TOKEN_EXPIRED -> "토큰이 만료됨"
                    Constants.ERR_NOT_INITIALIZED -> "SDK 초기화 실패"
                    Constants.ERR_REFUSED -> "서버 연결 거부됨"
                    else -> getString(R.string.error_occurred, err)
                }
                updateStatus(errorMsg)
                Log.e("AgoraError", "Error code: $err, Message: $errorMsg")
                
                // 토큰 관련 오류면 자동 재연결 시도
                if (err == 110 || err == Constants.ERR_TOKEN_EXPIRED) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        Log.i("AgoraReconnect", "토큰 오류로 재연결 시도")
                        leaveAndRejoinChannel()
                    }, 3000)
                }
            }
        }

        override fun onConnectionStateChanged(state: Int, reason: Int) {
            runOnUiThread {
                when (state) {
                    Constants.CONNECTION_STATE_CONNECTED -> updateStatus(getString(R.string.connected))
                    Constants.CONNECTION_STATE_CONNECTING -> updateStatus(getString(R.string.connecting))
                    Constants.CONNECTION_STATE_DISCONNECTED -> updateStatus(getString(R.string.disconnected))
                    Constants.CONNECTION_STATE_FAILED -> updateStatus(getString(R.string.connection_failed))
                }
            }
        }
    }

    private lateinit var vibrator: Vibrator
    private lateinit var audioManager: AudioManager // 시스템 오디오 매니저

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 오디오 매니저 초기화
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        initSoundPool()
        initFirebase()
        
        // 초기 볼륨 설정 (기본값으로)
        currentVolume = 50 // 볼륨 50%로 설정
        setupVolumeControl()
        
        // 통화 연결/해제 버튼 설정
        binding.connectButton.setOnClickListener {
            if (!isAgoraConnected) {
                // 아고라 연결이 되어 있지 않다면 연결 시작
                if (rtcEngine == null) {
                    initializeAgoraEngine()
                } else {
                    joinChannel()
                }
            } else {
                // 연결되어 있다면 연결 해제
                leaveChannel()
            }
        }
        
        // userListTextView 설정
        binding.userListTextView.visibility = View.VISIBLE
        binding.userListTextView.text = getString(R.string.no_users_connected)
        
        // 앱 시작 시 Agora 초기화만 하고 연결은 하지 않음
        if (checkPermissions()) {
            initializeAgoraEngine()
        }

        // 진동 서비스 초기화
        @Suppress("DEPRECATION")
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }
    
    private fun initFirebase() {
        // Firebase 초기화
        database = FirebaseDatabase.getInstance()
        driverStatusRef = database.getReference("drivers").child(userId)
        callRequestsRef = database.getReference("call_requests")
        channelStatusRef = database.getReference("channel_status").child(channelName) // 채널 상태 참조 추가
        
        // 기사 상태 등록
        val driverInfo = HashMap<String, Any>()
        driverInfo["status"] = "standby"
        driverInfo["last_active"] = ServerValue.TIMESTAMP
        driverInfo["device_id"] = android.os.Build.MODEL
        
        driverStatusRef.setValue(driverInfo)
            .addOnSuccessListener {
                Log.d("Firebase", "기사 정보 등록 성공: $userId")
                updateStatus(getString(R.string.waiting_for_connection))
            }
            .addOnFailureListener { e ->
                Log.e("Firebase", "기사 정보 등록 실패", e)
                updateStatus(getString(R.string.connection_failed))
            }
            
        // 앱 종료 시 자동 삭제 설정
        driverStatusRef.onDisconnect().removeValue()
        
        // 호출 리스너 등록
        startListeningForCalls()
        
        // 채널 상태 리스너 등록
        startListeningForChannelStatus()
    }
    
    private fun startListeningForCalls() {
        // 기존 리스너 해제
        callListener?.let {
            callRequestsRef.removeEventListener(it)
        }
        
        // 새로운 호출에 대한 리스너 등록
        callListener = callRequestsRef.orderByChild("status").equalTo("new").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) return
                
                lifecycleScope.launch {
                    for (callSnapshot in snapshot.children) {
                        val call = callSnapshot.getValue(HashMap::class.java)
                        call?.let {
                            // 호출 정보 표시
                            val customerPhone = call["customer_phone"]?.toString() ?: getString(R.string.no_phone)
                            val pickupLocation = call["pickup_location"]?.toString() ?: getString(R.string.no_location)
                            
                            // 이미 처리 중이거나 완료된 호출은 무시
                            val status = call["status"]?.toString() ?: "unknown"
                            if (status == "new") {
                                // 새 호출 알림
                                updateStatus(getString(R.string.new_call, customerPhone, pickupLocation))
                                Toast.makeText(this@MainActivity, getString(R.string.new_call_toast, customerPhone), Toast.LENGTH_LONG).show()
                                
                                // 효과음 재생
                                playEffectSound(soundStart)
                                delay(300)
                                playEffectSound(soundStart)
                            }
                        }
                    }
                }
            }
            
            override fun onCancelled(error: DatabaseError) {
                Log.e("Firebase", getString(R.string.call_monitoring_error, error.message))
            }
        })
    }
    
    private fun startListeningForChannelStatus() {
        // 기존 리스너 해제
        channelStatusListener?.let {
            channelStatusRef.removeEventListener(it)
        }
        
        // 채널 상태 리스너 등록
        channelStatusListener = channelStatusRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    // 채널 상태 정보가 없으면 무시
                    return
                }
                
                val isActive = snapshot.child("active").getValue(Boolean::class.java) ?: false
                val activatedBy = snapshot.child("activated_by").getValue(String::class.java) ?: ""
                val timestamp = snapshot.child("timestamp").getValue(Long::class.java) ?: 0L
                
                Log.d("ChannelStatus", getString(R.string.channel_status_change, isActive, activatedBy))
                
                // 현재 시간과 타임스탬프 비교 (30초 이내 활성화된 경우만 처리)
                val currentTime = System.currentTimeMillis()
                val isRecent = (currentTime - timestamp) < 30000 // 30초 이내
                
                if (isActive && isRecent && activatedBy != userId && !isAgoraConnected) {
                    // 다른 사용자가 채널을 활성화했고, 내가 연결되어 있지 않으면 자동 연결
                    Log.d("ChannelStatus", getString(R.string.other_user_channel_activation, activatedBy))
                    
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, getString(R.string.other_user_detected_toast), Toast.LENGTH_SHORT).show()
                        updateStatus(getString(R.string.other_user_detected))
                        
                        // 자동 연결 시작 효과음 재생 - 두 번 재생
                        playEffectSound(soundStart)
                        Handler(Looper.getMainLooper()).postDelayed({
                            playEffectSound(soundStart)
                        }, 500)
                        
                        isAutoConnected = true
                        
                        // 아고라 초기화 및 연결
                        if (rtcEngine == null) {
                            initializeAgoraEngine()
                        } else {
                            joinChannel()
                        }
                    }
                } else if (!isActive && isAutoConnected && isAgoraConnected) {
                    // 채널이 비활성화되고 자동 연결 상태이면 연결 해제
                    Log.d("ChannelStatus", getString(R.string.channel_deactivation_detected))
                    
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, getString(R.string.channel_deactivation_toast), Toast.LENGTH_SHORT).show()
                        
                        // 자동 연결 종료 효과음 재생
                        playEffectSound(soundEnd)
                        
                        leaveChannel()
                        isAutoConnected = false
                    }
                }
            }
            
            override fun onCancelled(error: DatabaseError) {
                Log.e("ChannelStatus", getString(R.string.channel_status_monitoring_error, error.message))
            }
        })
    }
    
    private fun updateDriverStatus(status: String) {
        val updates = HashMap<String, Any>()
        updates["status"] = status
        updates["last_active"] = ServerValue.TIMESTAMP
        
        driverStatusRef.updateChildren(updates)
            .addOnFailureListener { e ->
                Log.e("Firebase", "상태 업데이트 실패: ${e.message}")
            }
    }
    
    private fun updateChannelStatus(isActive: Boolean) {
        val updates = HashMap<String, Any>()
        updates["active"] = isActive
        updates["activated_by"] = userId
        updates["timestamp"] = ServerValue.TIMESTAMP
        
        channelStatusRef.updateChildren(updates)
            .addOnSuccessListener {
                Log.d("ChannelStatus", "채널 상태 업데이트 성공: active=$isActive")
            }
            .addOnFailureListener { e ->
                Log.e("ChannelStatus", "채널 상태 업데이트 실패: ${e.message}")
            }
    }
    
    private fun initSoundPool() {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
            
        soundPool = SoundPool.Builder()
            .setMaxStreams(5)
            .setAudioAttributes(audioAttributes)
            .build()
            
        // 효과음 로드 (Raw 폴더의 ptt_start.m4a 파일 사용)
        soundStart = soundPool.load(this, R.raw.ptt_start, 1)
        soundEnd = soundPool.load(this, R.raw.ptt_start, 1)
        
        // 초기 효과음 볼륨 설정 (두 배로 증폭)
        effectsVolume = (currentVolume / 75f).coerceAtMost(1.0f)
    }

    private fun setupVolumeControl() {
        // 초기 볼륨 표시 업데이트
        updateVolumeText()
        updateVolumeProgressBar()
        
        // 볼륨 감소 버튼
        binding.volumeDownButton.setOnClickListener {
            if (currentVolume > 0) {
                currentVolume = maxOf(0, currentVolume - 5) // 5%씩 감소, 최소 0%
                updateVolume()
            }
        }
        
        // 볼륨 증가 버튼
        binding.volumeUpButton.setOnClickListener {
            if (currentVolume < 150) {
                currentVolume = minOf(150, currentVolume + 5) // 5%씩 증가, 최대 150%
                updateVolume()
            }
        }
        
        // 볼륨 프로그레스바 설정
        binding.volumeProgressBar.apply {
            max = 150 // 최대값 150%
            progress = currentVolume // 현재 볼륨으로 초기화
            
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        currentVolume = progress
                        updateVolumeText()
                        
                        // 실시간으로 볼륨 적용 (드래그 중에도)
                        applyVolumeToRtcEngine()
                    }
                }
                
                override fun onStartTrackingTouch(seekBar: SeekBar?) {
                    // 필요한 경우 구현
                }
                
                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    // 시크바 조작 완료 시 볼륨 적용 및 피드백
                    updateVolume()
                }
            })
        }
    }
    
    private fun updateVolume() {
        // 볼륨 텍스트 업데이트
        updateVolumeText()
        // 볼륨 프로그레스바 업데이트
        updateVolumeProgressBar()
        
        // 아고라 엔진에 볼륨 설정
        applyVolumeToRtcEngine()
        
        // 햅틱 피드백
        binding.root.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        
        // 볼륨 설정 상태 알림
        Toast.makeText(this, getString(R.string.volume_percent, currentVolume), Toast.LENGTH_SHORT).show()
    }
    
    private fun applyVolumeToRtcEngine() {
        rtcEngine?.let {
            try {
                // 볼륨 설정 전 로그
                Log.i("AgoraVolume", "볼륨 설정 시도: $currentVolume%")
                
                // 재생 볼륨만 조절 (송신 볼륨은 조절하지 않음)
                it.adjustPlaybackSignalVolume(currentVolume)
                it.adjustAudioMixingVolume(currentVolume)
                // 송신 볼륨은 조절하지 않음 (상대방에게 영향 없음)
                
                // 효과음 볼륨도 함께 조절 (두 배로 증폭하되 최대 1.0을 초과하지 않도록)
                val effectVolume = (currentVolume / 75f).coerceAtMost(1.0f)
                updateEffectsVolume(effectVolume)
                
                Log.i("AgoraVolume", "재생 볼륨만 설정 성공: $currentVolume%, 효과음 볼륨: $effectVolume (2배 증폭)")
                
                // 추가 시도: setParameters를 통한 볼륨 설정 (재생 볼륨만)
                try {
                    val volumeParams = "{\"che.audio.output.volume\": $currentVolume, \"che.audio.playback_volume\": $currentVolume}"
                    it.setParameters(volumeParams)
                    Log.i("AgoraVolume", "볼륨 파라미터 설정: $volumeParams")
                } catch (e: Exception) {
                    Log.e("AgoraVolume", "볼륨 파라미터 설정 실패: ${e.message}")
                }
            } catch (e: Exception) {
                Log.e("AgoraVolume", "볼륨 설정 실패: ${e.message}")
                Toast.makeText(this, "볼륨 설정 실패: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } ?: run {
            // rtcEngine이 null인 경우에도 효과음 볼륨은 조절
            val effectVolume = (currentVolume / 75f).coerceAtMost(1.0f)
            updateEffectsVolume(effectVolume)
            Log.w("AgoraVolume", "rtcEngine이 초기화되지 않아 효과음 볼륨만 조절했습니다: $effectVolume (2배 증폭)")
        }
    }
    
    private fun updateEffectsVolume(volume: Float) {
        try {
            // SoundPool 효과음 볼륨 저장 (0.0-1.0 범위)
            effectsVolume = volume.coerceIn(0f, 1f)
            Log.i("EffectsVolume", "효과음 볼륨 설정: $effectsVolume")
        } catch (e: Exception) {
            Log.e("EffectsVolume", "효과음 볼륨 설정 실패: ${e.message}")
        }
    }
    
    private fun updateVolumeText() {
        binding.volumeTextView.text = getString(R.string.volume_percent, currentVolume)
    }
    
    private fun updateVolumeProgressBar() {
        binding.volumeProgressBar.progress = currentVolume
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
            val clickTime = System.currentTimeMillis()
            
                if (isFirstClick) {
                    // 첫 번째 클릭일 경우 아고라 연결 시작
                    isFirstClick = false
                    updateStatus(getString(R.string.connecting))
                    
                    if (!isAgoraConnected) {
                        // 아고라 연결이 되어 있지 않다면 연결 시작
                        if (rtcEngine == null) {
                            initializeAgoraEngine()
            } else {
                            joinChannel()
                        }
                    }
                    
                    // 두 번째 클릭 대기를 위해 상태 업데이트
                    lastClickTime = clickTime
                } else if (clickTime - lastClickTime < doubleClickTimeDelta) {
                    // 두 번째 클릭일 경우 송신 시작
                    if (isAgoraConnected && !isSpeaking) {
                        startSpeaking()
                        // 효과음 두 번 재생 (간격을 늘려 확실히 두 번 재생되도록 함)
                        playEffectSound(soundStart)
                        Handler(Looper.getMainLooper()).postDelayed({
                            playEffectSound(soundStart)
                        }, 500) // 간격을 500ms로 늘림
                    }
                }
                
                lastClickTime = clickTime
                return true
            }
            KeyEvent.KEYCODE_VOLUME_UP -> {
                // 볼륨 업 키를 눌렀을 때 볼륨 증가
                if (currentVolume < 150) {
                    currentVolume = minOf(150, currentVolume + 5)
                    updateVolume()
            }
            return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (isSpeaking) {
                stopSpeaking()
            }
            
            // 자동 연결된 상태가 아닐 때만 연결 해제 (수동으로 연결한 경우만)
            if (isAgoraConnected && !isAutoConnected) {
                leaveChannel()
                // 효과음 한 번만 재생
                playEffectSound(soundEnd)
                
                // 채널 상태 업데이트 - 비활성화
                updateChannelStatus(false)
            }
            
            // 상태 초기화
            isFirstClick = true
            
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun startSpeaking() {
        if (!isSpeaking) {
            isSpeaking = true
            rtcEngine?.enableLocalAudio(true)
            binding.statusText.text = getString(R.string.transmitting)
            binding.root.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            Toast.makeText(this, getString(R.string.ptt_start_guide), Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopSpeaking() {
        if (isSpeaking) {
            isSpeaking = false
            rtcEngine?.enableLocalAudio(false)
            updateStatus(getString(R.string.waiting_for_reception))
            binding.root.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            Toast.makeText(this, getString(R.string.ptt_end), Toast.LENGTH_SHORT).show()
        }
    }

    private fun initializeAgoraEngine() {
        try {
            Log.i("AgoraInit", "아고라 엔진 초기화 시작: appId=$appId")
            
            val config = RtcEngineConfig()
            config.mContext = baseContext
            config.mAppId = appId
            config.mEventHandler = rtcEventHandler

            rtcEngine = RtcEngine.create(config)
            Log.i("AgoraInit", "아고라 엔진 생성 성공")
            
            // 아고라 엔진 생성 후 잠시 지연
            Thread.sleep(500)
            
            rtcEngine?.apply {
                // 추가 로깅
                Log.i("AgoraConfig", "아고라 설정 구성 시작")
                
                setChannelProfile(Constants.CHANNEL_PROFILE_COMMUNICATION)
                enableAudio()
                setEnableSpeakerphone(true)
                setDefaultAudioRoutetoSpeakerphone(true)
                enableLocalAudio(false)
                
                // 음성 처리 모드 설정
                setAudioProfile(
                    Constants.AUDIO_PROFILE_SPEECH_STANDARD, // 음성에 최적화된 프로파일로 변경
                    Constants.AUDIO_SCENARIO_CHATROOM  // 채팅룸 시나리오로 변경 (에코 억제에 최적화)
                )

                // --- 추가: 에코 억제 강화 및 기타 오디오 설정 ---
                try {
                    // 고급 오디오 파라미터 설정
                    val audioParams = """
                    {
                        "che.audio.aec.enable": true, 
                        "che.audio.ns.enable": true, 
                        "che.audio.agc.enable": true,
                        "che.audio.aec.mode": 2,
                        "che.audio.ns.mode": 2,
                        "che.audio.agc.mode": 1,
                        "che.audio.aec.param1": 300,
                        "che.audio.aec.param2": 6,
                        "che.audio.reverb.suppress_level": 3,
                        "che.audio.output.volume": 50,
                        "che.audio.playback_volume": 50
                    }
                    """.trimIndent()
                    setParameters(audioParams)
                    Log.i("AgoraConfig", "에코 억제 설정 적용: $audioParams")
                    
                    // 추가 장치 특화 설정
                    setParameters("{\"che.audio.custom.aec\": true}")
                    setParameters("{\"che.audio.custom.ns\": true}")
                    
                    // 울림 감지 및 억제 정도 설정
                    setParameters("{\"che.audio.aec.complexity\": 2}") // 고급 에코 제거 알고리즘 사용
                    setParameters("{\"che.audio.aec.lowcplx\": 0}")   // 저복잡도 모드 비활성화
                    
                    Log.i("AgoraConfig", "울림 억제 설정 적용 완료")
                } catch (e: Exception) {
                    Log.e("AgoraConfig", "오디오 파라미터 설정 실패: ${e.message}")
                }
                
                // --- 볼륨 명시적으로 설정
                try {
                    // 볼륨 설정 시도 (재생 볼륨만)
                    Log.i("AgoraConfig", "재생 볼륨 설정 시도 ($currentVolume%)")
                    adjustPlaybackSignalVolume(currentVolume)
                    adjustAudioMixingVolume(currentVolume)
                    
                    // 송신 볼륨은 기본값(100)으로 설정
                    adjustRecordingSignalVolume(100)
                    Log.i("AgoraConfig", "송신 볼륨은 기본값(100%)으로 고정")
                    
                    // 현재 오디오 상태 확인
                    val params = getParameters("che.audio.playback_volume")
                    Log.i("AgoraConfig", "현재 오디오 파라미터: $params")
                    
                    Log.i("AgoraConfig", "초기 재생 볼륨 설정: $currentVolume%")
                } catch (e: Exception) {
                    Log.e("AgoraConfig", "볼륨 초기화 실패: ${e.message}")
                }
                
                Log.i("AgoraConfig", "아고라 설정 완료, 채널 참여 준비 완료")
                // --- 볼륨 조정 끝 ---
            }

            // 효과음 볼륨 초기화 (두 배로 증폭)
            updateEffectsVolume((currentVolume / 75f).coerceAtMost(1.0f))

            // 아고라 엔진 초기화 후 현재 볼륨 적용 - 딜레이 후 여러 번 시도
            Handler(Looper.getMainLooper()).postDelayed({
                applyVolumeToRtcEngine()
                Log.i("AgoraConfig", "엔진 초기화 후 볼륨 설정 (딜레이 1초)")
            }, 1000)
            
            Handler(Looper.getMainLooper()).postDelayed({
                applyVolumeToRtcEngine()
                Log.i("AgoraConfig", "엔진 초기화 후 볼륨 설정 (딜레이 2초)")
            }, 2000)

            // 초기화 후 바로 채널 연결하지 않음 (버튼으로 연결)
            updateStatus(getString(R.string.initialization_complete))
            
        } catch (e: Exception) {
            val errorMsg = "Agora 엔진 초기화 실패: ${e.message}"
            Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
            Log.e("AgoraInit", errorMsg, e) // 초기화 실패 시 로그 추가
            
            // 초기화 실패 시 재시도 로직
            Handler(Looper.getMainLooper()).postDelayed({
                Toast.makeText(this, "아고라 엔진 재초기화 시도중...", Toast.LENGTH_SHORT).show()
                initializeAgoraEngine()
            }, 5000)
        }
    }

    private fun joinChannel() {
        try {
            // 새로 생성한 유효한 토큰 사용
            val validToken = "007eJxTYNg0PeF18EVH3WXzVr+3kn8jqlJRe3vXLrvsdfU81ZOOdBkpMCSaG1omGxqlGaZYWJikmZtbJCeZm1kkpRokmlqmWRha1td+S28IZGS4tvMCIyMDBIL4fAwpRZllqUXxyRmJeXmpOQwMAA6UJVQ="
            Log.i("AgoraJoin", "새로운 토큰으로 채널 연결 시도: $channelName, 앱 ID: $appId")
            rtcEngine?.joinChannel(validToken, channelName, "", 0)
            updateStatus(getString(R.string.connecting))
            
            // 채널 상태 업데이트 - 활성화
            updateChannelStatus(true)

            // 채널 참여 후 현재 설정된 볼륨 적용 - 여러 번 시도
            Handler(Looper.getMainLooper()).post {
                applyVolumeToRtcEngine()
                Log.i("AgoraChannel", "채널 참여 후 볼륨 설정 1차: $currentVolume%")
            }
            
            Handler(Looper.getMainLooper()).postDelayed({
                applyVolumeToRtcEngine()
                Log.i("AgoraChannel", "채널 참여 후 볼륨 설정 2차: $currentVolume%")
            }, 1000)
            
            Handler(Looper.getMainLooper()).postDelayed({
                applyVolumeToRtcEngine()
                Log.i("AgoraChannel", "채널 참여 후 볼륨 설정 3차: $currentVolume%")
            }, 2000)
        } catch (e: Exception) {
            val msg = "채널 참여 실패: ${e.message}"
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            Log.e("AgoraJoin", msg, e)
        }
    }
    
    private fun leaveChannel() {
        try {
            rtcEngine?.leaveChannel()
            updateStatus(getString(R.string.channel_disconnected))
            isAgoraConnected = false
            isAutoConnected = false // 자동 연결 상태도 해제
            binding.connectButton.text = getString(R.string.connect_call)
            Toast.makeText(this, getString(R.string.call_ended), Toast.LENGTH_SHORT).show()
            
            // 수동으로 연결 해제한 경우에만 채널 상태 업데이트
            if (!isAutoConnected) {
                updateChannelStatus(false)
            }
        } catch (e: Exception) {
            Log.e("AgoraLeave", "채널 나가기 실패: ${e.message}", e)
        }
    }
    
    private fun leaveAndRejoinChannel() {
        try {
            rtcEngine?.leaveChannel()
            Toast.makeText(this, "채널 재연결 시도 중...", Toast.LENGTH_SHORT).show()
            
            // 잠시 후 재연결
            Handler(Looper.getMainLooper()).postDelayed({
                joinChannel()
            }, 1000)
        } catch (e: Exception) {
            Log.e("AgoraReconnect", "재연결 실패: ${e.message}", e)
        }
    }

    private fun checkPermissions(): Boolean {
        val permissionsToRequest = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest, 100)
            return false
        }
        return true
    }

    private fun updateStatus(status: String) {
        binding.statusText.text = status
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            initializeAgoraEngine()
        } else {
            Toast.makeText(this, "권한이 필요합니다", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isAgoraConnected) {
        rtcEngine?.leaveChannel()
            
            // 앱 종료 시 채널 상태 비활성화
            updateChannelStatus(false)
        }
        RtcEngine.destroy()
        soundPool.release()
        
        // Firebase 리스너 해제
        callListener?.let {
            callRequestsRef.removeEventListener(it)
        }
        
        channelStatusListener?.let {
            channelStatusRef.removeEventListener(it)
        }
        
        // Firebase 상태 삭제 (이미 onDisconnect에 등록되어 있지만 확실히 하기 위해)
        driverStatusRef.removeValue()
    }

    private fun updateUserListUI() {
        // userList를 기반으로 UI를 업데이트
        val userListText = if (userList.isEmpty()) 
            getString(R.string.no_users_connected) 
        else 
            getString(R.string.connected_users, userList.joinToString(", "))
            
        binding.userListTextView.text = userListText
        binding.userListTextView.visibility = View.VISIBLE
        Log.d("AgoraUsers", "UI 업데이트: $userListText")
    }

    // 볼륨이 반영된 효과음 재생 헬퍼 메소드
    private fun playEffectSound(soundId: Int, loop: Int = 0) {
        soundPool.play(soundId, effectsVolume, effectsVolume, 1, loop, 1.0f)
    }
} 