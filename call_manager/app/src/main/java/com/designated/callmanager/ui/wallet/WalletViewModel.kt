package com.designated.callmanager.ui.wallet

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.designated.callmanager.CallManagerApplication
import com.designated.callmanager.data.PointTransaction
import com.designated.callmanager.data.PointsInfo
import com.designated.callmanager.data.repository.PointRepository
import com.designated.callmanager.di.DatabaseProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * 사무실 wallet ViewModel — PR 3 (2026-05-05)
 *
 * 책임
 *  - 사무실 포인트 잔액·거래 내역 표시 (PointRepository Local-first 활용)
 *  - 콜마당 본부 단일 입금계좌 안내 정보 로드 (system_config/deposit_account)
 *  - 출금 신청 (submitWithdrawalRequest CF callable)
 *
 * 데이터 출처
 *  - SharedPreferences("login_prefs"): provinceId / cityId / officeId
 *  - PointRepository.getPointsInfoFlow / getTransactionsFlow (Room DB + Firestore sync)
 *  - Firestore /system_config/deposit_account (1회 fetch)
 *  - Cloud Functions submitWithdrawalRequest (asia-northeast3)
 */
class WalletViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "WalletViewModel"
    }

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore = Firebase.firestore
    private val functions: FirebaseFunctions = Firebase.functions("asia-northeast3")

    private val prefs = application.getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
    private val provinceId: String = prefs.getString("provinceId", "") ?: ""
    private val cityId: String = prefs.getString("cityId", "") ?: ""
    private val officeId: String = prefs.getString("officeId", "") ?: ""

    private val pointRepository: PointRepository by lazy {
        DatabaseProvider.providePointRepository(
            database = (application as CallManagerApplication).database,
            firestore = firestore,
            scope = DatabaseProvider.provideRepositoryScope()
        )
    }

    val pointsInfo: StateFlow<PointsInfo?> = pointRepository
        .getPointsInfoFlow(provinceId, cityId, officeId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val transactions: StateFlow<List<PointTransaction>> = pointRepository
        .getTransactionsFlow(provinceId, cityId, officeId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _depositAccount = MutableStateFlow<DepositAccount?>(null)
    val depositAccount: StateFlow<DepositAccount?> = _depositAccount.asStateFlow()

    private val _depositLoadState = MutableStateFlow<DepositLoadState>(DepositLoadState.Idle)
    val depositLoadState: StateFlow<DepositLoadState> = _depositLoadState.asStateFlow()

    private val _withdrawalState = MutableStateFlow<WithdrawalUiState>(WithdrawalUiState.Idle)
    val withdrawalState: StateFlow<WithdrawalUiState> = _withdrawalState.asStateFlow()

    init {
        if (provinceId.isNotEmpty() && cityId.isNotEmpty() && officeId.isNotEmpty()) {
            viewModelScope.launch {
                runCatching { pointRepository.refreshData(provinceId, cityId, officeId) }
                    .onFailure { Log.w(TAG, "초기 wallet refresh 실패", it) }
            }
        } else {
            Log.w(TAG, "사무실 정보 누락 — wallet 비활성")
        }
    }

    fun refresh() {
        if (provinceId.isEmpty() || officeId.isEmpty()) return
        viewModelScope.launch {
            runCatching { pointRepository.refreshData(provinceId, cityId, officeId) }
                .onFailure { Log.w(TAG, "수동 wallet refresh 실패", it) }
        }
    }

    fun loadDepositAccount() {
        if (_depositLoadState.value is DepositLoadState.Loading) return
        _depositLoadState.value = DepositLoadState.Loading
        viewModelScope.launch {
            try {
                val snap = firestore.collection("system_config")
                    .document("deposit_account")
                    .get()
                    .await()
                if (!snap.exists()) {
                    _depositLoadState.value = DepositLoadState.Error("입금계좌 정보가 등록되지 않았습니다. 콜마당 본부에 문의하세요.")
                    return@launch
                }
                val data = snap.data ?: emptyMap<String, Any>()
                val account = DepositAccount(
                    bankName = data["bankName"] as? String ?: "",
                    accountNumber = data["accountNumber"] as? String ?: "",
                    accountHolder = data["accountHolder"] as? String ?: "",
                    contactPhone = data["contactPhone"] as? String,
                )
                if (account.bankName.isBlank() || account.accountNumber.isBlank() || account.accountHolder.isBlank()) {
                    _depositLoadState.value = DepositLoadState.Error("입금계좌 정보가 불완전합니다. 콜마당 본부에 문의하세요.")
                    return@launch
                }
                _depositAccount.value = account
                _depositLoadState.value = DepositLoadState.Success
            } catch (e: Exception) {
                Log.e(TAG, "입금계좌 로드 실패", e)
                _depositLoadState.value = DepositLoadState.Error("입금계좌 정보를 불러오지 못했습니다. (${e.message?.take(60) ?: "알 수 없음"})")
            }
        }
    }

    fun submitWithdrawal(
        amount: Int,
        bankName: String,
        accountNumber: String,
        accountHolder: String,
    ) {
        if (_withdrawalState.value is WithdrawalUiState.Submitting) return
        if (auth.currentUser == null) {
            _withdrawalState.value = WithdrawalUiState.Error("로그인이 필요합니다.")
            return
        }
        if (amount <= 0) {
            _withdrawalState.value = WithdrawalUiState.Error("출금 금액을 0보다 큰 값으로 입력하세요.")
            return
        }
        val current = pointsInfo.value?.balance ?: 0
        if (amount > current) {
            _withdrawalState.value = WithdrawalUiState.Error("잔액 부족 (현재 $current, 요청 $amount)")
            return
        }
        if (bankName.isBlank() || accountNumber.isBlank() || accountHolder.isBlank()) {
            _withdrawalState.value = WithdrawalUiState.Error("은행명·계좌번호·예금주를 모두 입력하세요.")
            return
        }

        _withdrawalState.value = WithdrawalUiState.Submitting
        viewModelScope.launch {
            try {
                val payload = mapOf(
                    "amount" to amount,
                    "bankName" to bankName.trim(),
                    "accountNumber" to accountNumber.trim(),
                    "accountHolder" to accountHolder.trim(),
                )
                val result = functions
                    .getHttpsCallable("submitWithdrawalRequest")
                    .call(payload)
                    .await()
                val resultData = result.getData() as? Map<*, *>
                val requestId = resultData?.get("requestId") as? String ?: ""
                _withdrawalState.value = WithdrawalUiState.Success(requestId)
            } catch (e: FirebaseFunctionsException) {
                val msg = when (e.code) {
                    FirebaseFunctionsException.Code.UNAUTHENTICATED,
                    FirebaseFunctionsException.Code.PERMISSION_DENIED ->
                        "권한이 없습니다. 다시 로그인 후 시도하세요."
                    FirebaseFunctionsException.Code.INVALID_ARGUMENT ->
                        "입력값이 올바르지 않습니다."
                    FirebaseFunctionsException.Code.FAILED_PRECONDITION ->
                        e.message ?: "잔액 부족 또는 사전 조건 미충족"
                    FirebaseFunctionsException.Code.DEADLINE_EXCEEDED,
                    FirebaseFunctionsException.Code.UNAVAILABLE ->
                        "네트워크 오류. 잠시 후 다시 시도하세요."
                    else -> e.message ?: "출금 신청 실패"
                }
                _withdrawalState.value = WithdrawalUiState.Error(msg)
            } catch (e: Exception) {
                Log.e(TAG, "출금 신청 실패", e)
                _withdrawalState.value = WithdrawalUiState.Error("출금 신청 실패: ${e.message?.take(80) ?: "알 수 없음"}")
            }
        }
    }

    fun resetWithdrawalState() {
        _withdrawalState.value = WithdrawalUiState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        runCatching { pointRepository.stopSync() }
            .onFailure { Log.w(TAG, "pointRepository.stopSync 실패", it) }
    }
}

data class DepositAccount(
    val bankName: String,
    val accountNumber: String,
    val accountHolder: String,
    val contactPhone: String?,
)

sealed class DepositLoadState {
    object Idle : DepositLoadState()
    object Loading : DepositLoadState()
    object Success : DepositLoadState()
    data class Error(val message: String) : DepositLoadState()
}

sealed class WithdrawalUiState {
    object Idle : WithdrawalUiState()
    object Submitting : WithdrawalUiState()
    data class Success(val requestId: String) : WithdrawalUiState()
    data class Error(val message: String) : WithdrawalUiState()
}
