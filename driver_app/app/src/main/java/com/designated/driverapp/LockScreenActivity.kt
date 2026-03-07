package com.designated.driverapp

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.designated.driverapp.data.Constants
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 잠금화면 위에 표시되는 배차 알림 Activity
 * - 화면이 꺼진 상태에서 FCM 수신 시 자동으로 표시
 * - 수락 버튼 클릭 시 MainActivity로 이동
 * - 거절 버튼 클릭 시 Activity 종료
 */
class LockScreenActivity : ComponentActivity() {

    companion object {
        private const val TAG = "LockScreenActivity"
        const val EXTRA_CALL_ID = "callId"
        const val EXTRA_TITLE = "title"
        const val EXTRA_BODY = "body"
        const val EXTRA_NOTIFICATION_ID = "notificationId"
    }

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "LockScreenActivity onCreate")

        // 화면 유지 및 잠금화면 위 표시 설정
        setupWindowFlags()

        // 알림음 및 진동 시작
        startAlertSound()
        startVibration()

        val callId = intent.getStringExtra(EXTRA_CALL_ID) ?: ""
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "새 배차 알림"
        val body = intent.getStringExtra(EXTRA_BODY) ?: "새로운 콜이 배정되었습니다."
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)

        Log.d(TAG, "callId=$callId, title=$title, body=$body")

        setContent {
            LockScreenContent(
                title = title,
                body = body,
                onAccept = {
                    Log.d(TAG, "수락 버튼 클릭")
                    stopAlertSound()
                    stopVibration()
                    cancelNotification(notificationId)
                    openMainActivity(callId)
                },
                onReject = {
                    Log.d(TAG, "거절 버튼 클릭")
                    stopAlertSound()
                    stopVibration()
                    cancelNotification(notificationId)
                    rejectCallDirectly(callId)
                    finish()
                }
            )
        }
    }

    private fun setupWindowFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            // Android 8.1 (API 27) 이상
            setShowWhenLocked(true)
            setTurnScreenOn(true)

            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            // Android 8.0 이하
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }

        // 화면 유지 (모든 버전)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        Log.d(TAG, "Window flags 설정 완료")
    }

    private fun startAlertSound() {
        try {
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@LockScreenActivity, alarmUri)
                isLooping = true  // 반복 재생
                prepare()
                start()
            }
            Log.d(TAG, "알림음 시작")
        } catch (e: Exception) {
            Log.e(TAG, "알림음 재생 실패", e)
        }
    }

    private fun stopAlertSound() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.stop()
            }
            it.release()
        }
        mediaPlayer = null
        Log.d(TAG, "알림음 중지")
    }

    private fun startVibration() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        // 진동 패턴: 0ms 대기, 500ms 진동, 200ms 대기, 500ms 진동 (반복)
        val pattern = longArrayOf(0, 500, 200, 500)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))  // 0 = 반복
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, 0)
        }
        Log.d(TAG, "진동 시작")
    }

    private fun stopVibration() {
        vibrator?.cancel()
        Log.d(TAG, "진동 중지")
    }

    private fun cancelNotification(notificationId: Int) {
        if (notificationId != 0) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(notificationId)
            Log.d(TAG, "알림 취소: $notificationId")
        }
    }

    private fun rejectCallDirectly(callId: String) {
        if (callId.isBlank()) return

        val prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        val provinceId = prefs.getString(Constants.PREF_KEY_PROVINCE_ID, null)
        val cityId = prefs.getString(Constants.PREF_KEY_CITY_ID, null)
        val officeId = prefs.getString(Constants.PREF_KEY_OFFICE_ID, null)
        val driverId = FirebaseAuth.getInstance().currentUser?.uid

        if (provinceId == null || cityId == null || officeId == null || driverId == null) {
            Log.e(TAG, "거절 실패: 위치 정보 또는 로그인 정보 없음")
            Toast.makeText(this, "거절 실패 - 앱에서 다시 시도해주세요", Toast.LENGTH_SHORT).show()
            return
        }

        val db = FirebaseFirestore.getInstance()
        val callRef = db.collection(Constants.COLLECTION_PROVINCES).document(provinceId)
            .collection(Constants.COLLECTION_CITIES).document(cityId)
            .collection(Constants.COLLECTION_OFFICES).document(officeId)
            .collection(Constants.COLLECTION_CALLS).document(callId)

        val driverRef = db.collection(Constants.COLLECTION_PROVINCES).document(provinceId)
            .collection(Constants.COLLECTION_CITIES).document(cityId)
            .collection(Constants.COLLECTION_OFFICES).document(officeId)
            .collection(Constants.COLLECTION_DRIVERS).document(driverId)

        // 트랜잭션으로 콜 거절 + 기사 상태 원자적 복구
        db.runTransaction { transaction ->
            transaction.update(callRef, mapOf(
                Constants.FIELD_STATUS to Constants.STATUS_WAITING,
                "assignedDriverId" to null,
                "assignedDriverName" to null,
                "assignedDriverPhone" to null,
                "rejectedByDriver" to driverId,
                Constants.FIELD_UPDATED_AT to FieldValue.serverTimestamp()
            ))
            transaction.update(driverRef, Constants.FIELD_STATUS, Constants.STATUS_WAITING)
        }.addOnSuccessListener {
            Log.d(TAG, "콜 거절 완료: callId=$callId")
        }.addOnFailureListener { e ->
            Log.e(TAG, "콜 거절 실패: ${e.message}", e)
        }
    }

    private fun openMainActivity(callId: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("callId", callId)
        }
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAlertSound()
        stopVibration()
        Log.d(TAG, "LockScreenActivity onDestroy")
    }
}

@Composable
fun LockScreenContent(
    title: String,
    body: String,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A2E)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 아이콘 영역
            Text(
                text = "🚗",
                fontSize = 64.sp,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // 제목
            Text(
                text = title,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // 본문
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2D2D44)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = body,
                    fontSize = 18.sp,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                )
            }

            Spacer(modifier = Modifier.height(48.dp))

            // 버튼 영역
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // 거절 버튼
                Button(
                    onClick = onReject,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE74C3C)),
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .padding(horizontal = 8.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "거절",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // 수락 버튼
                Button(
                    onClick = onAccept,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF27AE60)),
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .padding(horizontal = 8.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "수락",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
