package com.designated.callmanager.ui.pendingdrivers

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.designated.callmanager.data.Constants
import com.designated.callmanager.data.PendingDriverInfo
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

// --- 상태 정의 ---
sealed class PendingDriversUiState {
    object Loading : PendingDriversUiState()
    data class Success(val drivers: List<PendingDriverInfo>) : PendingDriversUiState()
    data class Error(val message: String) : PendingDriversUiState()
}

sealed class DriverApprovalState {
    object Idle : DriverApprovalState()
    object Loading : DriverApprovalState()
    data class Success(val driverName: String, val approved: Boolean) : DriverApprovalState() // 승인/거절 결과
    data class Error(val message: String) : DriverApprovalState()
}
// --- ---

class PendingDriversViewModel(
    application: Application,
    private val regionId: String,
    private val officeId: String
) : AndroidViewModel(application) {
    private val firestore: FirebaseFirestore = Firebase.firestore

    private val _uiState = MutableStateFlow<PendingDriversUiState>(PendingDriversUiState.Loading)
    val uiState: StateFlow<PendingDriversUiState> = _uiState.asStateFlow()

    private val _approvalState = MutableStateFlow<DriverApprovalState>(DriverApprovalState.Idle)
    val approvalState: StateFlow<DriverApprovalState> = _approvalState.asStateFlow()

    init {
        android.util.Log.w("PendingDriversVM_INIT", "=== PendingDriversViewModel 초기화 ===")
        android.util.Log.w("PendingDriversVM_INIT", "regionId: '$regionId', officeId: '$officeId'")
        
        if (regionId.isBlank() || officeId.isBlank()) {
            android.util.Log.e("PendingDriversVM_INIT", "지역/사무실 ID가 유효하지 않음!")
            _uiState.value = PendingDriversUiState.Error("관리자 정보(지역/사무실 ID)가 유효하지 않습니다.")
        } else {
            android.util.Log.w("PendingDriversVM_INIT", "fetchPendingDrivers 호출")
            fetchPendingDrivers()
        }
    }

    fun fetchPendingDrivers() {
        android.util.Log.w("PendingDriversVM_DEBUG", "=== fetchPendingDrivers 시작 ===")
        android.util.Log.w("PendingDriversVM_DEBUG", "regionId: '$regionId', officeId: '$officeId'")
        
        if (regionId.isBlank() || officeId.isBlank()) {
            android.util.Log.e("PendingDriversVM_DEBUG", "지역/사무실 ID가 비어있음!")
            _uiState.value = PendingDriversUiState.Error("관리자 정보(지역/사무실 ID)가 유효하지 않습니다.")
            return
        }

        _uiState.value = PendingDriversUiState.Loading
        viewModelScope.launch {
            try {
                android.util.Log.w("PendingDriversVM_DEBUG", "Firestore 쿼리 시작 - pending_drivers 컬렉션")
                
                // 1단계: 전체 pending_drivers 확인
                val allPendingSnapshot = firestore.collection("pending_drivers").get().await()
                android.util.Log.w("PendingDriversVM_DEBUG", "전체 pending_drivers 문서 수: ${allPendingSnapshot.size()}")
                
                allPendingSnapshot.documents.forEach { doc ->
                    android.util.Log.w("PendingDriversVM_DEBUG", "pending_drivers 문서: ${doc.id} = ${doc.data}")
                }
                
                // 2단계: 특정 지역/사무실의 기사만 필터링
                val snapshot = firestore.collection("pending_drivers")
                    .whereEqualTo("targetRegionId", regionId)
                    .whereEqualTo("targetOfficeId", officeId)
                    .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.ASCENDING)
                    .get()
                    .await()
                android.util.Log.w("PendingDriversVM_DEBUG", "필터링 후 문서 수: ${snapshot.size()}")

                val driverList = snapshot.documents.mapNotNull { doc ->
                    try {
                        android.util.Log.w("PendingDriversVM_DEBUG", "문서 파싱 중: ${doc.id}")
                        val parsedDriver = doc.toObject(PendingDriverInfo::class.java)
                        android.util.Log.w("PendingDriversVM_DEBUG", "파싱 결과: $parsedDriver")
                        
                        if (parsedDriver?.authUid == null) {
                            android.util.Log.w("PendingDriversVM_DEBUG", "authUid가 null, 문서 ID 사용: ${doc.id}")
                            parsedDriver?.copy(authUid = doc.id)
                        } else {
                            parsedDriver
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("PendingDriversVM_DEBUG", "문서 파싱 실패: ${doc.id}", e)
                        null
                    }
                }
                
                android.util.Log.w("PendingDriversVM_DEBUG", "최종 승인 대기 목록 크기: ${driverList.size}")
                driverList.forEach { driver ->
                    android.util.Log.w("PendingDriversVM_DEBUG", "승인 대기 기사: ${driver.name} (${driver.driverType})")
                }
                
                _uiState.value = PendingDriversUiState.Success(driverList)
                android.util.Log.w("PendingDriversVM_DEBUG", "=== fetchPendingDrivers 완료 ===")
                
            } catch (e: Exception) {
                android.util.Log.e("PendingDriversVM_DEBUG", "fetchPendingDrivers 실패", e)
                _uiState.value = PendingDriversUiState.Error("승인 대기 목록 로드 실패: ${e.message}")
            }
        }
    }

    fun approveDriver(driverInfo: PendingDriverInfo) {
        android.util.Log.w("PendingDriversVM", "승인 시작 - ${driverInfo.name} (${driverInfo.driverType})")
        val driverUid = driverInfo.authUid
        if (driverUid.isNullOrBlank()) {
            android.util.Log.e("PendingDriversVM", "승인 실패: authUid 없음")
            _approvalState.value = DriverApprovalState.Error("승인 실패: 기사 고유 ID(authUid)가 없습니다.")
            return
        }
        if (driverInfo.targetRegionId.isNullOrBlank() || driverInfo.targetOfficeId.isNullOrBlank()) {
            android.util.Log.e("PendingDriversVM", "승인 실패: 대상 정보 없음 - regionId=${driverInfo.targetRegionId}, officeId=${driverInfo.targetOfficeId}")
            _approvalState.value = DriverApprovalState.Error("승인 실패: 기사 정보에 대상 지역/사무실 ID가 없습니다.")
            return
        }
        android.util.Log.w("PendingDriversVM", "승인 진행 - UID: $driverUid, 대상: ${driverInfo.targetRegionId}/${driverInfo.targetOfficeId}")
        _approvalState.value = DriverApprovalState.Loading
        viewModelScope.launch {
            try {
                // 1. 최종 기사 데이터 생성 (DriverInfo 모델에 맞춰 필드 구성)
                val finalDriverData: Map<String, Any?> = mapOf(
                    "id" to driverUid, // 문서 ID를 authUid와 동일하게 사용
                    "authUid" to driverUid,
                    "name" to driverInfo.name,
                    "phoneNumber" to driverInfo.phoneNumber,
                    "email" to driverInfo.email,
                    "driverType" to driverInfo.driverType,
                    
                    // 기사 운행 상태: DriverStatus Enum의 value 사용 (예: "오프라인")
                    "status" to Constants.DRIVER_STATUS_OFFLINE,

                    // 기사 가입 승인 상태: DriverApprovalStatus Enum의 name 사용 (예: "APPROVED")
                    "approvalStatus" to Constants.APPROVAL_STATUS_APPROVED,

                    "regionId" to driverInfo.targetRegionId,
                    "officeId" to driverInfo.targetOfficeId,
                    
                    // 픽업앱 로그인 시 필요한 associatedOfficeId 필드 추가
                    "associatedOfficeId" to driverInfo.targetOfficeId,
                    
                    "createdAt" to (driverInfo.requestedAt ?: com.google.firebase.Timestamp.now()),
                    "updatedAt" to com.google.firebase.Timestamp.now(),
                    "approvedAt" to com.google.firebase.Timestamp.now(),
                    "isActive" to true,
                    "rating" to 0f,
                    "totalTrips" to 0
                )

                // 2. 최종 경로 참조 (기사 타입에 따라 컬렉션 결정)
                android.util.Log.w("PendingDriversVM", "원본 driverType 값: '${driverInfo.driverType}'")
                
                val normalizedType = driverInfo.driverType.trim()
                val driverCollection = when {
                    normalizedType.equals("PICKUP", ignoreCase = true) -> "pickup_drivers"
                    normalizedType == "픽업기사" -> "pickup_drivers"  // 한국어 대응
                    normalizedType.equals("DESIGNATED", ignoreCase = true) -> "designated_drivers"
                    normalizedType == "대리기사" -> "designated_drivers"  // 한국어 대응
                    else -> {
                        android.util.Log.w("PendingDriversVM", "알 수 없는 driverType: '${driverInfo.driverType}', designated_drivers로 기본 설정")
                        "designated_drivers" // 기본적으로 대리 기사
                    }
                }
                android.util.Log.w("PendingDriversVM", "선택된 드라이버 컬렉션: $driverCollection")
                
                val finalDriverDocRef = firestore.collection("regions").document(driverInfo.targetRegionId)
                    .collection("offices").document(driverInfo.targetOfficeId)
                    .collection(driverCollection).document(driverUid)
                android.util.Log.w("PendingDriversVM", "최종 경로: ${finalDriverDocRef.path}")

                // 3. 승인 대기 문서 참조
                val pendingDriverDocRef = firestore.collection("pending_drivers").document(driverUid)
                android.util.Log.w("PendingDriversVM", "Pending 경로: ${pendingDriverDocRef.path}")

                android.util.Log.w("PendingDriversVM", "최종 데이터: $finalDriverData")

                // 4. 단계별 실행으로 변경 (디버깅용)
                android.util.Log.w("PendingDriversVM", "$driverCollection 문서 생성 시작")
                try {
                    finalDriverDocRef.set(finalDriverData).await()
                    android.util.Log.w("PendingDriversVM", "$driverCollection 문서 생성 성공")
                } catch (e: Exception) {
                    android.util.Log.e("PendingDriversVM", "$driverCollection 문서 생성 실패", e)
                    throw e
                }
                
                android.util.Log.w("PendingDriversVM", "pending_drivers 문서 삭제 시작")
                try {
                    pendingDriverDocRef.delete().await()
                    android.util.Log.w("PendingDriversVM", "pending_drivers 문서 삭제 성공")
                } catch (e: Exception) {
                    android.util.Log.e("PendingDriversVM", "pending_drivers 문서 삭제 실패", e)
                    // 삭제 실패해도 계속 진행 (이미 생성된 pickup_drivers는 유지)
                }

                _approvalState.value = DriverApprovalState.Success(driverInfo.name ?: "(이름 없음)", true)
                android.util.Log.w("PendingDriversVM", "승인 성공")
                fetchPendingDrivers() // 목록 새로고침

            } catch (e: Exception) {
                android.util.Log.e("PendingDriversVM", "승인 실패", e)
                android.util.Log.e("PendingDriversVM", "예외 타입: ${e.javaClass.simpleName}")
                android.util.Log.e("PendingDriversVM", "예외 메시지: ${e.message}")
                _approvalState.value = DriverApprovalState.Error("기사 승인 중 오류 발생: ${e.message}")
            }
        }
    }

    fun rejectDriver(driverInfo: PendingDriverInfo) {
        val driverUid = driverInfo.authUid
        if (driverUid.isNullOrBlank()) {
            _approvalState.value = DriverApprovalState.Error("거절 실패: 기사 고유 ID(authUid)가 없습니다.")
            return
        }
        _approvalState.value = DriverApprovalState.Loading
        viewModelScope.launch {
            try {
                // 승인 대기 문서 삭제
                val pendingDriverDocRef = firestore.collection("pending_drivers").document(driverUid)
                pendingDriverDocRef.delete().await()

                _approvalState.value = DriverApprovalState.Success(driverInfo.name ?: "(이름 없음)", false)
                fetchPendingDrivers() // 목록 새로고침

            } catch (e: Exception) {
                _approvalState.value = DriverApprovalState.Error("기사 거절 중 오류 발생: ${e.message}")
            }
        }
    }

    fun resetApprovalState() {
        _approvalState.value = DriverApprovalState.Idle
    }

    class Factory(private val application: Application, private val regionId: String, private val officeId: String) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(PendingDriversViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return PendingDriversViewModel(application, regionId, officeId) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
} 