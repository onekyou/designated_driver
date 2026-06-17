package com.designated.pickupdriver

import android.Manifest
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.designated.pickupdriver.data.Constants
import com.designated.pickupdriver.data.repository.ChatRepository
import com.designated.pickupdriver.service.PttState
import com.designated.pickupdriver.ui.chat.DashboardWithChatSheet
import com.designated.pickupdriver.ui.login.LoginScreen
import com.designated.pickupdriver.ui.login.SignUpScreen
import com.designated.pickupdriver.ui.theme.PickupDriverAppTheme
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import javax.inject.Inject

private object Routes {
    const val LOGIN = "login"
    const val SIGNUP = "signup"
    const val DASHBOARD = "dashboard"
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var auth: FirebaseAuth
    @Inject lateinit var prefs: SharedPreferences
    @Inject lateinit var chatRepository: ChatRepository

    // PTT 송수신 매니저 — Application 단일 인스턴스 공유(PttReceiverService와 동일 엔진).
    private val pttManager get() = PickupDriverApplication.getInstance().pttManager

    // PTT 송신 상태 (call_manager 패턴). 콜드 판정 제거 — 포그라운드 발화는 항상 라이브(2026-06-03 재설계).
    private var pttHolding = false
    private val pttPressedKeys = HashSet<Int>() // 현재 눌린 볼륨키 (업/다운 동시 누름 안전 처리)

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* 거부해도 앱은 정상 동작 — 알림만 안 옴 */ }

    private val recordAudioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) Toast.makeText(this, "음성 메모에 마이크 권한이 필요합니다", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()

        // PTT 콜백 배선 — 콜드 음성메모 송신은 @Singleton ChatRepository 직접 호출(Hilt 스코프 우회).
        pttManager.onColdVoiceMemo = { file, durationMs -> sendPttVoiceMemoViaRepo(file, durationMs) }
        // [STT] 라이브 발화 전사 텍스트 → 무음 PTT-텍스트 메시지 (ViewModel 우회, repo 직접).
        pttManager.onPttTranscript = { text -> sendPttTextViaRepo(text) }
        pttManager.onRecordUnavailable = { recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }

        val start = if (auth.currentUser != null &&
            !prefs.getString(Constants.PREF_KEY_OFFICE_ID, null).isNullOrBlank()) {
            Routes.DASHBOARD
        } else {
            Routes.LOGIN
        }

        setContent {
            PickupDriverAppTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Scaffold { innerPadding ->
                            val navController = rememberNavController()
                            NavHost(
                                navController = navController,
                                startDestination = start,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                composable(Routes.LOGIN) {
                                    LoginScreen(
                                        onLoginSuccess = {
                                            navController.navigate(Routes.DASHBOARD) {
                                                popUpTo(Routes.LOGIN) { inclusive = true }
                                            }
                                        },
                                        onNavigateToSignUp = {
                                            navController.navigate(Routes.SIGNUP)
                                        }
                                    )
                                }
                                composable(Routes.SIGNUP) {
                                    SignUpScreen(
                                        onSignUpSuccess = {
                                            navController.popBackStack(Routes.LOGIN, inclusive = false)
                                        },
                                        onNavigateBack = {
                                            navController.popBackStack()
                                        }
                                    )
                                }
                                composable(Routes.DASHBOARD) {
                                    DashboardWithChatSheet(
                                        onLogout = {
                                            navController.navigate(Routes.LOGIN) {
                                                popUpTo(Routes.DASHBOARD) { inclusive = true }
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        // PTT 상태 배너 — 어느 화면에서든 상단 표시 (송신 빨강 / 수신 파랑 / 연결중 주황)
                        val pttState by pttManager.state.collectAsState()
                        if (pttState != PttState.IDLE) {
                            val bg = when (pttState) {
                                PttState.TALKING, PttState.RECORDING -> Color(0xFFD32F2F)
                                PttState.LISTENING -> Color(0xFF1565C0)
                                else -> Color(0xFFF9A825)
                            }
                            val label = when (pttState) {
                                PttState.RECORDING -> "🔴 녹음중 (말하세요)"
                                PttState.TALKING -> "🔴 PTT 발화중"
                                PttState.LISTENING -> "🔊 수신중"
                                else -> "연결중…"
                            }
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .fillMaxWidth()
                                    .background(bg)
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(label, color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * 볼륨 버튼(업/다운 무관) 누르기 시작 = 즉시 발화(hold-to-talk), 떼면 종료. 시스템 볼륨 변경은 차단(return true).
     * 업/다운 동시 누름 안전 — 첫 키에서 시작, 모든 키 뗀 뒤 종료. 화면 꺼짐 송신은 별도 트랙.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val kc = event.keyCode
        if (kc == KeyEvent.KEYCODE_VOLUME_DOWN || kc == KeyEvent.KEYCODE_VOLUME_UP) {
            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    if (event.repeatCount == 0) {
                        pttPressedKeys.add(kc)
                        if (!pttHolding) {
                            val (p, c, o) = getOfficeInfoForPtt()
                            if (p != null && c != null && o != null) {
                                pttHolding = true
                                pttManager.startTransmit(this, p, c, o)
                                Log.d("MainActivity", "PTT 발화 시작 (단일 누름)")
                            } else {
                                Log.w("MainActivity", "PTT: 사무실 정보 없음 — 발화 불가")
                            }
                        }
                    }
                    // repeatCount>0 (hold 중 키반복) 무시
                }
                KeyEvent.ACTION_UP -> {
                    pttPressedKeys.remove(kc)
                    if (pttPressedKeys.isEmpty() && pttHolding) {
                        pttHolding = false
                        pttManager.stopTransmit()
                        Log.d("MainActivity", "PTT 발화 종료 (손 뗌)")
                    }
                }
            }
            return true // 시스템 볼륨 변경 차단 (PTT 전용)
        }
        return super.dispatchKeyEvent(event)
    }

    /** 사무실 정보 — 주입 prefs(pickup_driver_prefs)에서 픽업 키로 읽음. */
    private fun getOfficeInfoForPtt(): Triple<String?, String?, String?> {
        val p = prefs.getString(Constants.PREF_KEY_PROVINCE_ID, null)
        val c = prefs.getString(Constants.PREF_KEY_CITY_ID, null)
        val o = prefs.getString(Constants.PREF_KEY_OFFICE_ID, null)
        return Triple(p, c, o)
    }

    /** 콜드 음성메모 송신 — ChatRepository 직접 호출(Hilt 스코프 우회). senderName 폴백 "픽업기사". */
    private fun sendPttVoiceMemoViaRepo(file: File, durationMs: Long) {
        val (p, c, o) = getOfficeInfoForPtt()
        val senderId = auth.currentUser?.uid
        if (p == null || c == null || o == null || senderId == null) {
            Log.w("MainActivity", "PTT 음성메모: 사무실/인증 정보 없음 — drop")
            runCatching { file.delete() }
            return
        }
        chatRepository.sendAudioMessage(
            provinceId = p, cityId = c, officeId = o,
            senderId = senderId, senderName = "픽업기사", senderRole = ChatRepository.ROLE_PICKUP_DRIVER,
            file = file, durationMs = durationMs, autoplay = true,
        )
    }

    /** [STT] PTT 발화 전사 텍스트 → 무음 PTT-텍스트 메시지. ChatRepository 직접 호출(Hilt 스코프 우회). */
    private fun sendPttTextViaRepo(text: String) {
        val (p, c, o) = getOfficeInfoForPtt()
        val senderId = auth.currentUser?.uid
        if (p == null || c == null || o == null || senderId == null) {
            Log.w("MainActivity", "PTT 텍스트: 사무실/인증 정보 없음 — drop")
            return
        }
        chatRepository.sendPttText(
            provinceId = p, cityId = c, officeId = o,
            senderId = senderId, senderName = "픽업기사", senderRole = ChatRepository.ROLE_PICKUP_DRIVER,
            text = text,
        )
    }

    override fun onResume() {
        super.onResume()
        pttManager.prewarm(this) // 엔진 워밍업(첫 발화 지연 제거)
        // 토큰 prewarm — office 확정 시 미리 발급(첫 발화 시 generateAgoraToken 호출 0)
        getOfficeInfoForPtt().let { (p, _, o) ->
            if (!p.isNullOrBlank() && !o.isNullOrBlank()) pttManager.prewarmToken(this, p, o)
        }
    }

    override fun onPause() {
        super.onPause()
        // PTT 안전종료 — hold 중 백그라운드 전환/포커스 상실로 ACTION_UP 유실 시 송신 지속 차단
        if (pttHolding) {
            pttHolding = false
            pttManager.stopTransmit()
        }
        pttPressedKeys.clear()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
