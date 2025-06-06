package com.designated.callmanager.ui.popup

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.designated.callmanager.data.CallInfo
import com.designated.callmanager.R
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.Button

class CallOverlayActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 오버레이 권한 체크
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            // 오버레이 권한이 없으면 안내 Activity로 이동
            Toast.makeText(this, "팝업 표시를 위해 오버레이 권한이 필요합니다.", Toast.LENGTH_LONG).show()
            val intent = Intent(this, RequestOverlayPermissionActivity::class.java)
            startActivity(intent)
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

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        )
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        wm.addView(view, params)
    }

    override fun onDestroy() {
        super.onDestroy()
        // 오버레이 뷰 제거 필요시 구현
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
