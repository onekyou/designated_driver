package com.designated.callmanager

import android.app.Application
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.designated.callmanager.data.local.AppDatabase
import com.designated.callmanager.data.repository.CallRepository
import com.designated.callmanager.data.repository.ChatRepository
import com.designated.callmanager.data.repository.DriverRepository
import com.designated.callmanager.service.PresenceManager
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * CallManager 애플리케이션 클래스
 *
 * Local-First 아키텍처:
 * - AppDatabase: 로컬 Room 데이터베이스
 * - CallRepository, DriverRepository: 데이터 관리 레이어
 * - CoroutineScope: 백그라운드 작업 관리
 */
class CallManagerApplication : Application() {

    companion object {
        private const val TAG = "CallManagerApplication"

        @Volatile
        private var INSTANCE: CallManagerApplication? = null

        fun getInstance(): CallManagerApplication {
            return INSTANCE ?: throw IllegalStateException("Application not initialized")
        }
    }

    // ========================================
    // Local-First 아키텍처 컴포넌트
    // ========================================

    /**
     * Room Database 인스턴스
     */
    val database: AppDatabase by lazy {
        AppDatabase.getDatabase(this)
    }

    /**
     * Firebase Firestore 인스턴스
     */
    val firestore: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance()
    }

    /**
     * Repository용 CoroutineScope
     * SupervisorJob: 자식 코루틴 실패 시 다른 코루틴에 영향 없음
     */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * CallRepository 인스턴스
     */
    val callRepository: CallRepository by lazy {
        CallRepository(applicationContext, database, firestore, applicationScope)
    }

    /**
     * DriverRepository 인스턴스
     */
    val driverRepository: DriverRepository by lazy {
        DriverRepository(database, firestore, applicationScope)
    }

    /**
     * ChatRepository 인스턴스 (사무실 단톡방 V1 + 이미지 V1.1)
     */
    val chatRepository: ChatRepository by lazy {
        ChatRepository(applicationContext, database, firestore, applicationScope)
    }

    override fun onCreate() {
        super.onCreate()
        INSTANCE = this

        // 앱 라이프사이클 관찰자 등록 (포그라운드/백그라운드 감지)
        ProcessLifecycleOwner.get().lifecycle.addObserver(AppLifecycleObserver())

        // 로그인 상태일 때만 Presence 초기화
        if (FirebaseAuth.getInstance().currentUser != null) {
            PresenceManager.initialize(this)
        }

        // Auth 상태 변경 리스너
        FirebaseAuth.getInstance().addAuthStateListener { auth ->
            if (auth.currentUser != null) {
                Log.d(TAG, "사용자 로그인됨 - Presence 초기화")
                PresenceManager.reinitialize(this)
            } else {
                Log.d(TAG, "사용자 로그아웃됨 - Presence 정리")
                PresenceManager.onLogout()
            }
        }

        // Firebase 초기화 상태 확인
        try {
            val firebaseApps = FirebaseApp.getApps(this)

            // Firebase Messaging 토큰 상태 확인
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.e(TAG, "FCM 토큰 조회 실패: ${task.exception?.message}")
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Firebase 상태 확인 중 오류: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * 앱 라이프사이클 관찰자
     * 포그라운드/백그라운드 전환 감지
     */
    inner class AppLifecycleObserver : DefaultLifecycleObserver {

        override fun onStart(owner: LifecycleOwner) {
            Log.d(TAG, "앱 포그라운드 전환")
            PresenceManager.onAppForeground()
        }

        override fun onStop(owner: LifecycleOwner) {
            Log.d(TAG, "앱 백그라운드 전환")
            PresenceManager.onAppBackground()
        }
    }
}