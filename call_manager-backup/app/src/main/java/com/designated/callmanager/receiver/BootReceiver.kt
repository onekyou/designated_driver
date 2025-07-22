package com.designated.callmanager.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.designated.callmanager.service.CallManagerService
import com.google.firebase.auth.FirebaseAuth

class BootReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || 
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            
            Log.d(TAG, "부팅 완료, 대리운전 콜 매니저 서비스 시작 시도")
            
            // 사용자가 로그인되어 있는지 확인
            val auth = FirebaseAuth.getInstance()
            if (auth.currentUser != null) {
                Log.d(TAG, "로그인된 사용자 감지됨, 서비스 시작")
                startCallManagerService(context)
            } else {
                Log.d(TAG, "로그인된 사용자 없음, 서비스 시작하지 않음")
            }
        }
    }
    
    private fun startCallManagerService(context: Context) {
        val serviceIntent = Intent(context, CallManagerService::class.java)
        
        // Android O 이상에서는 startForegroundService 호출
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
        
        Log.d(TAG, "콜 매니저 서비스 시작 요청 완료")
    }
} 