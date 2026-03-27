package com.designated.driverapp.ui.home

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.designated.driverapp.data.Constants
import com.designated.driverapp.data.settlement.DailySettlementStatus
import com.designated.driverapp.navigation.AppDestinations
import com.designated.driverapp.util.SecurePreferencesManager
import com.designated.driverapp.util.SessionManager
import com.designated.driverapp.viewmodel.DriverViewModel
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray

private const val TAG = "SettingsScreen"

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SettingsScreenEntryPoint {
    fun securePreferencesManager(): SecurePreferencesManager
    fun sessionManager(): SessionManager
}

@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: DriverViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val entryPoint = remember {
        EntryPointAccessors.fromApplication(context.applicationContext, SettingsScreenEntryPoint::class.java)
    }
    val securePreferencesManager = remember { entryPoint.securePreferencesManager() }
    val sessionManager = remember { entryPoint.sessionManager() }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val dailySettlementStatus by viewModel.dailySettlementStatus.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val systemUiController = rememberSystemUiController()

    SideEffect {
        systemUiController.setStatusBarColor(
            color = Color(0xFF222222),
            darkIcons = false
        )
    }

    // 이전 운행/정산 기록 관련 상태
    data class SessionData(
        val date: String,
        val history: List<String>,
        val summary: Map<String, Any>
    )

    fun loadSessions(ctx: Context): MutableList<SessionData> {
        val prefs = ctx.getSharedPreferences("trip_sessions", Context.MODE_PRIVATE)
        val sessionsJson = prefs.getString("sessions", "[]")
        val arr = JSONArray(sessionsJson)
        val list = mutableListOf<SessionData>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val date = obj.optString("date", "")
            val historyArr = obj.optJSONArray("history") ?: JSONArray()
            val history = List(historyArr.length()) { j -> historyArr.getString(j) }
            val summaryObj = obj.optJSONObject("summary") ?: org.json.JSONObject()
            val summary = mutableMapOf<String, Any>()
            summaryObj.keys().forEach { k -> summary[k] = summaryObj.get(k) }
            list.add(SessionData(date, history, summary))
        }
        return list
    }

    var sessionList by remember { mutableStateOf(loadSessions(context)) }
    var showSessionDetail by remember { mutableStateOf(false) }
    var selectedSession: SessionData? by remember { mutableStateOf(null) }

    // 탈퇴 관련 상태
    var showBlockedDialog by remember { mutableStateOf(false) }
    var blockedReason by remember { mutableStateOf("") }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var passwordInput by remember { mutableStateOf("") }
    var isDeleting by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }

    // 세션 상세 다이얼로그
    if (showSessionDetail && selectedSession != null) {
        val s = selectedSession!!
        AlertDialog(
            onDismissRequest = { showSessionDetail = false },
            title = { Text("기록일: ${s.date}") },
            text = {
                Column {
                    Text("[운행내역]", fontWeight = FontWeight.Bold)
                    if (s.history.isEmpty()) {
                        Text("운행내역이 없습니다.", color = Color.Gray)
                    } else {
                        s.history.forEach { h ->
                            val parts = h.split("|timestamp=")
                            val displaySummary = parts[0]
                            Text(displaySummary, color = Color.White)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("[총 정산내역]", fontWeight = FontWeight.Bold)
                    val sm = s.summary
                    Text("총 운행 횟수: ${sm["totalCount"] ?: "-"}")
                    Text("총 수입: ${sm["totalFare"] ?: "-"}원")
                    Text("총 납입: ${sm["totalDeposit"] ?: "-"}원")
                    Text("총 외상: ${sm["totalCredit"] ?: "-"}원")
                    Text("실 납입: ${sm["realDeposit"] ?: "-"}원")
                    Text("실 수입: ${sm["realIncome"] ?: "-"}원")
                }
            },
            confirmButton = {
                Button(onClick = { showSessionDetail = false }) { Text("닫기") }
            },
            containerColor = Color(0xFF1A1A1A)
        )
    }

    // 차단 사유 다이얼로그
    if (showBlockedDialog) {
        AlertDialog(
            onDismissRequest = { showBlockedDialog = false },
            title = { Text("탈퇴 불가", color = Color.White) },
            text = { Text(blockedReason, color = Color.White) },
            confirmButton = {
                Button(
                    onClick = { showBlockedDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB000))
                ) { Text("확인", color = Color.Black) }
            },
            containerColor = Color(0xFF2A2A2A)
        )
    }

    // 탈퇴 확인 다이얼로그
    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("회원탈퇴", color = Color.White) },
            text = {
                Text(
                    "탈퇴하면 모든 데이터가 삭제되며 복구할 수 없습니다.\n정말 탈퇴하시겠습니까?",
                    color = Color.White
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDialog = false
                        showPasswordDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                ) { Text("탈퇴", color = Color.White) }
            },
            dismissButton = {
                Button(
                    onClick = { showConfirmDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF555555))
                ) { Text("취소", color = Color.White) }
            },
            containerColor = Color(0xFF2A2A2A)
        )
    }

    // 비밀번호 재입력 다이얼로그
    if (showPasswordDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isDeleting) {
                    showPasswordDialog = false
                    passwordInput = ""
                    deleteError = null
                }
            },
            title = { Text("본인 확인", color = Color.White) },
            text = {
                Column {
                    Text("탈퇴를 진행하려면 비밀번호를 입력해주세요.", color = Color.White)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = {
                            passwordInput = it
                            deleteError = null
                        },
                        label = { Text("비밀번호", color = Color.Gray) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        enabled = !isDeleting,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFFFB000),
                            unfocusedBorderColor = Color.Gray,
                            cursorColor = Color(0xFFFFB000)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (deleteError != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(deleteError!!, color = Color(0xFFD32F2F))
                    }
                    if (isDeleting) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color(0xFFFFB000),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("탈퇴 처리 중...", color = Color.Gray)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (passwordInput.isBlank()) {
                            deleteError = "비밀번호를 입력해주세요."
                            return@Button
                        }
                        isDeleting = true
                        deleteError = null

                        scope.launch {
                            try {
                                val result = deleteAccountAndExit(
                                    context = context,
                                    viewModel = viewModel,
                                    securePreferencesManager = securePreferencesManager,
                                    sessionManager = sessionManager,
                                    password = passwordInput
                                )
                                if (result.isSuccess) {
                                    withContext(Dispatchers.Main) {
                                        showPasswordDialog = false
                                        Toast.makeText(context, "회원탈퇴가 완료되었습니다.", Toast.LENGTH_SHORT).show()
                                        navController.navigate(AppDestinations.LOGIN_ROUTE) {
                                            popUpTo(0) { inclusive = true }
                                        }
                                    }
                                } else {
                                    withContext(Dispatchers.Main) {
                                        deleteError = result.exceptionOrNull()?.message ?: "탈퇴 처리 중 오류가 발생했습니다."
                                        isDeleting = false
                                    }
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    deleteError = e.message ?: "탈퇴 처리 중 오류가 발생했습니다."
                                    isDeleting = false
                                }
                            }
                        }
                    },
                    enabled = !isDeleting,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                ) { Text("확인", color = Color.White) }
            },
            dismissButton = {
                Button(
                    onClick = {
                        showPasswordDialog = false
                        passwordInput = ""
                        deleteError = null
                    },
                    enabled = !isDeleting,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF555555))
                ) { Text("취소", color = Color.White) }
            },
            containerColor = Color(0xFF2A2A2A)
        )
    }

    Scaffold(
        topBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding(),
                color = Color.Black,
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.Filled.ArrowBack,
                            contentDescription = "뒤로가기",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Text(
                        text = "설정",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )

                    // 오른쪽 여백 맞추기
                    Spacer(modifier = Modifier.size(48.dp))
                }
            }
        },
        containerColor = Color(0xFF121212)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            // 이전 운행/정산 기록 섹션
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column {
                    Text(
                        text = "이전 운행/정산 기록",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(16.dp)
                    )

                    if (sessionList.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("이전 기록이 없습니다.", color = Color.Gray)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 300.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            contentPadding = PaddingValues(bottom = 16.dp)
                        ) {
                            items(sessionList) { session ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedSession = session
                                            showSessionDetail = true
                                        },
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF3A3A3A)),
                                    shape = RoundedCornerShape(0.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "기록일: ${session.date}",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                                Text(
                                                    text = "운행내역: ${session.history.size}건",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = Color.LightGray
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // 회원탈퇴 섹션
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "계정",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            // 차단 조건 확인
                            val hasActiveCall = uiState.activeCall != null
                            val hasNewCall = uiState.newCallPopup != null
                            val hasPendingSettlement = dailySettlementStatus == DailySettlementStatus.PENDING_CONFIRM

                            when {
                                hasActiveCall || hasNewCall -> {
                                    blockedReason = "진행 중인 콜이 있어 탈퇴할 수 없습니다.\n콜을 완료하거나 취소한 후 다시 시도해주세요."
                                    showBlockedDialog = true
                                }
                                hasPendingSettlement -> {
                                    blockedReason = "미확인 정산이 있어 탈퇴할 수 없습니다.\n정산을 완료한 후 다시 시도해주세요."
                                    showBlockedDialog = true
                                }
                                else -> {
                                    showConfirmDialog = true
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFD32F2F)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("회원탈퇴", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * 회원탈퇴 실행
 * 순서: 서비스 중지 → Firestore 문서 삭제 → Auth 재인증 → Auth 삭제 → SharedPreferences 클리어
 */
suspend fun deleteAccountAndExit(
    context: Context,
    viewModel: DriverViewModel,
    securePreferencesManager: SecurePreferencesManager,
    sessionManager: SessionManager,
    password: String
): Result<Unit> = withContext(Dispatchers.IO) {
    try {
        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser
            ?: return@withContext Result.failure(Exception("로그인 상태가 아닙니다."))

        val firestore = FirebaseFirestore.getInstance()
        val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        val provinceId = prefs.getString(Constants.PREF_KEY_PROVINCE_ID, null)
        val cityId = prefs.getString(Constants.PREF_KEY_CITY_ID, null)
        val officeId = prefs.getString(Constants.PREF_KEY_OFFICE_ID, null)

        if (provinceId == null || cityId == null || officeId == null) {
            return@withContext Result.failure(Exception("사무실 정보를 찾을 수 없습니다."))
        }

        // 1. 포그라운드 서비스 중지
        withContext(Dispatchers.Main) {
            viewModel.stopDriverService()
        }

        // 2. Firestore 기사 문서 삭제
        val driverDocPath = "provinces/$provinceId/cities/$cityId/offices/$officeId/designated_drivers/${user.uid}"
        Log.d(TAG, "기사 문서 삭제 시작: $driverDocPath")
        firestore.document(driverDocPath).delete().await()
        Log.d(TAG, "기사 문서 삭제 완료")

        // 3. Firebase Auth 재인증 + 계정 삭제
        val email = user.email
        if (email != null) {
            val credential = EmailAuthProvider.getCredential(email, password)
            user.reauthenticate(credential).await()
            Log.d(TAG, "재인증 성공")
        }
        user.delete().await()
        Log.d(TAG, "Auth 계정 삭제 완료")

        // 4. 모든 SharedPreferences 클리어
        prefs.edit().clear().apply()
        securePreferencesManager.clearAll()
        sessionManager.clearSession()
        context.getSharedPreferences("trip_history", Context.MODE_PRIVATE).edit().clear().apply()
        context.getSharedPreferences("settlement_prefs", Context.MODE_PRIVATE).edit().clear().apply()
        context.getSharedPreferences("driver_popup_prefs", Context.MODE_PRIVATE).edit().clear().apply()
        context.getSharedPreferences("trip_sessions", Context.MODE_PRIVATE).edit().clear().apply()
        Log.d(TAG, "모든 SharedPreferences 클리어 완료")

        Result.success(Unit)
    } catch (e: Exception) {
        Log.e(TAG, "회원탈퇴 실패", e)
        val message = when {
            e.message?.contains("INVALID_LOGIN_CREDENTIALS") == true ||
            e.message?.contains("wrong-password") == true ||
            e.message?.contains("invalid-credential") == true -> "비밀번호가 올바르지 않습니다."
            e.message?.contains("requires-recent-login") == true -> "보안을 위해 재로그인이 필요합니다. 로그아웃 후 다시 로그인해주세요."
            e.message?.contains("network") == true -> "네트워크 연결을 확인해주세요."
            else -> "탈퇴 처리 중 오류가 발생했습니다: ${e.message}"
        }
        Result.failure(Exception(message))
    }
}
