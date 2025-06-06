package com.designated.callmanager.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import com.designated.callmanager.MainActivity
import com.designated.callmanager.R
import com.designated.callmanager.ui.dashboard.DashboardViewModel
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.core.content.ContextCompat

class CallManagerService : Service() {
    companion object {
        private const val TAG = "CallManagerService"
        private const val FOREGROUND_NOTIFICATION_ID = 1
        private const val SERVICE_CHANNEL_ID = "CallManagerServiceChannel"
        const val ACTION_CALL_UPDATED = "com.designated.callmanager.ACTION_CALL_UPDATED"
        const val EXTRA_CALL_ID = "com.designated.callmanager.EXTRA_CALL_ID"
        const val EXTRA_CALL_STATUS = "com.designated.callmanager.EXTRA_CALL_STATUS"
        const val EXTRA_CALL_SUMMARY = "com.designated.callmanager.EXTRA_CALL_SUMMARY"

        // 서비스 시작 상태 추적
        var isServiceRunning = false
    }

    private val firestore = FirebaseFirestore.getInstance()
    private lateinit var sharedPreferences: SharedPreferences

    private var callsListener: ListenerRegistration? = null
    private var connectionListener: ListenerRegistration? = null
    private var isConnected = true
    private var isInitialDataLoaded = false

    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "서비스 생성됨")
        sharedPreferences = getSharedPreferences("login_prefs", Context.MODE_PRIVATE)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                SERVICE_CHANNEL_ID,
                "콜 매니저 서비스",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "백그라운드에서 콜 매니저 서비스 실행 상태 알림"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
            Log.d(TAG, "Foreground 서비스 알림 채널 생성됨: $SERVICE_CHANNEL_ID")
        }

        startForeground(FOREGROUND_NOTIFICATION_ID, createForegroundServiceNotification())
        setupCallListener()
        isServiceRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand 호출됨, action: ${intent?.action}")
        when (intent?.action) {
            DashboardViewModel.ACTION_START_SERVICE -> {
                Log.d(TAG, "서비스 시작 액션 수신")
                if (!isServiceRunning) {
                    startForeground(
                        FOREGROUND_NOTIFICATION_ID,
                        createForegroundServiceNotification()
                    )
                    isServiceRunning = true
                    Log.d(TAG, "Foreground 서비스 시작됨 (from intent)")
                }
                setupCallListener()
            }

            DashboardViewModel.ACTION_STOP_SERVICE -> {
                Log.d(TAG, "서비스 중지 액션 수신")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                isServiceRunning = false
                Log.d(TAG, "Foreground 서비스 중지 및 자체 종료됨")
            }

            else -> {
                if (!isServiceRunning) {
                    Log.d(TAG, "서비스가 시스템에 의해 재시작됨")
                    startForeground(
                        FOREGROUND_NOTIFICATION_ID,
                        createForegroundServiceNotification()
                    )
                    isServiceRunning = true
                }
                setupCallListener()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "서비스 종료됨")
        isServiceRunning = false

        stopFirebaseListeners()
    }

    private fun createForegroundServiceNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, SERVICE_CHANNEL_ID)
            .setContentTitle("대리운전 콜 관리")
            .setContentText("서비스 실행 중")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun setupCallListener() {
        callsListener?.remove()
        callsListener = null
        isInitialDataLoaded = false

        val regionId = sharedPreferences.getString("regionId", null)
        val officeId = sharedPreferences.getString("officeId", null)

        if (regionId == null || officeId == null) {
            Log.w(
                TAG,
                "Region ID ($regionId) 또는 Office ID ($officeId)가 SharedPreferences에 설정되지 않아 콜 리스너를 시작할 수 없습니다."
            )
            updateNotification("콜 감시 오류", "관리자 정보(지역/사무실)가 설정되지 않았습니다.")
            return
        }

        val targetPath = "regions/$regionId/offices/$officeId/calls"
        Log.i(TAG, "콜 리스너 설정 시작: 경로 = $targetPath")

        callsListener = firestore.collection("regions").document(regionId)
            .collection("offices").document(officeId)
            .collection("calls")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(10)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e(TAG, "콜 리스너 오류 (Path: $targetPath): ${e.message}", e)
                    return@addSnapshotListener
                }
                if (snapshot == null) {
                    Log.w(TAG, "콜 리스너 스냅샷이 null입니다. (Path: $targetPath)")
                    return@addSnapshotListener
                }

                Log.d(
                    TAG,
                    "콜 리스너 스냅샷 수신 (Path: $targetPath): ${snapshot.documentChanges.size}개 변경 감지"
                )

                val firstLoad = !isInitialDataLoaded
                if (firstLoad) {
                    Log.d(TAG, "초기 데이터 로딩 중... 알림 표시 안 함.")
                    isInitialDataLoaded = true
                }

                for (dc in snapshot.documentChanges) {
                    val document = dc.document
                    val callId = document.id
                    Log.d(
                        TAG,
                        "  Processing change: Doc ID=$callId, Type=${dc.type}, OldIndex=${dc.oldIndex}, NewIndex=${dc.newIndex}"
                    )

                    when (dc.type) {
                        DocumentChange.Type.ADDED -> {
                            if (!firstLoad) {
                                val callStatus =
                                    document.getString("status") ?: "UNKNOWN" // 실제 상태 확인
                                Log.i(
                                    TAG,
                                    "새로운 콜 감지 (ADDED, InitialLoad=false): ID = $callId, Status = $callStatus. MainActivity로 Intent 전송 시도."
                                )

                                val mainActivityIntent = Intent(
                                    this@CallManagerService,
                                    MainActivity::class.java
                                ).apply {
                                    action = MainActivity.ACTION_SHOW_CALL_DIALOG
                                    putExtra(MainActivity.EXTRA_CALL_ID, callId)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                                }
                                try {
                                    startActivity(mainActivityIntent)
                                    Log.d(
                                        TAG,
                                        "MainActivity.ACTION_SHOW_CALL_DIALOG Intent 전송 완료 for Call ID: $callId, Status: $callStatus"
                                    )
                                } catch (e: Exception) {
                                    Log.e(
                                        TAG,
                                        "MainActivity 시작 중 오류 발생 (ACTION_SHOW_CALL_DIALOG)",
                                        e
                                    )
                                }
                            } else {
                                Log.d(TAG, "  새로운 콜이지만 초기 로딩 중이므로 알림 생략: ID = $callId")
                            }
                        }

                        DocumentChange.Type.MODIFIED -> {
                            Log.i(TAG, "콜 정보 변경 감지 (MODIFIED): ID = $callId.")
                            val status = document.getString("status") ?: "UNKNOWN"
                            // val customerName = document.getString("customerName") ?: "이름 없음" // 필요 시 사용

                            // <<< 수정 시작 >>>
                            // 1. "운행시작" 상태인지 확인
                            if (status == "운행시작") {
                                Log.i(
                                    TAG,
                                    "  >> 상태가 '운행시작'으로 변경됨. MainActivity로 Intent 전송 시도. Call ID: $callId, Status: $status"
                                )
                                // MainActivity로 ACTION_SHOW_CALL_DIALOG Intent를 보냄
                                val mainActivityIntent = Intent(
                                    this@CallManagerService,
                                    MainActivity::class.java
                                ).apply {
                                    action =
                                        MainActivity.ACTION_SHOW_CALL_DIALOG // MainActivity의 상수 사용
                                    putExtra(MainActivity.EXTRA_CALL_ID, callId)
                                    // 필요하다면 어떤 종류의 _변경_인지 알리는 추가 정보 전달 가능
                                    // putExtra("event_type", status)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                                }
                                // 배차팝업만 띄움
                                startActivity(mainActivityIntent)
                                Log.d(
                                    TAG,
                                    "  >> MainActivity.ACTION_SHOW_CALL_DIALOG Intent 전송 완료 for Call ID: $callId, Status: $status"
                                )
                                // 신규콜 접수 알림도 띄움
                                try {
                                    showCallPopupOverlay(callId, document)
                                } catch (e: Exception) {
                                    Log.e(TAG, "운행상태 변경 알림(Notification) 표시 실패: ${e.message}", e)
                                }
                            } else if (status == "완료") {
                                Log.i(
                                    TAG,
                                    "  >> 상태가 '운행완료'로 변경됨. 알림만 표시. Call ID: $callId, Status: $status"
                                )
                                // 운행완료 알림(Notification)만 띄우고, 팝업(MainActivity) 실행은 하지 않음!
                                try {
                                    showCallPopupOverlay(callId, document)
                                } catch (e: Exception) {
                                    Log.e(TAG, "운행완료 알림(Notification) 표시 실패: ${e.message}", e)
                                }
                            } else {
                                // "운행시작"/"운행완료"가 아닌 다른 상태 변경에 대한 처리 (기존 LocalBroadcast 등)
                                Log.d(
                                    TAG,
                                    "  >> 상태 변경 감지 (status: $status), 기존 LocalBroadcast 전송 로직 수행."
                                )
                                val summary = document.getString("trip_summary")
                                val intent = Intent(ACTION_CALL_UPDATED).apply {
                                    putExtra(EXTRA_CALL_ID, callId)
                                    putExtra(EXTRA_CALL_STATUS, status)
                                    summary?.let { putExtra(EXTRA_CALL_SUMMARY, it) }
                                }
                                LocalBroadcastManager.getInstance(this@CallManagerService)
                                    .sendBroadcast(intent)
                                Log.d(
                                    TAG,
                                    "    LocalBroadcast 전송 완료: Action=$ACTION_CALL_UPDATED, ID=$callId, Status=$status"
                                )
                            }
                            // <<< 수정 끝 >>>
                        }

                        DocumentChange.Type.REMOVED -> {
                            Log.i(TAG, "콜 정보 삭제 감지 (REMOVED): ID = $callId. (처리 로직 추가 필요 시)")
                        }
                    }
                }
            }
    }

    private fun stopFirebaseListeners() {
        connectionListener?.remove()
        connectionListener = null

        callsListener?.remove()
        callsListener = null
    }

    private fun startFirebaseListeners() {
        stopFirebaseListeners()

        startConnectionMonitoring()

        Log.d(TAG, "Firebase 리스너 시작됨")
    }

    private fun startConnectionMonitoring() {
        connectionListener = firestore.collection("daeri_calls")
            .limit(1)
            .addSnapshotListener { _, error ->
                if (error != null && error.code == FirebaseFirestoreException.Code.UNAVAILABLE) {
                    Log.e(TAG, "Firestore 연결 끊김: ${error.message}")
                    isConnected = false

                    updateNotification("대리운전 콜 매니저 연결 끊김", "네트워크 연결을 확인하고 있습니다...")

                    serviceScope.launch {
                        delay(3000)
                        if (!isConnected) {
                            stopFirebaseListeners()
                            startFirebaseListeners()
                        }
                    }
                } else if (!isConnected) {
                    Log.d(TAG, "Firestore 연결 복구됨")
                    isConnected = true

                    updateNotification("대리운전 콜 매니저 실행 중", "백그라운드에서 콜을 감지하고 있습니다.")
                }
            }
    }

    private fun updateNotification(title: String, content: String) {
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val updatedNotification = NotificationCompat.Builder(this, SERVICE_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(createForegroundServiceNotification().contentIntent)
            .setOngoing(true)
            .build()

        notificationManager.notify(FOREGROUND_NOTIFICATION_ID, updatedNotification)

        Log.d(TAG, "Foreground 서비스 알림 업데이트됨: $title - $content")
    }

    private fun showCallPopupOverlay(callId: String, document: DocumentSnapshot) {
        val status = document.getString("status") ?: return
        Log.d(TAG, "showCallPopupOverlay 호출됨. callId: $callId, status: $status")

        // Android Oreo (오레오, API 26) 이상에서는 알림 채널 생성 필요
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 상태별 다른 채널 ID 사용 (운행시작/운행완료 등)
        val channelId = "call_notification_channel_${status}"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelName = "Call Notifications (${status})"
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for designated driver call service (${status})"
                enableLights(true)
                lightColor = Color.RED
                enableVibration(true)
                setShowBadge(true) // 앱 아이콘에 뱃지 표시 허용
            }
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "알림 채널 생성/업데이트: $channelId (상태: $status)")
        }

        val mainActivityIntent =
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(EXTRA_CALL_ID, callId)
            }
        val pendingMainIntent = PendingIntent.getActivity(
            this,
            callId.hashCode(),
            mainActivityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingMainIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        val popupTitle: String
        val popupBody: String
        // setupCallListener에서 이미 팝업 인텐트를 보냈으므로, 여기서는 알림만 표시하고 팝업은 표시하지 않음
        val shouldShowPopup = false

        when (status) {
            "완료" -> {
                popupTitle = "운행 완료"
                val customerName = document.getString("customerName") ?: "고객"
                popupBody = "${customerName}님의 운행이 완료되었습니다."
                notificationBuilder.setContentTitle(popupTitle)
                    .setContentText(popupBody)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(popupBody))
                Log.i(TAG, "운행완료 알림 준비. callId: $callId")
            }

            "운행시작" -> {
                val customerName = document.getString("customerName") ?: "고객"
                val customerPhone = document.getString("customerPhone")
                    ?: "-" // Firestore에 customerPhone 필드가 있다고 가정
                val departure = document.getString("departure_set") ?: "정보없음"
                val destination = document.getString("destination_set") ?: "정보없음"
                val fare = document.getLong("fare")?.toString()?.let { value -> "${value}원" } ?: "정보없음"

                // 시스템 알림용 제목 및 내용
                val popupTitle = "운행 시작: ${customerName}님"
                val notificationText = "출발: $departure / 도착: $destination / 요금: $fare"
                val notificationBigText =
                    "고객명: $customerName\n연락처: $customerPhone\n출발지: $departure\n도착지: $destination\n요금: $fare"

                notificationBuilder.setContentTitle(popupTitle)
                    .setContentText(notificationText)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(notificationBigText))
                Log.i(TAG, "운행시작 알림 준비. callId: $callId")
            }

            else -> {
                Log.d(TAG, "상태 '$status'는 알림 또는 팝업 표시 대상이 아닙니다. (callId: $callId)")
                return
            }
        }

        notificationManager.notify(callId.hashCode(), notificationBuilder.build())
        Log.i(TAG, "알림만 표시됨. callId: $callId, status: $status")
        
        // 팝업 표시 코드 제거 - setupCallListener에서 이미 팝업 인텐트를 보냄
    }
}