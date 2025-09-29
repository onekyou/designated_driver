package com.designated.callmanager.ui.attribution

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.designated.callmanager.service.BasicKPIService
import com.designated.callmanager.service.BasicKPIResult
import com.designated.callmanager.service.ReadOnlyAttributionService
import com.designated.callmanager.service.AttributionStatsResult
import com.designated.callmanager.service.RecentAttributionsResult
import com.designated.callmanager.data.OfficeSettings
import com.designated.callmanager.util.QRCodeGenerator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class AttributionManagementViewModel(application: Application) : AndroidViewModel(application) {
    private val context: Context = application.applicationContext
    private val basicKPIService = BasicKPIService()
    private val attributionService = ReadOnlyAttributionService()

    private val _attributionStats = MutableStateFlow<com.designated.callmanager.service.AttributionStats?>(null)
    val attributionStats = _attributionStats.asStateFlow()

    private val _kpiMetrics = MutableStateFlow<com.designated.callmanager.service.BasicKPI?>(null)
    val kpiMetrics = _kpiMetrics.asStateFlow()

    private val _recentAttributions = MutableStateFlow<List<com.designated.callmanager.service.AttributionMatch>>(emptyList())
    val recentAttributions = _recentAttributions.asStateFlow()

    private val _officeSettings = MutableStateFlow<OfficeSettings?>(null)
    val officeSettings = _officeSettings.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    private val TAG = "AttributionManagementVM"

    fun loadAttributionData(regionId: String, officeId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            try {
                // 병렬로 데이터 로드
                coroutineScope {
                    val statsJob = async { loadAttributionStats(regionId, officeId) }
                    val kpiJob = async { loadKPIMetrics(regionId, officeId) }
                    val recentJob = async { loadRecentAttributions(regionId, officeId) }
                    val settingsJob = async { loadOfficeSettings(regionId, officeId) }

                    // 모든 작업 완료 대기
                    statsJob.await()
                    kpiJob.await()
                    recentJob.await()
                    settingsJob.await()
                }

                android.util.Log.d(TAG, "회원관리 데이터 로드 완료")

            } catch (e: Exception) {
                _errorMessage.value = "데이터를 불러오는 중 오류가 발생했습니다"
                android.util.Log.e(TAG, "데이터 로드 실패", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun loadAttributionStats(regionId: String, officeId: String) {
        try {
            val result = attributionService.getAttributionStats(regionId, officeId)
            when (result) {
                is AttributionStatsResult.Success -> {
                    _attributionStats.value = result.stats
                }
                is AttributionStatsResult.Error -> {
                    android.util.Log.e(TAG, "어트리뷰션 통계 로드 실패: ${result.message}")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "어트리뷰션 통계 로드 중 오류", e)
        }
    }

    private suspend fun loadKPIMetrics(regionId: String, officeId: String) {
        try {
            val result = basicKPIService.getBasicKPI(regionId, officeId)
            when (result) {
                is BasicKPIResult.Success -> {
                    _kpiMetrics.value = result.kpi
                }
                is BasicKPIResult.Error -> {
                    android.util.Log.e(TAG, "KPI 메트릭 로드 실패: ${result.message}")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "KPI 메트릭 로드 중 오류", e)
        }
    }

    private suspend fun loadRecentAttributions(regionId: String, officeId: String) {
        try {
            val result = attributionService.getRecentAttributions(regionId, officeId, 10)
            when (result) {
                is RecentAttributionsResult.Success -> {
                    _recentAttributions.value = result.attributions
                }
                is RecentAttributionsResult.Error -> {
                    android.util.Log.e(TAG, "최근 어트리뷰션 로드 실패: ${result.message}")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "최근 어트리뷰션 로드 중 오류", e)
        }
    }

    private suspend fun loadOfficeSettings(regionId: String, officeId: String) {
        try {
            // 기존 오피스 설정 로드 로직 (QR 코드 등)
            val settingsDoc = Firebase.firestore
                .collection("regions")
                .document(regionId)
                .collection("offices")
                .document(officeId)
                .collection("settings")
                .document("attribution")
                .get()
                .await()

            if (settingsDoc.exists()) {
                val settings = OfficeSettings(
                    officeId = officeId,
                    qrCode = settingsDoc.getString("qrCode") ?: "",
                    inviteCode = settingsDoc.getString("inviteCode") ?: "",
                    landingPageUrl = settingsDoc.getString("landingPageUrl") ?: "",
                    attributionThreshold = settingsDoc.getLong("attributionThreshold")?.toInt() ?: 70
                )
                _officeSettings.value = settings
            } else {
                _officeSettings.value = OfficeSettings(officeId = officeId)
            }

        } catch (e: Exception) {
            android.util.Log.e(TAG, "오피스 설정 로드 실패", e)
            _officeSettings.value = OfficeSettings(officeId = officeId)
        }
    }

    fun refreshData(regionId: String, officeId: String) {
        loadAttributionData(regionId, officeId)
    }

    fun clearError() {
        _errorMessage.value = null
    }

    // QR 코드 다운로드/공유/인쇄 함수들 (읽기 전용)
    private var cachedQRBitmap: Bitmap? = null

    fun downloadQRCode() {
        viewModelScope.launch {
            try {
                val qrCode = _officeSettings.value?.qrCode
                if (qrCode.isNullOrBlank()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "QR 코드 정보가 없습니다", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                // QR 코드 Bitmap 생성 (IO 쓰레드)
                val bitmap = withContext(Dispatchers.IO) {
                    cachedQRBitmap ?: QRCodeGenerator.generateQRCodeBitmap(qrCode, 1024).also {
                        cachedQRBitmap = it
                    }
                }

                if (bitmap == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "QR 코드 생성 실패", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                // MediaStore에 저장 (갤러리에 표시)
                val uri = withContext(Dispatchers.IO) {
                    QRCodeGenerator.saveQRCodeToMediaStore(context, bitmap, "qr_code_office")
                }

                withContext(Dispatchers.Main) {
                    if (uri != null) {
                        Toast.makeText(context, "QR 코드가 갤러리에 저장되었습니다", Toast.LENGTH_LONG).show()
                        android.util.Log.d(TAG, "QR 코드 다운로드 성공: $uri")
                    } else {
                        Toast.makeText(context, "QR 코드 저장 실패", Toast.LENGTH_SHORT).show()
                    }
                }

            } catch (e: Exception) {
                android.util.Log.e(TAG, "QR 코드 다운로드 실패", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "오류: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun shareQRCode() {
        viewModelScope.launch {
            try {
                val qrCode = _officeSettings.value?.qrCode
                if (qrCode.isNullOrBlank()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "QR 코드 정보가 없습니다", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                // QR 코드 Bitmap 생성 (IO 쓰레드)
                val bitmap = withContext(Dispatchers.IO) {
                    cachedQRBitmap ?: QRCodeGenerator.generateQRCodeBitmap(qrCode, 1024).also {
                        cachedQRBitmap = it
                    }
                }

                if (bitmap == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "QR 코드 생성 실패", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                // 임시 파일로 저장
                val file = withContext(Dispatchers.IO) {
                    QRCodeGenerator.saveQRCodeToFile(context, bitmap, "qr_code_share")
                }

                if (file == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "QR 코드 저장 실패", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                // FileProvider를 통해 URI 생성
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )

                // 공유 Intent 생성
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_TEXT, "우리 사무실 QR 코드입니다\n$qrCode")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                withContext(Dispatchers.Main) {
                    context.startActivity(Intent.createChooser(shareIntent, "QR 코드 공유").apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                    android.util.Log.d(TAG, "QR 코드 공유 실행")
                }

            } catch (e: Exception) {
                android.util.Log.e(TAG, "QR 코드 공유 실패", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "오류: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}