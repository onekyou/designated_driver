package com.designated.callmanager.reservation.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.designated.callmanager.CallManagerApplication
import com.designated.callmanager.data.CallInfo
import com.designated.callmanager.reservation.data.CallRecordingRepository
import com.designated.callmanager.reservation.data.RecordingFile
import com.designated.callmanager.reservation.data.ReservationParseClient
import com.designated.callmanager.reservation.data.ReservationParseException
import com.designated.callmanager.reservation.data.UniversalReservation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 통화예약 인박스 — production 플로우(측정 하니스 ReservationTestScreen 과 분리).
 *
 * 6/8 파일럿 UX: 최근 통화녹음 목록 → 한 건 선택 → "분석중" → 써머리확인 → [확인] → 실제 콜 생성.
 * 수동 트리거(사장이 고른 통화만 처리 = PII 안전). W경로(서버 faster-whisper STT) 고정.
 */
sealed class InboxUiState {
    object Idle : InboxUiState()
    data class Analyzing(val name: String) : InboxUiState()
    data class Confirm(val recording: RecordingFile, val reservation: UniversalReservation) : InboxUiState()
    data class NotReservation(val name: String) : InboxUiState()
    data class Created(val phone: String) : InboxUiState()
    data class Error(val message: String) : InboxUiState()
}

class ReservationInboxViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = CallRecordingRepository(app)
    private val client = ReservationParseClient()
    private val callRepository by lazy { (app as CallManagerApplication).callRepository }
    private val prefs = app.getSharedPreferences("login_prefs", Context.MODE_PRIVATE)

    private val _recordings = MutableStateFlow<List<RecordingFile>>(emptyList())
    val recordings: StateFlow<List<RecordingFile>> = _recordings.asStateFlow()

    private val _uiState = MutableStateFlow<InboxUiState>(InboxUiState.Idle)
    val uiState: StateFlow<InboxUiState> = _uiState.asStateFlow()

    fun loadRecordings() {
        viewModelScope.launch {
            _recordings.value = withContext(Dispatchers.IO) { repo.listFromMediaStore() }
        }
    }

    /** 녹음 1건 분석 → 예약이면 확인 다이얼로그, 비예약이면 안내. */
    fun analyze(rf: RecordingFile) {
        _uiState.value = InboxUiState.Analyzing(rf.displayName)
        viewModelScope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) { repo.readBytes(rf.uri) }
                    ?: throw ReservationParseException("녹음을 읽지 못했습니다.")
                val recordedAt = rf.parsed?.localDateTime ?: ""
                // W경로 고정(서버 faster-whisper STT → Gemini). C는 안 함(비용·헛예약 안전).
                val result = client.parse(bytes, recordedAt, mode = "W")
                val reservation = result.w?.reservation
                    ?: throw ReservationParseException("분석 결과가 없습니다.")
                if (!reservation.isReservation) {
                    _uiState.value = InboxUiState.NotReservation(rf.displayName)
                } else {
                    _uiState.value = InboxUiState.Confirm(rf, reservation)
                }
            } catch (e: ReservationParseException) {
                _uiState.value = InboxUiState.Error(e.message ?: "분석 실패")
            } catch (e: Exception) {
                _uiState.value = InboxUiState.Error(e.message ?: "분석 실패")
            }
        }
    }

    /** 확인된 CallInfo → 실제 콜 생성. 확인 = 콜 생성 자체(별도 플래그 없음). */
    fun confirmAndCreate(callInfo: CallInfo) {
        val provinceId = prefs.getString("provinceId", null)
        val cityId = prefs.getString("cityId", null)
        val officeId = prefs.getString("officeId", null)
        if (provinceId == null || cityId == null || officeId == null) {
            _uiState.value = InboxUiState.Error("사무실 정보가 없습니다. 다시 로그인하세요.")
            return
        }
        viewModelScope.launch {
            try {
                callRepository.createCall(callInfo, provinceId, cityId, officeId)
                _uiState.value = InboxUiState.Created(callInfo.phoneNumber)
            } catch (e: Exception) {
                _uiState.value = InboxUiState.Error(e.message ?: "콜 생성 실패")
            }
        }
    }

    fun reset() {
        _uiState.value = InboxUiState.Idle
    }
}
