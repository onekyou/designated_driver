package com.designated.walkietalkie

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Environment
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class TestRecordActivity : AppCompatActivity() {
    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private lateinit var recordButton: Button
    private lateinit var outputFile: String

    private val requiredPermissions = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_test_record)

        recordButton = findViewById(R.id.recordButton)
        recordButton.setOnClickListener {
            if (checkPermissions()) {
                if (!isRecording) {
                    startRecording()
                } else {
                    stopRecording()
                }
            }
        }
    }

    private fun startRecording() {
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "audio_test_$timestamp.wav"
            
            outputFile = File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), fileName).absolutePath

            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(384000) // 384kbps for high quality
                setAudioSamplingRate(48000) // 48kHz sampling rate
                setOutputFile(outputFile)
                prepare()
                start()
            }

            isRecording = true
            recordButton.text = "녹음 중지"
            Toast.makeText(this, "녹음 시작", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            Toast.makeText(this, "녹음 시작 실패: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            isRecording = false
            recordButton.text = "녹음 시작"
            Toast.makeText(this, "녹음 파일 저장됨: $outputFile", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "녹음 중지 실패: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkPermissions(): Boolean {
        val permissionsToRequest = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest, 100)
            return false
        }
        return true
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            Toast.makeText(this, "권한이 승인되었습니다", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "권한이 필요합니다", Toast.LENGTH_LONG).show()
        }
    }

    override fun onStop() {
        super.onStop()
        if (isRecording) {
            stopRecording()
        }
    }
} 