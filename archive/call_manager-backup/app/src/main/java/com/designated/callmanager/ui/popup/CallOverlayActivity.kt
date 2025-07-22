package com.designated.callmanager.ui.popup

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.app.NotificationCompat
import com.designated.callmanager.R
import com.designated.callmanager.data.CallInfo

class CallOverlayActivity : Activity() {
    
    private var overlayView: View? = null
    private var windowManager: WindowManager? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Log.d("CallOverlayActivity", "onCreate - 팝업 생성 시작")

        // 오버레이 권한 체크
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Log.w("CallOverlayActivity", "오버레이 권한이 없음")
            // 오버레이 권한이 없으면 일반 알림으로 대체
            showNotificationInstead()
            finish()
            return
        }

        // 오버레이 레이아웃 inflate
        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val view = inflater.inflate(R.layout.overlay_call_popup, null)

        // 콜 정보 표시 (intent extra에서 전달)
        val callName = intent.getStringExtra(EXTRA_CALL_NAME) ?: "신규 콜"
        val callPhone = intent.getStringExtra(EXTRA_CALL_PHONE) ?: "-"
        val callSummary = intent.getStringExtra(EXTRA_CALL_SUMMARY) ?: "-"

        view.findViewById<TextView>(R.id.textCallName).text = callName
        view.findViewById<TextView>(R.id.textCallPhone).text = callPhone
        view.findViewById<TextView>(R.id.textCallSummary).text = callSummary

        // 버튼 동작 예시
        view.findViewById<Button>(R.id.btnAssign).setOnClickListener {
            Toast.makeText(this, "배차 요청", Toast.LENGTH_SHORT).show()
            finish()
        }
        view.findViewById<Button>(R.id.btnHold).setOnClickListener {
            Toast.makeText(this, "보류", Toast.LENGTH_SHORT).show()
            finish()
        }
        view.findViewById<Button>(R.id.btnDelete).setOnClickListener {
            Toast.makeText(this, "삭제", Toast.LENGTH_SHORT).show()
            finish()
        }

        try {
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or 
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.TRANSLUCENT
            )
            
            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            overlayView = view
            windowManager?.addView(view, params)
            Log.d("CallOverlayActivity", "오버레이 뷰 추가 성공")
        } catch (e: Exception) {
            Log.e("CallOverlayActivity", "오버레이 뷰 추가 실패", e)
            showNotificationInstead()
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            overlayView?.let { view ->
                windowManager?.removeView(view)
                Log.d("CallOverlayActivity", "오버레이 뷰 제거됨")
            }
        } catch (e: Exception) {
            Log.e("CallOverlayActivity", "오버레이 뷰 제거 실패", e)
        }
    }
    
    private fun showNotificationInstead() {
        Log.d("CallOverlayActivity", "오버레이 대신 알림 표시")
        
        val callName = intent.getStringExtra(EXTRA_CALL_NAME) ?: "신규 콜"
        val callPhone = intent.getStringExtra(EXTRA_CALL_PHONE) ?: "-"
        val callSummary = intent.getStringExtra(EXTRA_CALL_SUMMARY) ?: "-"
        
        val channelId = "call_alert_channel"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // 알림 채널 생성 (Android 8.0 이상)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "콜 알림",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "새로운 콜 정보 알림"
                enableLights(true)
                lightColor = Color.RED
                enableVibration(true)
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
        
        // 앱 실행 인텐트
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, com.designated.callmanager.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // 알림 생성
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("🚗 새로운 콜: $callName")
            .setContentText("📞 $callPhone\n📍 $callSummary")
            .setStyle(NotificationCompat.BigTextStyle().bigText("📞 $callPhone\n📍 $callSummary"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(pendingIntent, true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()
        
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    companion object {
        const val EXTRA_CALL_NAME = "extra_call_name"
        const val EXTRA_CALL_PHONE = "extra_call_phone"
        const val EXTRA_CALL_SUMMARY = "extra_call_summary"

        fun startOverlay(context: Context, callName: String, callPhone: String, callSummary: String) {
            val intent = Intent(context, CallOverlayActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.putExtra(EXTRA_CALL_NAME, callName)
            intent.putExtra(EXTRA_CALL_PHONE, callPhone)
            intent.putExtra(EXTRA_CALL_SUMMARY, callSummary)
            context.startActivity(intent)
        }
    }
}
