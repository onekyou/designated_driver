package com.designated.driverapp.ui.login

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.google.firebase.firestore.FirebaseFirestore
import com.designated.driverapp.model.DriverStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import android.content.SharedPreferences
import com.google.firebase.messaging.FirebaseMessaging
import com.designated.driverapp.data.Constants

// LoginState sealed class 수정 (NeedsConfirmation 제거)
sealed class LoginState {
    object Idle : LoginState()
    object Loading : LoginState()
    data class Success(val regionId: String, val officeId: String, val driverId: String, val needsTokenUpdate: Boolean) : LoginState()
    data class Error(val message: String) : LoginState()
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val sharedPreferences: SharedPreferences
) : ViewModel() {
    private val TAG = "LoginViewModel"

    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState: StateFlow<LoginState> = _loginState

    var email by mutableStateOf("") // Consider renaming to emailOrPhone if applicable
    var password by mutableStateOf("")
    var autoLogin by mutableStateOf(false)

    init {
        Log.d(TAG, "초기화: 아이디/비번 기억 설정 확인 중...")
        
        // 저장된 자동 로그인 체크박스 상태 불러오기
        autoLogin = sharedPreferences.getBoolean("auto_login", false)
        Log.d(TAG, "아이디/비번 기억 활성화 상태: $autoLogin")
        
        // 아이디/비번 기억이 활성화되어 있다면, 저장된 이메일/비번 불러와서 필드 채우기
        if (autoLogin) {
            val savedIdentifier = sharedPreferences.getString("identifier", "")
            val savedPassword = sharedPreferences.getString("password", "")
            Log.d(TAG, "저장된 로그인 정보 로드: 이메일=${savedIdentifier?.takeIf { it.isNotEmpty() }?.let { "${it.take(3)}..." } ?: "없음"}, 비밀번호=${if (savedPassword?.isNotEmpty() == true) "있음" else "없음"}")
            if (!savedIdentifier.isNullOrBlank()) { // 비밀번호는 빈 값일 수도 있으므로 email만 체크
                email = savedIdentifier
                password = savedPassword ?: "" // null일 경우 빈 문자열로
            }
        } else {
            Log.d(TAG, "아이디/비번 기억 비활성화됨")
        }
    }

    fun login() {
        Log.d(TAG, "로그인 시도: 이메일=${email.takeIf { it.isNotEmpty() }?.let { "${it.take(3)}..." } ?: ""}")
        
        if (email.isBlank() || password.isBlank()) {
            _loginState.value = LoginState.Error("이메일(또는 전화번호)과 비밀번호를 모두 입력해주세요.")
            return
        }

        _loginState.value = LoginState.Loading
        viewModelScope.launch {
            try {
                auth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Log.d(TAG, "로그인 성공: ${auth.currentUser?.email}")
                            val userId = auth.currentUser?.uid

                            if (userId != null) {
                                // val firestore = FirebaseFirestore.getInstance() // Injected
                                
                                // --- 승인 대기 상태 먼저 확인 ---
                                checkPendingStatusAndProceed(userId)
                                // --- --- 

                            } else {
                                Log.w(TAG, "Auth: User ID is null after successful login task? This shouldn't happen.")
                                _loginState.value = LoginState.Error("로그인 처리 중 오류가 발생했습니다. (UID 누락)")
                            }
                        } else {
                            Log.e(TAG, "로그인 실패: ${task.exception?.message}")
                            _loginState.value = LoginState.Error(task.exception?.message ?: "로그인에 실패했습니다.")
                        }
                    }
            } catch (e: Exception) {
                Log.e(TAG, "로그인 중 오류 발생", e)
                _loginState.value = LoginState.Error(e.message ?: "알 수 없는 오류가 발생했습니다.")
            }
        }
    }

    // --- 승인 대기 상태 확인 후 진행하는 함수 --- 
    private fun checkPendingStatusAndProceed(userId: String) {
        Log.d(TAG, "Checking pending status for UID: $userId")
        val pendingDocRef = firestore.collection("pending_drivers").document(userId)

        pendingDocRef.get().addOnSuccessListener { pendingDoc ->
            if (pendingDoc.exists()) {
                // 아직 승인 대기 중인 상태
                Log.w(TAG, "Login attempt failed: User $userId is still pending approval.")
                _loginState.value = LoginState.Error("관리자 승인 대기 중인 계정입니다.")
                auth.signOut() // 로그아웃 처리
            } else {
                // 승인 대기 목록에 없음 -> 정식 기사 목록에서 찾아 정보 저장 시도
                Log.d(TAG, "User $userId is not in pending list. Proceeding to find driver document.")
                findDriverDocumentAndSaveInfo(userId)
            }
        }.addOnFailureListener { e ->
             Log.e(TAG, "Error checking pending status for UID: $userId", e)
             _loginState.value = LoginState.Error("로그인 처리 중 오류 발생: ${e.message}")
             auth.signOut()
        }
    }
    // --- --- 

    // --- Firestore에서 기사 문서 찾아 정보 저장 및 상태 업데이트 함수 --- 
    private fun findDriverDocumentAndSaveInfo(userId: String) {
        Log.d(TAG, "Attempting collectionGroup query for UID: $userId")
        this.firestore.collectionGroup("designated_drivers")
            .whereEqualTo("authUid", userId)
            .limit(1)
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (!querySnapshot.isEmpty) {
                    val documentSnapshot = querySnapshot.documents[0]
                    val driverRef = documentSnapshot.reference
                    val regionId = documentSnapshot.getString("regionId")
                    val officeId = documentSnapshot.getString("officeId")
                    val driverApprovalStatus = documentSnapshot.getString("approvalStatus")
                    val currentDriverStatus = documentSnapshot.getString("status")
                    val driverName = documentSnapshot.getString("name") ?: "기사님"
                    val serverFcmToken = documentSnapshot.getString(Constants.FIELD_FCM_TOKEN) // 서버에 저장된 토큰 읽기

                    Log.d(TAG, "Firestore: Read regionId=$regionId, officeId=$officeId, approvalStatus=$driverApprovalStatus, serverFcmToken=${serverFcmToken?.take(10)}")

                    // ★★★ approvalStatus 필드를 읽도록 수정 ★★★
                    if (driverApprovalStatus != com.designated.driverapp.model.DriverApprovalStatus.APPROVED.name) { // Enum의 name ("APPROVED")과 비교
                         Log.w(TAG, "Login failed: Driver $driverName ($userId) is not approved. Status: $driverApprovalStatus")
                         val errorMessage = when (driverApprovalStatus) {
                             com.designated.driverapp.model.DriverApprovalStatus.PENDING.name -> "관리자 승인 대기 중인 계정입니다."
                             com.designated.driverapp.model.DriverApprovalStatus.REJECTED.name -> "가입이 거절된 계정입니다. 관리자에게 문의하세요."
                             else -> "계정 상태를 확인할 수 없습니다. 관리자에게 문의하세요."
                         }
                         _loginState.value = LoginState.Error(errorMessage)
                         auth.signOut()
                         return@addOnSuccessListener
                    }
                    // ★★★ --- ★★★

                    if (!regionId.isNullOrBlank() && !officeId.isNullOrBlank()) {
                        // SharedPreferences에 regionId, officeId, driverId 저장 (이름 통일: driver_prefs)
                        sharedPreferences.edit().apply {
                            putString("regionId", regionId)
                            putString("officeId", officeId)
                            putString("driverId", userId) // ★ driverId(Firebase UID) 저장 추가 ★
                            // --- 로그인 정보 저장 로직 추가 (autoLogin 상태에 따라) ---
                             if (autoLogin) {
                                 Log.d(TAG, "자동 로그인 활성화됨 - 로그인 정보 저장")
                                 putBoolean("auto_login", true)
                                 putString("identifier", email)
                                 putString("password", password) // 비밀번호 저장 보안 고려 필요
                             } else {
                                 Log.d(TAG, "자동 로그인 비활성화됨 - 로그인 정보 삭제")
                                 remove("auto_login")
                                 remove("identifier")
                                 remove("password")
                             }
                            // --- ---
                            apply() // 모든 변경사항 적용
                        }
                        Log.i(TAG, "✅ SharedPreferences: Saved regionId, officeId, driverId and login info successfully.")

                        // <<-- Start of new logic: Compare local and server FCM token -->>
                        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                            if (!task.isSuccessful) {
                                Log.w(TAG, "Fetching current FCM token failed", task.exception)
                                // 토큰 가져오기 실패해도 로그인은 진행. 단, 업데이트 필요 플래그는 false.
                                _loginState.value = LoginState.Success(regionId, officeId, userId, false)
                                return@addOnCompleteListener
                            }
                            val localFcmToken = task.result
                            val needsUpdate = serverFcmToken.isNullOrBlank() || serverFcmToken != localFcmToken
                            Log.d(TAG, "FCM Token check: Needs update? $needsUpdate (Server: ${serverFcmToken?.take(10)}, Local: ${localFcmToken.take(10)})")
                            
                            // 로그인 성공 상태와 함께 토큰 업데이트 필요 여부 전달
                            _loginState.value = LoginState.Success(regionId, officeId, userId, needsUpdate)

                            // Firestore 상태 업데이트 로직은 그대로 유지
                            val onlineStatus = com.designated.driverapp.model.DriverStatus.ONLINE.value
                            if (currentDriverStatus != onlineStatus) {
                                driverRef.update("status", onlineStatus)
                                    .addOnSuccessListener { Log.i(TAG, "✅ Firestore: Driver status updated to '$onlineStatus'.") }
                                    .addOnFailureListener { e -> Log.e(TAG, "❌ Firestore: FAILED to update driver status.", e) }
                            }
                        }
                        // <<-- End of new logic -->>

                    } else {
                         Log.e(TAG, "❌ Firestore: regionId or officeId is missing: ${driverRef.path}")
                        // 적절한 오류 처리 (예: 로그인 실패 처리 또는 사용자 알림)
                        _loginState.value = LoginState.Error("기사 정보(지역/사무실 ID)가 누락되었습니다.")
                        auth.signOut() // 로그아웃 처리
                    }
                } else {
                    Log.e(TAG, "❌ Firestore: Could not find driver document for UID: $userId")
                    // 적절한 오류 처리
                     _loginState.value = LoginState.Error("등록되지 않은 기사 계정입니다.")
                     auth.signOut()
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Firestore: Failed collectionGroup query for UID: $userId", e)
                 _loginState.value = LoginState.Error("기사 정보 조회 중 오류 발생: ${e.message}")
                 auth.signOut()
            }
    }
    // --- --- 

    fun toggleAutoLogin(enabled: Boolean) {
        // 체크박스 상태만 업데이트
        Log.d(TAG, "아이디/비번 기억 체크박스 상태 변경: $enabled")
        autoLogin = enabled 
    }

    fun resetLoginState() {
        _loginState.value = LoginState.Idle
    }
} 