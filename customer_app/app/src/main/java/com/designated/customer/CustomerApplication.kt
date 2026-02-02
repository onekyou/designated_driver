package com.designated.customer

import android.app.Application
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.designated.customer.service.PresenceManager
import com.google.firebase.auth.FirebaseAuth

class CustomerApplication : Application() {

    companion object {
        private const val TAG = "CustomerApplication"
    }

    override fun onCreate() {
        super.onCreate()

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
