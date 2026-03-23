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

    private val _qrCodeBitmap = MutableStateFlow<Bitmap?>(null)
    val qrCodeBitmap = _qrCodeBitmap.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    // 사무실 정보 (수정 가능)
    private val _officePhone = MutableStateFlow("")
    val officePhone = _officePhone.asStateFlow()

    private val _bankName = MutableStateFlow("")
    val bankName = _bankName.asStateFlow()

    private val _accountNumber = MutableStateFlow("")
    val accountNumber = _accountNumber.asStateFlow()

    private val _accountHolder = MutableStateFlow("")
    val accountHolder = _accountHolder.asStateFlow()

    private val TAG = "AttributionManagementVM"

    fun loadAttributionData(provinceId: String, cityId: String, officeId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            try {
                // 병렬로 데이터 로드
                coroutineScope {
                    val statsJob = async { loadAttributionStats(provinceId, cityId, officeId) }
                    val kpiJob = async { loadKPIMetrics(provinceId, cityId, officeId) }
                    val recentJob = async { loadRecentAttributions(provinceId, cityId, officeId) }
                    val settingsJob = async { loadOfficeSettings(provinceId, cityId, officeId) }

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

    private suspend fun loadAttributionStats(provinceId: String, cityId: String, officeId: String) {
        try {
            val result = attributionService.getAttributionStats(provinceId, cityId, officeId)
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

    private suspend fun loadKPIMetrics(provinceId: String, cityId: String, officeId: String) {
        try {
            val result = basicKPIService.getBasicKPI(provinceId, cityId, officeId)
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

    private suspend fun loadRecentAttributions(provinceId: String, cityId: String, officeId: String) {
        try {
            val result = attributionService.getRecentAttributions(provinceId, cityId, officeId, 10)
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

    private suspend fun loadOfficeSettings(provinceId: String, cityId: String, officeId: String) {
        try {
            // 1. 사무실 기본 정보 로드
            val officeDoc = Firebase.firestore
                .collection("provinces")
                .document(provinceId)
                .collection("cities")
                .document(cityId)
                .collection("offices")
                .document(officeId)
                .get()
                .await()

            if (officeDoc.exists()) {
                _officePhone.value = officeDoc.getString("phone") ?: ""
                _bankName.value = officeDoc.getString("bankName") ?: ""
                _accountNumber.value = officeDoc.getString("accountNumber") ?: ""
                _accountHolder.value = officeDoc.getString("accountHolder") ?: ""
            }

            // 2. QR 설정 로드
            val settingsDoc = Firebase.firestore
                .collection("provinces")
                .document(provinceId)
                .collection("cities")
                .document(cityId)
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

                // QR 코드 Bitmap 생성
                if (settings.qrCode.isNotEmpty()) {
                    withContext(Dispatchers.Default) {
                        val bitmap = QRCodeGenerator.generateQRCodeBitmap(settings.qrCode, 512)
                        _qrCodeBitmap.value = bitmap
                    }
                }
            } else {
                _officeSettings.value = OfficeSettings(officeId = officeId)
                _qrCodeBitmap.value = null
            }

        } catch (e: Exception) {
            android.util.Log.e(TAG, "오피스 설정 로드 실패", e)
            _officeSettings.value = OfficeSettings(officeId = officeId)
        }
    }

    fun refreshData(provinceId: String, cityId: String, officeId: String) {
        loadAttributionData(provinceId, cityId, officeId)
    }

    fun clearError() {
        _errorMessage.value = null
    }

    // 사무실 정보 업데이트 함수
    fun updateOfficeInfo(
        provinceId: String,
        cityId: String,
        officeId: String,
        phone: String,
        bank: String,
        account: String,
        holder: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                _isLoading.value = true

                // 1. 사무실 기본 정보 업데이트
                Firebase.firestore
                    .collection("provinces")
                    .document(provinceId)
                    .collection("cities")
                    .document(cityId)
                    .collection("offices")
                    .document(officeId)
                    .update(
                        mapOf(
                            "phone" to phone,
                            "bankName" to bank,
                            "accountNumber" to account,
                            "accountHolder" to holder
                        )
                    )
                    .await()

                // 2. Play Store Install Referrer URL 생성
                val encodedPhone = android.net.Uri.encode(phone)
                val encodedBank = android.net.Uri.encode(bank)
                val encodedAccount = android.net.Uri.encode(account)
                val encodedHolder = android.net.Uri.encode(holder)

                val referrerParams = "p=$provinceId&c=$cityId&o=$officeId" +
                        "&phone=$encodedPhone" +
                        "&bank=$encodedBank" +
                        "&account=$encodedAccount" +
                        "&holder=$encodedHolder"

                val playStoreUrl = "https://play.google.com/store/apps/details" +
                        "?id=com.designated.customer.app" +
                        "&referrer=${android.net.Uri.encode(referrerParams)}"

                android.util.Log.d(TAG, "Play Store URL 생성 완료")

                // 3. QR 설정 업데이트
                Firebase.firestore
                    .collection("provinces")
                    .document(provinceId)
                    .collection("cities")
                    .document(cityId)
                    .collection("offices")
                    .document(officeId)
                    .collection("settings")
                    .document("attribution")
                    .update(
                        mapOf(
                            "qrCode" to playStoreUrl,
                            "landingPageUrl" to playStoreUrl
                        )
                    )
                    .await()

                // 5. 로컬 상태 업데이트
                _officePhone.value = phone
                _bankName.value = bank
                _accountNumber.value = account
                _accountHolder.value = holder

                // 4. QR 코드 재생성
                val newSettings = _officeSettings.value?.copy(
                    qrCode = playStoreUrl,
                    landingPageUrl = playStoreUrl
                )
                _officeSettings.value = newSettings

                withContext(Dispatchers.Default) {
                    val bitmap = QRCodeGenerator.generateQRCodeBitmap(playStoreUrl, 512)
                    _qrCodeBitmap.value = bitmap
                    cachedQRBitmap = null // 캐시 초기화
                }

                withContext(Dispatchers.Main) {
                    onSuccess()
                    android.util.Log.d(TAG, "사무실 정보 업데이트 및 QR 재생성 완료")
                }

            } catch (e: Exception) {
                android.util.Log.e(TAG, "사무실 정보 업데이트 실패", e)
                withContext(Dispatchers.Main) {
                    onError(e.message ?: "알 수 없는 오류")
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    // 사무실 정보 입력 업데이트 (UI용)
    fun updatePhoneInput(value: String) { _officePhone.value = value }
    fun updateBankNameInput(value: String) { _bankName.value = value }
    fun updateAccountNumberInput(value: String) { _accountNumber.value = value }
    fun updateAccountHolderInput(value: String) { _accountHolder.value = value }

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