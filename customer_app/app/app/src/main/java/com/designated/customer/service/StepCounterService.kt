package com.designated.customer.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import com.designated.customer.data.repository.StepRepository
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.DetectedActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 만보기 센서를 관리하는 서비스
 */
class StepCounterService(
    private val context: Context,
    private val repository: StepRepository
) : SensorEventListener {

    private val sensorManager: SensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    // TYPE_STEP_COUNTER: 부팅 이후 총 걸음수 (정확하지만 느림)
    private val stepCounterSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

    // TYPE_STEP_DETECTOR: 걸음 감지 즉시 이벤트 발생 (빠름)
    private val stepDetectorSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)

    private val prefs: SharedPreferences =
        context.getSharedPreferences("step_counter_prefs", Context.MODE_PRIVATE)

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // 센서 시작 시점의 걸음 수 (일일 리셋용)
    private var initialSteps = 0

    // 현재 걸음 수
    private val _currentSteps = MutableStateFlow(0)
    val currentSteps: StateFlow<Int> = _currentSteps

    // 세션 기능 - 임시 카운터 (사용자가 원할 때 리셋 가능)
    private var sessionStartSteps = 0
    private val _sessionSteps = MutableStateFlow(0)
    val sessionSteps: StateFlow<Int> = _sessionSteps

    private val _isSessionActive = MutableStateFlow(false)
    val isSessionActive: StateFlow<Boolean> = _isSessionActive

    // DB 저장 최적화를 위한 변수
    private var lastSavedSteps = 0
    private var lastSaveTime = 0L
    private val saveInterval = 3000L // 3초마다 저장
    private val saveStepThreshold = 5 // 5걸음 이상 차이나면 저장

    // 센서 사용 가능 여부
    private val _isSensorAvailable = MutableStateFlow(stepCounterSensor != null || stepDetectorSensor != null)
    val isSensorAvailable: StateFlow<Boolean> = _isSensorAvailable

    // Activity Recognition - 활동 상태 추적
    private var isWalkingOrRunning = true // 기본값: 카운트 허용
    private val _currentActivity = MutableStateFlow("UNKNOWN")
    val currentActivity: StateFlow<String> = _currentActivity

    companion object {
        private const val TAG = "StepCounterService"
        private const val KEY_INITIAL_STEPS = "initial_steps"
        private const val KEY_LAST_DATE = "last_date"
        private const val KEY_TODAY_STEPS = "today_steps"
        private const val KEY_IS_WALKING = "is_walking"
    }

    init {
        Log.d(TAG, "Step Counter Sensor: ${stepCounterSensor?.name ?: "NOT AVAILABLE"}")
        Log.d(TAG, "Step Detector Sensor: ${stepDetectorSensor?.name ?: "NOT AVAILABLE"}")

        if (stepCounterSensor == null && stepDetectorSensor == null) {
            Log.w(TAG, "No step sensors available on this device")
        }

        // 저장된 활동 상태 로드
        isWalkingOrRunning = prefs.getBoolean(KEY_IS_WALKING, true)

        // ActivityRecognitionReceiver에 이 서비스 등록
        ActivityRecognitionReceiver.registerService(this)

        // Activity Recognition 시작
        startActivityRecognition()
    }

    /**
     * Activity Recognition 시작 - 걷기/차량 이동 등 활동 감지
     */
    private fun startActivityRecognition() {
        // 권한 체크 (Android 10+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val hasPermission = context.checkSelfPermission(android.Manifest.permission.ACTIVITY_RECOGNITION) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED

            if (!hasPermission) {
                Log.w(TAG, "ACTIVITY_RECOGNITION permission not granted - skipping Activity Recognition")
                return
            }
        }

        try {
            val transitions = listOf(
                // 걷기 시작/종료
                ActivityTransition.Builder()
                    .setActivityType(DetectedActivity.WALKING)
                    .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                    .build(),
                ActivityTransition.Builder()
                    .setActivityType(DetectedActivity.WALKING)
                    .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT)
                    .build(),
                // 뛰기 시작/종료
                ActivityTransition.Builder()
                    .setActivityType(DetectedActivity.RUNNING)
                    .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                    .build(),
                ActivityTransition.Builder()
                    .setActivityType(DetectedActivity.RUNNING)
                    .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT)
                    .build(),
                // 차량 이동 시작/종료
                ActivityTransition.Builder()
                    .setActivityType(DetectedActivity.IN_VEHICLE)
                    .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                    .build(),
                ActivityTransition.Builder()
                    .setActivityType(DetectedActivity.IN_VEHICLE)
                    .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT)
                    .build(),
                // 자전거 시작/종료
                ActivityTransition.Builder()
                    .setActivityType(DetectedActivity.ON_BICYCLE)
                    .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                    .build(),
                ActivityTransition.Builder()
                    .setActivityType(DetectedActivity.ON_BICYCLE)
                    .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT)
                    .build(),
                // 정지 상태
                ActivityTransition.Builder()
                    .setActivityType(DetectedActivity.STILL)
                    .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                    .build()
            )

            val request = ActivityTransitionRequest(transitions)

            // Activity Recognition API를 통해 활동 감지 시작
            val task = ActivityRecognition.getClient(context)
                .requestActivityUpdates(5000L, createActivityPendingIntent())

            task.addOnSuccessListener {
                Log.d(TAG, "Activity Recognition started successfully")
            }

            task.addOnFailureListener { e ->
                Log.e(TAG, "Failed to start Activity Recognition", e)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error starting Activity Recognition", e)
        }
    }

    /**
     * Activity Recognition 결과를 받을 PendingIntent 생성
     */
    private fun createActivityPendingIntent(): PendingIntent {
        val intent = Intent(context, ActivityRecognitionReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    /**
     * 활동 상태 업데이트 (ActivityRecognitionReceiver에서 호출)
     */
    fun updateActivityState(activityType: Int) {
        val previousState = isWalkingOrRunning

        when (activityType) {
            DetectedActivity.WALKING, DetectedActivity.RUNNING -> {
                isWalkingOrRunning = true
                _currentActivity.value = if (activityType == DetectedActivity.WALKING) "WALKING" else "RUNNING"
                Log.d(TAG, "Activity: ${_currentActivity.value} - Step counting ENABLED")
            }
            DetectedActivity.IN_VEHICLE -> {
                isWalkingOrRunning = false
                _currentActivity.value = "IN_VEHICLE"
                Log.d(TAG, "Activity: IN_VEHICLE - Step counting DISABLED")
            }
            DetectedActivity.ON_BICYCLE -> {
                isWalkingOrRunning = false
                _currentActivity.value = "ON_BICYCLE"
                Log.d(TAG, "Activity: ON_BICYCLE - Step counting DISABLED")
            }
            DetectedActivity.STILL -> {
                isWalkingOrRunning = true // 정지 상태에서는 다시 활성화 (걷다가 멈춘 경우)
                _currentActivity.value = "STILL"
                Log.d(TAG, "Activity: STILL - Step counting ENABLED")
            }
            else -> {
                isWalkingOrRunning = true // 알 수 없는 상태는 카운트 허용
                _currentActivity.value = "UNKNOWN"
                Log.d(TAG, "Activity: UNKNOWN - Step counting ENABLED")
            }
        }

        // 상태 저장
        prefs.edit().putBoolean(KEY_IS_WALKING, isWalkingOrRunning).apply()

        // 상태 변경 로그
        if (previousState != isWalkingOrRunning) {
            Log.i(TAG, "Step counting state changed: ${if (isWalkingOrRunning) "ENABLED" else "DISABLED"}")
        }
    }

    /**
     * 센서 리스닝 시작 (재시도 로직 포함)
     */
    fun startListening() {
        Log.d(TAG, "startListening() called")

        // 먼저 기존 리스너 완전히 제거
        try {
            sensorManager.unregisterListener(this)
            Thread.sleep(100) // 100ms 대기 (센서 리소스 해제 시간)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering listener", e)
        }

        var registeredCount = 0
        val maxRetries = 3

        // 1. Step Detector 등록 (즉각 반응용) - 재시도
        stepDetectorSensor?.let { sensor ->
            for (retry in 1..maxRetries) {
                Log.d(TAG, "Registering Step Detector: ${sensor.name} (attempt $retry)")
                val registered = sensorManager.registerListener(
                    this,
                    sensor,
                    SensorManager.SENSOR_DELAY_FASTEST
                )
                if (registered) {
                    registeredCount++
                    Log.d(TAG, "✅ Step Detector registered (INSTANT updates)")
                    break
                } else {
                    Log.e(TAG, "❌ Failed to register Step Detector (attempt $retry)")
                    if (retry < maxRetries) {
                        Thread.sleep(100) // 재시도 전 대기
                    }
                }
            }
        }

        // 2. Step Counter 등록 (정확한 총 걸음수용) - 재시도
        stepCounterSensor?.let { sensor ->
            for (retry in 1..maxRetries) {
                Log.d(TAG, "Registering Step Counter: ${sensor.name} (attempt $retry)")
                val registered = sensorManager.registerListener(
                    this,
                    sensor,
                    SensorManager.SENSOR_DELAY_GAME // FASTEST → GAME으로 변경 (더 자주 업데이트)
                )
                if (registered) {
                    registeredCount++
                    Log.d(TAG, "✅ Step Counter registered (ACCURATE count, GAME delay)")
                    loadInitialSteps()
                    break
                } else {
                    Log.e(TAG, "❌ Failed to register Step Counter (attempt $retry)")
                    if (retry < maxRetries) {
                        Thread.sleep(100) // 재시도 전 대기
                    }
                }
            }
        }

        if (registeredCount == 0) {
            Log.w(TAG, "⚠️ No sensors registered after $maxRetries retries")
            Log.w(TAG, "Available sensors: ${sensorManager.getSensorList(Sensor.TYPE_ALL).map { it.name }}")
        } else {
            Log.i(TAG, "🎉 Successfully registered $registeredCount sensor(s)")
        }
    }

    /**
     * 센서 리스닝 중지
     */
    fun stopListening() {
        sensorManager.unregisterListener(this)

        // Activity Recognition 중지
        try {
            ActivityRecognition.getClient(context)
                .removeActivityUpdates(createActivityPendingIntent())
                .addOnSuccessListener {
                    Log.d(TAG, "Activity Recognition stopped")
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping Activity Recognition", e)
        }

        // Receiver 등록 해제
        ActivityRecognitionReceiver.unregisterService()

        Log.d(TAG, "Step counter sensor unregistered")
    }

    /**
     * 센서 데이터 수신
     */
    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            when (it.sensor.type) {
                // TYPE_STEP_DETECTOR: 한 걸음마다 즉시 이벤트 (빠른 UI 반응용)
                Sensor.TYPE_STEP_DETECTOR -> {
                    if (isWalkingOrRunning) {
                        _currentSteps.value++
                        updateSessionSteps()
                        Log.d(TAG, "👣 Step detected! Current: ${_currentSteps.value}")
                    }
                }

                // TYPE_STEP_COUNTER: 정확한 총 걸음수 (동기화/보정용)
                Sensor.TYPE_STEP_COUNTER -> {
                    val totalStepsSinceBoot = it.values[0].toInt()

                    // 날짜가 바뀌었는지 확인
                    checkAndResetDaily(totalStepsSinceBoot)

                    // 오늘의 정확한 걸음 수
                    val accurateSteps = totalStepsSinceBoot - initialSteps
                    val currentDisplayedSteps = _currentSteps.value

                    // 차이가 2걸음 이상 나면 보정
                    val difference = kotlin.math.abs(accurateSteps - currentDisplayedSteps)
                    if (difference >= 2) {
                        Log.w(TAG, "⚠️ 보정 필요: 표시값=$currentDisplayedSteps, 정확값=$accurateSteps (차이=$difference)")
                        _currentSteps.value = accurateSteps
                        updateSessionSteps()
                    }

                    // DB 저장은 조건부
                    saveStepsIfNeeded(accurateSteps)

                    Log.d(TAG, "📊 Accurate: $accurateSteps (total: $totalStepsSinceBoot, displayed: ${_currentSteps.value})")
                }
            }
        }
    }

    /**
     * 세션 걸음 수 업데이트
     */
    private fun updateSessionSteps() {
        if (_isSessionActive.value) {
            _sessionSteps.value = _currentSteps.value - sessionStartSteps
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        Log.d(TAG, "Sensor accuracy changed: $accuracy")
    }

    /**
     * 초기 걸음 수 로드 (앱 시작 시 / 날짜 변경 시)
     */
    private fun loadInitialSteps() {
        val today = getCurrentDate()
        val lastDate = prefs.getString(KEY_LAST_DATE, "")

        if (lastDate == today) {
            // 같은 날이면 저장된 초기값 사용
            initialSteps = prefs.getInt(KEY_INITIAL_STEPS, 0)
            val cachedSteps = prefs.getInt(KEY_TODAY_STEPS, 0)
            _currentSteps.value = cachedSteps
            Log.d(TAG, "Loaded cached steps: $cachedSteps")
        } else {
            // 날짜가 바뀌었으면 리셋 (다음 센서 이벤트에서 초기화)
            initialSteps = 0
            _currentSteps.value = 0
            prefs.edit()
                .putString(KEY_LAST_DATE, today)
                .putInt(KEY_TODAY_STEPS, 0)
                .apply()
            Log.d(TAG, "Date changed - reset steps")
        }
    }

    /**
     * 날짜 변경 확인 및 리셋
     */
    private fun checkAndResetDaily(totalSteps: Int) {
        val today = getCurrentDate()
        val lastDate = prefs.getString(KEY_LAST_DATE, "")

        if (lastDate != today) {
            // 자정이 지나면 리셋
            initialSteps = totalSteps
            _currentSteps.value = 0

            prefs.edit()
                .putString(KEY_LAST_DATE, today)
                .putInt(KEY_INITIAL_STEPS, totalSteps)
                .putInt(KEY_TODAY_STEPS, 0)
                .apply()

            Log.d(TAG, "Daily reset - new date: $today, initial steps: $totalSteps")
        } else if (initialSteps == 0) {
            // 첫 실행 시 초기값 설정
            val cachedSteps = prefs.getInt(KEY_TODAY_STEPS, 0)
            initialSteps = totalSteps - cachedSteps

            prefs.edit()
                .putInt(KEY_INITIAL_STEPS, initialSteps)
                .apply()

            Log.d(TAG, "Initial steps set: $initialSteps")
        }
    }

    /**
     * 조건에 따라 DB 저장 (최적화)
     * - 10걸음 이상 차이나거나
     * - 5초 이상 지났을 때만 저장
     */
    private fun saveStepsIfNeeded(steps: Int) {
        val currentTime = System.currentTimeMillis()
        val stepDifference = steps - lastSavedSteps
        val timeDifference = currentTime - lastSaveTime

        // 조건 1: 10걸음 이상 차이 OR 조건 2: 5초 이상 경과
        if (stepDifference >= saveStepThreshold || timeDifference >= saveInterval) {
            // DB 저장 (비동기)
            saveStepsToDatabase(steps)

            // 캐시 저장 (즉시)
            saveStepsToCache(steps)

            // 저장 시점 기록
            lastSavedSteps = steps
            lastSaveTime = currentTime

            Log.d(TAG, "💾 Saved to DB: $steps steps (diff: $stepDifference, time: ${timeDifference}ms)")
        }
    }

    /**
     * 데이터베이스에 걸음 수 저장
     */
    private fun saveStepsToDatabase(steps: Int) {
        serviceScope.launch {
            try {
                repository.updateSteps(steps)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save steps to database", e)
            }
        }
    }

    /**
     * SharedPreferences에 걸음 수 캐시 저장
     */
    private fun saveStepsToCache(steps: Int) {
        prefs.edit()
            .putInt(KEY_TODAY_STEPS, steps)
            .apply()
    }

    /**
     * 현재 날짜 문자열 (YYYY-MM-DD)
     */
    private fun getCurrentDate(): String {
        return LocalDate.now().toString()
    }

    /**
     * 수동으로 걸음 수 리셋 (테스트용)
     */
    fun resetSteps() {
        initialSteps = 0
        _currentSteps.value = 0

        prefs.edit()
            .clear()
            .putString(KEY_LAST_DATE, getCurrentDate())
            .apply()

        Log.d(TAG, "Steps manually reset")
    }

    /**
     * 새로운 세션 시작 (현재 걸음수를 시작점으로 설정)
     */
    fun startNewSession() {
        sessionStartSteps = _currentSteps.value
        _sessionSteps.value = 0
        _isSessionActive.value = true
        Log.d(TAG, "🎯 New session started at ${sessionStartSteps} steps")
    }

    /**
     * 세션 리셋 (0부터 다시 시작)
     */
    fun resetSession() {
        sessionStartSteps = _currentSteps.value
        _sessionSteps.value = 0
        Log.d(TAG, "🔄 Session reset at ${sessionStartSteps} steps")
    }

    /**
     * 세션 종료
     */
    fun endSession() {
        _isSessionActive.value = false
        sessionStartSteps = 0
        _sessionSteps.value = 0
        Log.d(TAG, "⏹️ Session ended")
    }

    /**
     * 세션 걸음수 가져오기
     */
    fun getSessionSteps(): Int {
        return _sessionSteps.value
    }
}
