package com.designated.driver.ui.home

// import android.app.Application // Hilt 주입으로 변경되므로 직접 Application 참조 제거 또는 @ApplicationContext 사용
import android.content.Context // @ApplicationContext 사용 위해 추가
import android.util.Log
// import androidx.lifecycle.AndroidViewModel // ViewModel으로 변경
import androidx.lifecycle.ViewModel // ViewModel 상속으로 변경
import com.designated.driverapp.model.CallInfo // 모델 패키지 사용
import com.designated.driverapp.model.DriverStatus // 모델 패키지 사용
import com.designated.driverapp.model.CallStatus // CallStatus import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import java.util.concurrent.atomic.AtomicBoolean
// import com.google.firebase.auth.ktx.auth // 주입받으므로 직접 초기화 제거
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
// import com.google.firebase.ktx.Firebase // 주입받으므로 직접 초기화 제거
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.Timestamp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import androidx.lifecycle.viewModelScope // viewModelScope import
import kotlinx.coroutines.tasks.await // await() 함수 import 추가
import kotlinx.coroutines.flow.combine
import com.designated.driverapp.service.DriverForegroundService
import android.content.Intent
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.* // Locale 사용
import android.content.SharedPreferences
import com.google.firebase.firestore.ktx.toObject // Firestore toObject 확장 함수 import
import kotlinx.coroutines.flow.asStateFlow // Import asStateFlow explicitly
import android.content.ServiceConnection // Import ServiceConnection
import android.os.IBinder // Import IBinder
import android.content.ComponentName // Import ComponentName
import com.google.firebase.firestore.FieldValue
import dagger.hilt.android.lifecycle.HiltViewModel // HiltViewModel 추가
import dagger.hilt.android.qualifiers.ApplicationContext // ApplicationContext 추가
import javax.inject.Inject // Inject 추가

private const val TAG = "DriverViewModel"
    private val isFinalizingTrip = AtomicBoolean(false)
    private var initialLoadCompleteForAssigned = false

