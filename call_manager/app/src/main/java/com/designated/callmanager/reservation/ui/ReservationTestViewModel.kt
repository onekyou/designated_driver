package com.designated.callmanager.reservation.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.designated.callmanager.reservation.data.CallRecordingRepository
import com.designated.callmanager.reservation.data.ParseResult
import com.designated.callmanager.reservation.data.RecordingFile
import com.designated.callmanager.reservation.data.ReservationParseClient
import com.designated.callmanager.reservation.data.ReservationParseException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class ReservationTestUiState {
    object Idle : ReservationTestUiState()
    data class Loading(val name: String) : ReservationTestUiState()
    data class Success(val recording: RecordingFile, val result: ParseResult) : ReservationTestUiState()
    data class Error(val message: String) : ReservationTestUiState()
}

class ReservationTestViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = CallRecordingRepository(app)
    private val client = ReservationParseClient()

    private val _recordings = MutableStateFlow<List<RecordingFile>>(emptyList())
    val recordings: StateFlow<List<RecordingFile>> = _recordings.asStateFlow()

    private val _uiState = MutableStateFlow<ReservationTestUiState>(ReservationTestUiState.Idle)
    val uiState: StateFlow<ReservationTestUiState> = _uiState.asStateFlow()

    fun loadRecordings() {
        viewModelScope.launch {
            _recordings.value = withContext(Dispatchers.IO) { repo.listFromMediaStore() }
        }
    }

    /** SAF 등으로 직접 고른 파일 → 목록 앞에 추가하고 바로 채점. */
    fun onPickedUri(uri: Uri) {
        viewModelScope.launch {
            val rf = withContext(Dispatchers.IO) { repo.fromPickedUri(uri) }
            _recordings.value = listOf(rf) + _recordings.value
            parse(rf)
        }
    }

    fun parse(rf: RecordingFile) {
        _uiState.value = ReservationTestUiState.Loading(rf.displayName)
        viewModelScope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) { repo.readBytes(rf.uri) }
                    ?: throw ReservationParseException("녹음을 읽지 못했습니다.")
                val recordedAt = rf.parsed?.localDateTime ?: ""
                val result = client.parse(bytes, recordedAt) // 슬라이스 v1 = C경로(천장). W(STT)는 다음 증분.
                _uiState.value = ReservationTestUiState.Success(rf, result)
            } catch (e: ReservationParseException) {
                _uiState.value = ReservationTestUiState.Error(e.message ?: "파싱 실패")
            } catch (e: Exception) {
                _uiState.value = ReservationTestUiState.Error(e.message ?: "파싱 실패")
            }
        }
    }

    fun reset() {
        _uiState.value = ReservationTestUiState.Idle
    }
}
