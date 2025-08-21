package com.designated.callmanager.ui.popup

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.designated.callmanager.R

class RequestOverlayPermissionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_request_overlay_permission)

        val infoText = findViewById<TextView>(R.id.textOverlayInfo)
        val btnRequest = findViewById<Button>(R.id.btnRequestOverlay)
        val btnClose = findViewById<Button>(R.id.btnCloseOverlay)

        infoText.text = "이 앱은 콜 수신 시 팝업을 띄우기 위해 '다른 앱 위에 표시' 권한이 필요합니다.\n\n권한을 허용해 주세요."

        btnRequest.setOnClickListener {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + packageName))
                startActivityForResult(intent, REQUEST_CODE_OVERLAY)
            } catch (e: Exception) {
                Toast.makeText(this, "설정 화면을 열 수 없습니다.", Toast.LENGTH_SHORT).show()
            }
        }
        btnClose.setOnClickListener { finish() }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_OVERLAY) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "오버레이 권한이 허용되었습니다!", Toast.LENGTH_LONG).show()
                finish()
            } else {
                Toast.makeText(this, "오버레이 권한이 아직 허용되지 않았습니다.", Toast.LENGTH_LONG).show()
            }
        }
    }

    companion object {
        private const val REQUEST_CODE_OVERLAY = 1010
    }
}