@HiltViewModel // HiltViewModel 어노테이션 추가
class DriverViewModel @Inject constructor( // @Inject constructor 추가
    @ApplicationContext private val appContext: Context, // Application 대신 Context 주입 및 이름 변경
    private val auth: FirebaseAuth, // FirebaseAuth 주입
    private val firestore: FirebaseFirestore, // FirebaseFirestore 주입
    private val sharedPreferences: SharedPreferences // SharedPreferences 주입
) : ViewModel() { // AndroidViewModel 대신 ViewModel 상속

    // 서비스 제어는 ViewModel 역할에서 제외 (Service 자체 관리)
    // private val appContext = application.applicationContext 

    // private val appContext = application.applicationContext // 주입받으므로 제거 또는 수정됨

    // private val auth: FirebaseAuth = Firebase.auth // 주입받으므로 제거
    // private val firestore = FirebaseFirestore.getInstance() // 주입받으므로 제거
    // private val sharedPreferences: SharedPreferences = application.getSharedPreferences("driver_prefs", Context.MODE_PRIVATE) // 주입받으므로 제거

    private val _assignedCalls = MutableStateFlow<List<CallInfo>>(emptyList())
    val assignedCalls: StateFlow<List<CallInfo>> = _assignedCalls

    // 기사 현재 상태 관리 (타입 명시)
    private val _driverStatus = MutableStateFlow<DriverStatus>(DriverStatus.OFFLINE)
    val driverStatus: StateFlow<DriverStatus> = _driverStatus

    private var assignedCallsListener: ListenerRegistration? = null
    private var driverStatusListener: ListenerRegistration? = null // 기사 상태 리스너

    // --- AuthStateListener 추가 ---
    private val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val user = firebaseAuth.currentUser
        if (user == null) {
            // 사용자가 로그아웃 됨 -> 리스너 중지 및 상태 초기화
            Log.d(TAG, "AuthStateListener: User logged out. Stopping listeners and clearing state.")
            stopListeners()
            _assignedCalls.value = emptyList()
            _driverStatus.value = DriverStatus.OFFLINE
        } else {
            // 사용자가 로그인 됨. 리스너 시작은 initializeListenersWithInfo 통해 명시적으로 이루어짐.
            Log.d(TAG, "AuthStateListener: User logged in (${user.uid}). Waiting for explicit info to initialize listeners.")
            // SharedPreferences 직접 확인 및 리스너 자동 시작 로직 제거
        }
    }
    // --- ---

    // --- 위치 관련 멤버 변수 추가 ---
    private val fusedLocationClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(appContext)
    private val geocoder: Geocoder = Geocoder(appContext, Locale.KOREA) // 한국 기준 Geocoder

    private val _currentLocationAddress = MutableStateFlow<String?>(null)
    val currentLocationAddress: StateFlow<String?> = _currentLocationAddress

    // 위치 정보 요청 상태 (로딩, 오류 등)를 위한 StateFlow (선택 사항)
    private val _locationFetchStatus = MutableStateFlow<LocationFetchStatus>(LocationFetchStatus.Idle)
    val locationFetchStatus: StateFlow<LocationFetchStatus> = _locationFetchStatus

    // --- Popup State --- 
    private val _callInfoForPopup = MutableStateFlow<CallInfo?>(null)
    val callInfoForPopup: StateFlow<CallInfo?> = _callInfoForPopup.asStateFlow()
    // --- ---

    // --- Service Binding Properties ---
    private var boundService: DriverForegroundService? = null
    private var isBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as DriverForegroundService.LocalBinder
            boundService = binder.getService()
            isBound = true
            Log.d(TAG, "DriverForegroundService connected.")
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            boundService = null
            isBound = false
            Log.d(TAG, "DriverForegroundService disconnected.")
        }
    }
    // --- ---

    private val _completedCallSummary = MutableStateFlow<CallInfo?>(null)
    val completedCallSummary: StateFlow<CallInfo?> = _completedCallSummary.asStateFlow()

    private val _completedCalls = MutableStateFlow<List<CallInfo>>(emptyList())
    val completedCalls: StateFlow<List<CallInfo>> = _completedCalls.asStateFlow()
    private var completedCallsListener: ListenerRegistration? = null

    private val _shouldNavigateToHome = MutableStateFlow(false)
    val shouldNavigateToHome: StateFlow<Boolean> = _shouldNavigateToHome.asStateFlow()

    init {
        Log.d(TAG, "ViewModel initialized.")
        auth.addAuthStateListener(authStateListener)
        // Bind to the service when ViewModel is created
        bindDriverService()
        startListeningForCompletedCalls()
    }

    // 리스너 중지 함수 추가
    private fun stopListeners() {
        Log.d(TAG, "Stopping Firestore listeners.")
        assignedCallsListener?.remove()
        assignedCallsListener = null
        driverStatusListener?.remove()
        driverStatusListener = null
    }

    // 로그인 성공 및 정보 준비 완료 시 호출될 함수
    fun initializeListenersWithInfo(regionId: String, officeId: String, driverId: String) {
        Log.d(TAG, "Initializing listeners with Info: regionId=$regionId, officeId=$officeId, driverId=$driverId")
        // auth.currentUser?.uid 와 전달받은 driverId가 일치하는지 한번 더 확인하는 것도 좋음
        if (auth.currentUser?.uid == driverId) {
            startListeningForAssignedCalls(regionId, officeId, driverId)
            startListeningForDriverStatus(regionId, officeId, driverId)
        } else {
            Log.e(TAG, "InitializeListenersWithInfo: Mismatch between auth.currentUser.uid and passed driverId. Listeners not started.")
            // 필요시 오류 처리 또는 로그아웃
        }
    }

    private fun startListeningForAssignedCalls(regionId: String, officeId: String, driverId: String) { // 파라미터 받도록 수정
        // 이미 리스너가 실행 중이면 중복 실행 방지
        if (assignedCallsListener != null) {
            Log.d(TAG, "Assigned calls listener already active.")
            return
        }

        // val driverId = auth.currentUser?.uid // 파라미터로 받음
        Log.d(TAG, "Attempting to start listener. Current user ID (from param): $driverId")
        // driverId null 체크는 initializeListenersWithInfo 에서 이미 처리되었거나, 여기서도 방어적으로 수행 가능

        Log.d(TAG, "Starting listener for calls assigned to driver: $driverId using region: $regionId, office: $officeId")

        assignedCallsListener?.remove() // 기존 리스너 확실히 제거

        // val regionId = sharedPreferences.getString("regionId", null) // 파라미터로 받음
        // val officeId = sharedPreferences.getString("officeId", null) // 파라미터로 받음

        if (regionId.isBlank() || officeId.isBlank()) { // isNullOrBlank 보다 isBlank가 여기선 더 적합할 수 있음 (null은 이미 상위에서 체크 가정)
            Log.e(TAG, "Error: regionId or officeId is blank. Cannot start assigned calls listener.")
            return
        }

        val callsQuery = firestore.collection("regions").document(regionId)
                                .collection("offices").document(officeId)
                                .collection("calls")
                                .whereEqualTo("assignedDriverId", driverId)
                                .whereIn("status", listOf(
                                    CallStatus.ASSIGNED.firestoreValue,
                                    CallStatus.ACCEPTED.firestoreValue,
                                    CallStatus.INPROGRESS.firestoreValue
                                ))
                                .orderBy("timestamp", Query.Direction.DESCENDING)
        
        val queryPathForLog = "regions/$regionId/offices/$officeId/calls (filtered by driverId AND active status)"
        Log.d(TAG, "  >> Querying path: $queryPathForLog for assignedDriverId == $driverId AND status IN [${CallStatus.ASSIGNED.firestoreValue}, ${CallStatus.ACCEPTED.firestoreValue}, ${CallStatus.INPROGRESS.firestoreValue}]")

        assignedCallsListener = callsQuery.addSnapshotListener { snapshot, e ->
            if (e != null) {
                Log.e(TAG, "🔥 Assigned calls listener error", e)
                _assignedCalls.value = emptyList() // 오류 발생 시 빈 리스트로 초기화
                return@addSnapshotListener
            }

            if (snapshot != null) {
                Log.d(TAG, "    Received snapshot. Document count: ${snapshot.size()}, Metadata: hasPendingWrites=${snapshot.metadata.hasPendingWrites()}")

                val calls = mutableListOf<CallInfo>()
                for (document in snapshot.documents) {
                    Log.d(TAG, "      Parsing document ID: ${document.id}")
                    try {
                        val callInfoPojo = document.toObject(CallInfo::class.java) // CallInfo.status는 이제 String 타입
                        if (callInfoPojo != null) {
                            calls.add(callInfoPojo.copy(id = document.id)) // id 채워서 추가
                            // 로그에는 callInfoPojo.status (String)와 callInfoPojo.statusEnum (CallStatus) 모두 사용 가능
                            Log.d(TAG, "        ✅ Parsed call: ${callInfoPojo.customerName}, String Status: ${callInfoPojo.status}, Enum for UI: ${callInfoPojo.statusEnum.displayName}")
                        } else {
                            Log.w(TAG, "        ⚠️ Document ${document.id} is null after conversion.")
                        }
                    } catch (ex: Exception) {
                        Log.e(TAG, "        ❌ Error parsing document ${document.id}", ex)
                    }
                }
                Log.d(TAG, "  ✅ Successfully parsed ${calls.size} active calls. Parsed IDs: ${calls.map { it.id }}")

                // --- 콜 배정 시 홈으로 이동 플래그 설정 ---
                if (_assignedCalls.value.isEmpty() && calls.isNotEmpty()) {
                    _shouldNavigateToHome.value = true
                }
                // --- ---

                if (!snapshot.isEmpty && calls.isEmpty() && _assignedCalls.value.isNotEmpty()) {
                    Log.w(TAG, "    -> StateFlow NOT updated. Snapshot had ${snapshot.size()} documents but parsing failed/filtered for all? Maintaining previous state (Size: ${_assignedCalls.value.size})")
                }
                else if (_assignedCalls.value != calls) {
                    Log.i(TAG, "    -> StateFlow WILL be updated. New list size: ${calls.size}. Previous size: ${_assignedCalls.value.size}")
                    _assignedCalls.value = calls
                } else {
                    Log.d(TAG, "    -> StateFlow not updated (content identical). List size: ${calls.size}")
                }

            } else {
                Log.w(TAG, "  ℹ️ Assigned calls snapshot is null. Clearing StateFlow if not already empty.")
                if (_assignedCalls.value.isNotEmpty()) {
                    _assignedCalls.value = emptyList()
                }
            }
        }
    }

    // 기사 상태 실시간 감지 리스너 추가
    private fun startListeningForDriverStatus(regionId: String, officeId: String, driverId: String) {
        if (driverStatusListener != null) {
            Log.d(TAG, "Driver status listener already active.")
            return
        }
        Log.d(TAG, "Starting listener for driver status at path: regions/$regionId/offices/$officeId/designated_drivers/$driverId")

        val driverDocRef = firestore.collection("regions").document(regionId)
            .collection("offices").document(officeId)
            .collection("designated_drivers").document(driverId)

        driverStatusListener = driverDocRef.addSnapshotListener { snapshot, e ->
            if (e != null) {
                Log.e(TAG, "🔥 Driver status listener error", e)
                return@addSnapshotListener
            }

            if (snapshot != null && snapshot.exists()) {
                val statusString = snapshot.getString("status") ?: DriverStatus.OFFLINE.value
                val newStatus = DriverStatus.fromValue(statusString)
                if (_driverStatus.value != newStatus) {
                    _driverStatus.value = newStatus
                    Log.d(TAG, "Driver status updated from Firestore: ${newStatus.displayName}")
                }
            } else {
                Log.w(TAG, "Driver document does not exist, cannot listen for status changes.")
                _driverStatus.value = DriverStatus.OFFLINE // Or some other default/error state
            }
        }
    }

    // 콜 수락 (기사 '운행준비중', 콜 '수락됨')
    fun acceptCall(callId: String) {
        val driverId = auth.currentUser?.uid ?: return
        Log.d(TAG, "콜 수락(운행 준비) 시도: callId=$callId, driverId=$driverId")
        viewModelScope.launch {
            try {
                val regionId = sharedPreferences.getString("regionId", null)
                val officeId = sharedPreferences.getString("officeId", null)
                if (regionId == null || officeId == null) {
                    Log.e(TAG, "Error accepting call: regionId or officeId is null.")
                    return@launch
                }
                
                // Construct the correct paths
                val driverDocRef = firestore.collection("regions").document(regionId)
                                        .collection("offices").document(officeId)
                                        .collection("designated_drivers").document(driverId)
                val callsCollectionRef = firestore.collection("regions").document(regionId)
                                                .collection("offices").document(officeId)
                                                .collection("calls")
                                                
                // 1. 기사 상태를 '운행준비중'으로 변경 (Use correct driverDocRef)
                driverDocRef.update("status", "운행준비중").await()
                Log.d(TAG, "기사 상태 '운행준비중'으로 변경 성공 (Path: ${driverDocRef.path})")
                // 2. 콜 상태를 '수락됨'으로 변경 
                callsCollectionRef.document(callId).update("status", "수락됨").await()
                Log.d(TAG, "콜 상태 '수락됨'으로 변경 성공")
            } catch (e: Exception) {
                Log.e(TAG, "콜 수락(운행 준비) 처리 중 오류", e)
            }
        }
    }

    // 운행 시작 (기사 '운행중', 콜 '운행시작')
    fun startDriving(
        callId: String,
        departure: String,
        destination: String,
        waypoints: String,
        fare: Int
    ) {
        val driverId = auth.currentUser?.uid ?: return
        Log.d(TAG, "운행 시작 시도: callId=$callId, driverId=$driverId")
        Log.d(TAG, "  - 전달된 정보: Dep='$departure', Dest='$destination', Way='$waypoints', Fare=$fare")
        viewModelScope.launch {
            try {
                val regionId = sharedPreferences.getString("regionId", null)
                val officeId = sharedPreferences.getString("officeId", null)
                if (regionId == null || officeId == null) {
                    Log.e(TAG, "Error starting driving: regionId or officeId is null.")
                    return@launch
                }
                
                // Construct the correct paths
                val driverDocRef = firestore.collection("regions").document(regionId)
                                        .collection("offices").document(officeId)
                                        .collection("designated_drivers").document(driverId)
                val callsCollectionRef = firestore.collection("regions").document(regionId)
                                                .collection("offices").document(officeId)
                                                .collection("calls")

                // 1. 기사 상태를 '운행중'으로 변경 (Use correct driverDocRef)
                driverDocRef.update("status", "운행중").await()
                Log.d(TAG, "기사 상태 '운행중'으로 변경 성공 (Path: ${driverDocRef.path})")

                // 2. 요약 정보 문자열 생성
                val summary = if (waypoints.isNotBlank()) {
                    "$departure - $destination ($waypoints) ${fare}원"
                } else {
                    "$departure - $destination ${fare}원"
                }
                Log.d(TAG, "  - 생성된 요약 정보: $summary")

                // 3. 콜 상태 및 추가 정보 업데이트 (Map 사용, trip_summary 추가)
                val callUpdates = mutableMapOf<String, Any?>(
                    "status" to "운행시작",
                    "departure_set" to departure,
                    "destination_set" to destination,
                    "waypoints_set" to waypoints,
                    "fare_set" to fare,
                    "trip_summary" to summary, // 생성된 요약 정보 추가
                    "updatedAt" to FieldValue.serverTimestamp() // 보안 규칙용 타임스탬프 추가
                )

                // Use the constructed callsCollectionRef
                callsCollectionRef.document(callId).update(callUpdates).await()
                Log.d(TAG, "콜 상태 '운행시작' 및 추가 정보 업데이트 성공 (요약 정보 포함)")

            } catch (e: Exception) {
                Log.e(TAG, "운행 시작 처리 중 오류", e)
            }
        }
    }

    // 길 안내 시작
    fun startNavigation(callInfo: CallInfo?) {
        if (callInfo == null) return
        Log.d(TAG, "길 안내 시작 요청: ${callInfo.customerAddress}")
        // TODO: 외부 내비게이션 앱 연동 로직 추가 (Intent 사용)
        // 예: TMap, KakaoNavi 등
    }

    // 운행 완료 버튼 클릭 시 (기사 '대기중', 콜 '정산대기중')
    fun completeCall(callId: String) {
        val driverId = auth.currentUser?.uid
        Log.i(TAG, ">>> completeCall function started. callId: $callId, driverId: $driverId")
        if (driverId == null) {
            Log.e(TAG, "   - Error: driverId is null. Cannot complete call.")
            return
        }
        viewModelScope.launch {
            Log.d(TAG, "   - Coroutine launched for completeCall.")
            try {
                Log.d(TAG, "      - Inside try block.")
                val regionId = sharedPreferences.getString("regionId", null)
                val officeId = sharedPreferences.getString("officeId", null)
                if (regionId == null || officeId == null) {
                    Log.e(TAG, "      - Error completing call: regionId or officeId is null.")
                    return@launch
                }
                val driverDocRef = firestore.collection("regions").document(regionId)
                                        .collection("offices").document(officeId)
                                        .collection("designated_drivers").document(driverId)
                val callsCollectionRef = firestore.collection("regions").document(regionId)
                                                .collection("offices").document(officeId)
                                                .collection("calls")

                Log.d(TAG, "         - Attempting to update call status to '정산대기중' for callId: $callId")
                val updates = mapOf(
                    "status" to CallStatus.AWAITING_SETTLEMENT.name,
                    "updatedAt" to FieldValue.serverTimestamp() // 보안 규칙용 타임스탬프 추가
                )
                callsCollectionRef.document(callId).update(updates).await()
                Log.d(TAG, "         - Call status update to '정산대기중' successful.")

                Log.d(TAG, "         - Attempting to update driver status to '대기중' for driverId: $driverId")
                driverDocRef.update("status", DriverStatus.WAITING.value).await()
                Log.d(TAG, "         - Driver status update to '대기중' successful. (Path: ${driverDocRef.path})")

                if (isBound) {
                    Log.d(TAG, "         - Attempting to call clearAssignedCallState on bound service.")
                    boundService?.clearAssignedCallState()
                    Log.d(TAG, "Called clearAssignedCallState on bound service.")
                } else {
                    Log.w(TAG, "Cannot clear service state: Service not bound.")
                }

                val completedDoc = callsCollectionRef.document(callId).get().await()
                val completedCallInfoPojo = try {
                    val callInfo = completedDoc.toObject<CallInfo>()?.apply { id = completedDoc.id } // CallInfo.status는 이제 String 타입
                    if (callInfo != null) {
                        Log.d(TAG, "      Raw status for completedCallInfo from Firestore: '${callInfo.status}' for doc ${completedDoc.id}")
                        Log.d(TAG, "      Parsed enum status for completedCallInfo: ${callInfo.statusEnum.displayName}")
                        callInfo
                    } else {
                        Log.e(TAG, "   - Error: Failed to parse completedDoc ${completedDoc.id} to CallInfo object.")
                        null
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "CallInfo manual parsing 실패: ${e.message}", e)
                    null
                }

                if (completedCallInfoPojo != null) {
                    showCompletedCallSummary(completedCallInfoPojo)
                } else {
                    Log.e(TAG, "   - Error: completedCallInfo is null. Cannot show summary.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "      ❌ 운행 완료 처리 중 오류 발생", e)
            }
        }
    }

    // 기사 상태 수동 업데이트
    fun updateDriverStatus(newStatus: DriverStatus) {
        val driverId = auth.currentUser?.uid ?: return
        // ON_TRIP, PREPARING 상태는 자동 관리되므로 수동 변경 제한, OFFLINE, WAITING만 허용
        if (_driverStatus.value == newStatus || newStatus == DriverStatus.ON_TRIP || newStatus == DriverStatus.PREPARING) return // BUSY를 ON_TRIP으로 변경

        val statusString = when(newStatus) { 
            DriverStatus.WAITING -> "대기중"
            DriverStatus.OFFLINE -> "오프라인"
            // ON_TRIP, PREPARING은 위에서 필터링되므로 여기에 도달하지 않음
            else -> return 
        }
        
        Log.d(TAG, "수동 상태 변경 시도: driverId=$driverId, newStatus=$statusString")
        viewModelScope.launch {
            try {
                 val regionId = sharedPreferences.getString("regionId", null)
                 val officeId = sharedPreferences.getString("officeId", null)
                 if (regionId == null || officeId == null) {
                     Log.e(TAG, "Error updating driver status: regionId or officeId is null.")
                     return@launch
                 }
                 
                 // Construct the correct path
                 val driverDocRef = firestore.collection("regions").document(regionId)
                                         .collection("offices").document(officeId)
                                         .collection("designated_drivers").document(driverId)
                 
                 // Use correct driverDocRef
                 driverDocRef.update("status", statusString).await()
                 Log.d(TAG, "기사 상태 '$statusString'으로 수동 변경 성공 (Path: ${driverDocRef.path})")
            } catch (e: Exception) {
                Log.e(TAG, "기사 상태 수동 변경 중 오류", e)
            }
        }
    }

    // ViewModel 소멸 시 리스너 정리
    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "ViewModel cleared.")
        auth.removeAuthStateListener(authStateListener)
        stopListeners()
        completedCallsListener?.remove()
        completedCallsListener = null
        // Unbind from the service when ViewModel is destroyed
        unbindDriverService()
    }

    // --- Foreground Service 시작/중지 함수 --- 
    fun startDriverService() {
        Log.d(TAG, "[ViewModel] Requesting to start DriverForegroundService")
        val serviceIntent = Intent(appContext, DriverForegroundService::class.java)
        appContext.startService(serviceIntent) // startService 사용 (Oreo 이상은 내부에서 startForeground 호출됨)
    }

    fun stopDriverService() {
        Log.d(TAG, "[ViewModel] Requesting to stop DriverForegroundService")
        val serviceIntent = Intent(appContext, DriverForegroundService::class.java)
        appContext.stopService(serviceIntent)
        // unbindDriverService() // Maybe unbind when explicitly stopping? Or rely on onCleared.
    }

    // --- Service Binding Functions ---
    private fun bindDriverService() {
        if (!isBound) {
            Log.d(TAG, "Attempting to bind DriverForegroundService...")
            Intent(appContext, DriverForegroundService::class.java).also { intent ->
                appContext.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            }
        } else {
             Log.d(TAG, "Service already bound.")
        }
    }

    private fun unbindDriverService() {
        if (isBound) {
            Log.d(TAG, "Unbinding DriverForegroundService...")
            appContext.unbindService(serviceConnection)
            isBound = false
            boundService = null
        } else {
            Log.d(TAG, "Service not bound, cannot unbind.")
        }
    }
    // --- ---

    // --- 현재 위치 주소 가져오기 함수 --- 
    fun fetchCurrentLocationAddress() {
        Log.d(TAG, "fetchCurrentLocationAddress: 요청 시작")
        _locationFetchStatus.value = LocationFetchStatus.Loading // 상태: 로딩 중

        // 1. 권한 확인
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "fetchCurrentLocationAddress: 위치 권한 없음")
            _currentLocationAddress.value = null // 이전 주소값 초기화
            _locationFetchStatus.value = LocationFetchStatus.Error("위치 권한이 필요합니다.")
            // 권한 요청은 Activity/Fragment 에서 처리해야 함
            return
        }

        // 2. 현재 위치 가져오기 (FusedLocationProviderClient 사용)
        try {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { location: Location? ->
                    if (location != null) {
                        Log.d(TAG, "fetchCurrentLocationAddress: 위치 정보 가져오기 성공 (Lat: ${location.latitude}, Lon: ${location.longitude})")
                        // 3. 좌표 -> 주소 변환 (백그라운드 스레드)
                        viewModelScope.launch(Dispatchers.IO) {
                            try {
                                // Geocoder API 버전 분기 (Android Tiramisu 이상)
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    geocoder.getFromLocation(location.latitude, location.longitude, 1) { addresses ->
                                        // GeocodeListener는 메인 스레드에서 호출될 수 있으므로 StateFlow 업데이트는 바로 가능
                                        if (addresses.isNotEmpty()) {
                                            val address = addresses[0].getAddressLine(0)
                                            Log.d(TAG, "fetchCurrentLocationAddress: 주소 변환 성공 (T+): $address")
                                            _currentLocationAddress.value = address
                                            _locationFetchStatus.value = LocationFetchStatus.Success
                                        } else {
                                            Log.w(TAG, "fetchCurrentLocationAddress: 주소 변환 결과 없음 (T+)")
                                            _currentLocationAddress.value = null
                                            _locationFetchStatus.value = LocationFetchStatus.Error("주소를 찾을 수 없습니다.")
                                        }
                                    }
                                } else {
                                    // 이전 버전 Geocoder 사용 (Deprecated 이지만 호환성 위해)
                                    @Suppress("DEPRECATION")
                                    val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                                    if (addresses != null && addresses.isNotEmpty()) {
                                        val address = addresses[0].getAddressLine(0)
                                        Log.d(TAG, "fetchCurrentLocationAddress: 주소 변환 성공 (Pre-T): $address")
                                        withContext(Dispatchers.Main) { // 메인 스레드로 전환하여 StateFlow 업데이트
                                             _currentLocationAddress.value = address
                                             _locationFetchStatus.value = LocationFetchStatus.Success
                                        }
                                    } else {
                                        Log.w(TAG, "fetchCurrentLocationAddress: 주소 변환 결과 없음 (Pre-T)")
                                        withContext(Dispatchers.Main) {
                                            _currentLocationAddress.value = null
                                            _locationFetchStatus.value = LocationFetchStatus.Error("주소를 찾을 수 없습니다.")
                                        }
                                    }
                                }
                            } catch (e: IOException) {
                                Log.e(TAG, "fetchCurrentLocationAddress: 주소 변환 중 오류", e)
                                withContext(Dispatchers.Main) {
                                     _currentLocationAddress.value = null
                                    _locationFetchStatus.value = LocationFetchStatus.Error("주소 변환 중 오류 발생")
                                }
                            }
                        }
                    } else {
                        Log.w(TAG, "fetchCurrentLocationAddress: 위치 정보 가져오기 실패 (location is null)")
                         _currentLocationAddress.value = null
                        _locationFetchStatus.value = LocationFetchStatus.Error("현재 위치를 가져올 수 없습니다.")
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "fetchCurrentLocationAddress: 위치 정보 가져오기 실패", e)
                     _currentLocationAddress.value = null
                    _locationFetchStatus.value = LocationFetchStatus.Error("위치 정보 가져오기 실패")
                }
        } catch (e: SecurityException) {
            // FusedLocationProvider 사용 시 SecurityException 발생 가능성 (이론상 권한 체크 후에는 드묾)
            Log.e(TAG, "fetchCurrentLocationAddress: 위치 접근 보안 오류", e)
             _currentLocationAddress.value = null
            _locationFetchStatus.value = LocationFetchStatus.Error("위치 권한 오류 발생")
        }
    }
    // --- --- 

    // --- Function to show call details popup --- 
    fun showCallDetailsPopup(callId: String) {
        Log.d(TAG, "showCallDetailsPopup called for callId: $callId")
        viewModelScope.launch {
            try {
                val officeId = sharedPreferences.getString("officeId", null)
                val regionId = sharedPreferences.getString("regionId", null)

                if (officeId == null || regionId == null) {
                    Log.e(TAG, "Cannot fetch call details: officeId or regionId is null.")
                    _callInfoForPopup.value = null // 또는 오류 상태 표시
                    return@launch
                }

                val callDocRef = firestore.collection("regions").document(regionId)
                    .collection("offices").document(officeId)
                    .collection("calls").document(callId)

                val callDoc = callDocRef.get().await()

                if (callDoc.exists()) {
                    val callInfoPojo = callDoc.toObject(CallInfo::class.java) // CallInfo.status는 이제 String 타입
                    if (callInfoPojo != null) {
                        _callInfoForPopup.value = callInfoPojo.copy(id = callDoc.id) // id 채워서 StateFlow에 할당
                        // 로그에는 callInfoPojo.status (String)와 callInfoPojo.statusEnum (CallStatus) 모두 사용 가능
                        Log.d(TAG, "Call details fetched for popup: ${callInfoPojo.id}, String Status: ${callInfoPojo.status}, Enum for UI: ${callInfoPojo.statusEnum.displayName}")
                    } else {
                        Log.e(TAG, "Error: Fetched call document ${callDoc.id} is null after parsing.")
                        _callInfoForPopup.value = null
                    }
                } else {
                    Log.w(TAG, "Call document $callId does not exist.")
                    _callInfoForPopup.value = null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching call details for popup: $callId", e)
                _callInfoForPopup.value = null
            }
        }
    }

    // --- Function to dismiss the popup --- 
    fun dismissCallPopup() {
        Log.d(TAG, "dismissCallPopup called.")
        _callInfoForPopup.value = null
    }
    // --- --- 

    fun showCompletedCallSummary(callInfo: CallInfo) {
        _completedCallSummary.value = callInfo
    }

    fun dismissCompletedCallSummary() {
        _completedCallSummary.value = null
    }

    private fun startListeningForCompletedCalls() {
        completedCallsListener?.remove()
        val driverId = auth.currentUser?.uid ?: return
        val regionId = sharedPreferences.getString("regionId", null)
        val officeId = sharedPreferences.getString("officeId", null)
        if (regionId == null || officeId == null) return
        val callsQuery = firestore.collection("regions").document(regionId)
            .collection("offices").document(officeId)
            .collection("calls")
            .whereEqualTo("assignedDriverId", driverId)
            .whereEqualTo("status", "완료")
            .orderBy("timestamp", Query.Direction.DESCENDING)
        completedCallsListener = callsQuery.addSnapshotListener { snapshot, e ->
            if (e != null) return@addSnapshotListener
            if (snapshot != null) {
                Log.d(TAG, "[CompletedCalls] Snapshot received. Documents: ${snapshot.size()}")
                val calls = mutableListOf<CallInfo>()
                for (document in snapshot.documents) {
                    try {
                        val callInfo = document.toObject(CallInfo::class.java)
                        if (callInfo != null) {
                            calls.add(callInfo.copy(id = document.id))
                            Log.d(TAG, "[CompletedCalls] Parsed: ${callInfo.id}, Status String: ${callInfo.status}, Status Enum: ${callInfo.statusEnum.displayName}")
                        } else {
                             Log.w(TAG, "[CompletedCalls] Document ${document.id} parsed to null CallInfo.")
                        }
                    } catch (ex: Exception) {
                        Log.e(TAG, "     ❌ Error parsing completed document ${document.id}", ex)
                    }
                }
                _completedCalls.value = calls
            }
        }
    }

    fun resetShouldNavigateToHome() {
        _shouldNavigateToHome.value = false
    }

    // 운행 완료 요약 팝업 '확인' 버튼 클릭 시 최종 처리
    fun confirmAndFinalizeTrip(
        callId: String,
        paymentMethod: String,
        cashAmount: Int?,
        fareToSet: Int,
        tripSummaryToSet: String
    ) {
        if (isFinalizingTrip.getAndSet(true)) {
            Log.d("DriverViewModel", "confirmAndFinalizeTrip is already in progress for callId: $callId. Aborting.")
            return
        }
        val driverId = auth.currentUser?.uid
        Log.i(TAG, ">>> confirmAndFinalizeTrip started. callId: $callId, driverId: $driverId, payment: $paymentMethod, cash: $cashAmount, fare: $fareToSet")
        if (driverId == null) {
            Log.e(TAG, "   - Error: driverId is null. Cannot finalize trip.")
            // _errorEvent.value = "사용자 정보를 찾을 수 없습니다. 다시 로그인해주세요." // 사용자에게 알림
            return
        }

        viewModelScope.launch {
            Log.d(TAG, "   - Coroutine launched for confirmAndFinalizeTrip.")
            try {
                val regionId = sharedPreferences.getString("regionId", null)
                val officeId = sharedPreferences.getString("officeId", null)
                if (regionId == null || officeId == null) {
                    Log.e(TAG, "      - Error finalizing trip: regionId or officeId is null.")
                    // _errorEvent.value = "지역 또는 사무실 정보를 찾을 수 없습니다." // 사용자에게 알림
                    return@launch
                }
                val callDocRef = firestore.collection("regions").document(regionId)
                    .collection("offices").document(officeId)
                    .collection("calls").document(callId)

                firestore.runTransaction { transaction ->
                    val snapshot = transaction.get(callDocRef)
                    if (!snapshot.exists()) {
                        Log.e(TAG, "      - Transaction: Document $callId does not exist!")
                        throw FirebaseFirestoreException("존재하지 않는 운행 정보입니다.", FirebaseFirestoreException.Code.NOT_FOUND)
                    }

                    val currentStatus = snapshot.getString("status")
                    val isSummaryAlreadyConfirmed = snapshot.getBoolean("isSummaryConfirmed") ?: false

                    Log.d(TAG, "      - Transaction: Current status for $callId is $currentStatus, confirmed: $isSummaryAlreadyConfirmed")

                    if (currentStatus == CallStatus.AWAITING_SETTLEMENT.name && !isSummaryAlreadyConfirmed) {
                        val updates = hashMapOf<String, Any?>(
                            "status" to CallStatus.COMPLETED.name,
                            "paymentMethod" to paymentMethod,
                            "cashAmount" to cashAmount,
                            "fare" to fareToSet,
                            "trip_summary" to tripSummaryToSet,
                            "isSummaryConfirmed" to true,
                            "summaryConfirmedTimestamp" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp() // 보안 규칙용 타임스탬프 추가
                        )
                        transaction.update(callDocRef, updates)
                        Log.d(TAG, "      - Transaction: Call $callId finalized successfully.")
                        null
                    } else {
                        if (isSummaryAlreadyConfirmed) {
                            Log.w(TAG, "      - Transaction: Call $callId summary already confirmed. Aborting.")
                             return@runTransaction "ALREADY_CONFIRMED"
                        }
                        if (currentStatus != CallStatus.AWAITING_SETTLEMENT.name) {
                            Log.w(TAG, "      - Transaction: Call $callId is not in '정산대기중' state. Current: $currentStatus. Aborting.")
                            throw FirebaseFirestoreException("콜 상태가 '정산대기중'이 아닙니다. 현재 상태: $currentStatus", FirebaseFirestoreException.Code.ABORTED)
                        }
                        throw FirebaseFirestoreException("알 수 없는 오류로 처리 중단됨.", FirebaseFirestoreException.Code.INTERNAL)
                    }
                }.await().let { transactionResult ->
                     if (transactionResult == "ALREADY_CONFIRMED") {
                        Log.i(TAG, "   - Trip finalization for $callId indicated already confirmed. Closing popup.")
                    } else {
                        Log.d(TAG, "   - Trip finalization transaction successful for callId: $callId.")
                    }
                    dismissCompletedCallSummary()
                }

            } catch (e: Exception) {
                Log.e(TAG, "      - Error in confirmAndFinalizeTrip for $callId: ${e.message}", e)
                if (e is FirebaseFirestoreException) {
                    when (e.code) {
                        FirebaseFirestoreException.Code.ABORTED -> {
                            // Log.d(TAG, "Aborted: ${e.message}")
                        }
                        FirebaseFirestoreException.Code.NOT_FOUND -> {
                            // Log.d(TAG, "Not Found: ${e.message}")
                        }
                        else -> {
                            // Log.d(TAG, "Other Firestore Exception: ${e.message}")
                        }
                    }
                }
            }
        }
    }
}

// --- 위치 정보 요청 상태 정의 (선택 사항) ---
sealed class LocationFetchStatus {
    object Idle : LocationFetchStatus() // 초기 상태
    object Loading : LocationFetchStatus() // 로딩 중
    object Success : LocationFetchStatus() // 성공
    data class Error(val message: String) : LocationFetchStatus() // 오류
}
// --- --- 