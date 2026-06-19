package com.designated.callmanager.ui.chat

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.designated.callmanager.CallManagerApplication
import com.designated.callmanager.data.local.LocalChatMessage
import com.designated.callmanager.data.repository.ChatRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File

/**
 * 사무실 단톡방 ViewModel (call_manager)
 *
 * 책임:
 *  - SharedPreferences("login_prefs")에서 provinceId/cityId/officeId 로드
 *  - admins/{uid}.name 필드를 senderName으로 사용 (없으면 "관리자" fallback)
 *  - senderRole = "MANAGER" 고정 (call_manager는 관리자 앱)
 *  - 첫 진입 시 chatRepository.loadInitialMessages 1회 호출
 *  - 메시지 전송/재시도 위임
 *
 * 관련 스펙: docs/chat-shared-spec.md
 */
class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val chatRepository: ChatRepository =
        (application as CallManagerApplication).chatRepository

    private val prefs = application.getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
    private val provinceId: String = prefs.getString("provinceId", "") ?: ""
    private val cityId: String = prefs.getString("cityId", "") ?: ""
    private val officeId: String = prefs.getString("officeId", "") ?: ""

    private val senderId: String = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    private val senderRole: String = ChatRepository.ROLE_MANAGER

    private val _senderName = MutableStateFlow<String?>(null)
    val senderName: StateFlow<String?> = _senderName.asStateFlow()

    // 채팅 입력바 텍스트 (외부 공유 prefill 수신을 위해 ViewModel 보유)
    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    // 외부에서 채팅 BottomSheet 펼침 요청 (ACTION_SEND 수신 시 1회 trigger)
    private val _shouldExpandSheet = MutableStateFlow(false)
    val shouldExpandSheet: StateFlow<Boolean> = _shouldExpandSheet.asStateFlow()

    private val isReady: Boolean
        get() = provinceId.isNotBlank() && cityId.isNotBlank() &&
                officeId.isNotBlank() && senderId.isNotBlank()

    val currentUserId: String = senderId

    val messages: StateFlow<List<LocalChatMessage>> = if (isReady) {
        chatRepository.getMessagesFlow(provinceId, cityId, officeId)
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    } else {
        MutableStateFlow(emptyList())
    }

    val latestMessage: StateFlow<LocalChatMessage?> = if (isReady) {
        chatRepository.getLatestMessageFlow(provinceId, cityId, officeId)
            .stateIn(viewModelScope, SharingStarted.Lazily, null)
    } else {
        MutableStateFlow(null)
    }

    init {
        if (isReady) {
            viewModelScope.launch {
                fetchSenderName()
                // Local-first: Room이 비어있을 때만 1회 fetch (앱 시작마다 50 read 방지)
                runCatching {
                    if (chatRepository.isEmptyInOffice(provinceId, cityId, officeId)) {
                        chatRepository.loadInitialMessages(provinceId, cityId, officeId)
                    }
                }.onFailure { e ->
                    Log.e(TAG, "[init] loadInitialMessages 실패", e)
                }
            }
        } else {
            Log.w(TAG, "[init] 필수 정보 부족 - provinceId=$provinceId, cityId=$cityId, officeId=$officeId, senderId=$senderId")
        }
    }

    /**
     * 블랙박스(시스템) 메시지 증분 풀 — 채팅 시트 열 때마다 호출(init 아님: VM은 Activity 1회 생성이라 init은 1회뿐).
     *  시스템 메시지는 FCM 미푸시라 여기서 마지막 동기화 이후만 당겨 Room INSERT. 평소 안 열면 read 0.
     */
    fun syncMessages() {
        if (!isReady) return
        viewModelScope.launch {
            runCatching { chatRepository.syncSince(provinceId, cityId, officeId) }
                .onFailure { e -> Log.e(TAG, "[syncMessages] 실패", e) }
        }
    }

    private suspend fun fetchSenderName() {
        try {
            val firestore = FirebaseFirestore.getInstance()
            val doc = firestore.collection("admins").document(senderId).get().await()
            val name = doc.getString("name")
            _senderName.value = if (!name.isNullOrBlank()) name else "관리자"
            Log.d(TAG, "[fetchSenderName] senderName=${_senderName.value}")
        } catch (e: Exception) {
            Log.e(TAG, "[fetchSenderName] admins 조회 실패", e)
            _senderName.value = "관리자"
        }
    }

    fun sendMessage(text: String) {
        if (!isReady) {
            Log.w(TAG, "[sendMessage] 필수 정보 부족 - 전송 스킵")
            return
        }
        val name = _senderName.value
        if (name.isNullOrBlank()) {
            Log.w(TAG, "[sendMessage] senderName 미로드 - 전송 스킵")
            return
        }
        chatRepository.sendMessage(provinceId, cityId, officeId, senderId, name, senderRole, text)
    }

    fun retryMessage(message: LocalChatMessage) {
        chatRepository.retryMessage(message)
    }

    fun setInputText(text: String) {
        _inputText.value = text
    }

    fun clearInputText() {
        _inputText.value = ""
    }

    fun requestExpandSheet() {
        _shouldExpandSheet.value = true
    }

    fun consumeExpandRequest() {
        _shouldExpandSheet.value = false
    }

    /**
     * 이미지 메시지 전송 — 갤러리 picker 결과 Uri를 그대로 Repository에 위임.
     * Repository가 EXIF + 압축 + Storage 업로드 + Firestore set + markImageSent 처리.
     */
    fun sendImageMessage(uri: Uri) {
        if (!isReady) {
            Log.w(TAG, "[sendImageMessage] 필수 정보 부족 - 전송 스킵")
            return
        }
        val name = _senderName.value
        if (name.isNullOrBlank()) {
            Log.w(TAG, "[sendImageMessage] senderName 미로드 - 전송 스킵")
            return
        }
        chatRepository.sendImageMessage(provinceId, cityId, officeId, senderId, name, senderRole, uri)
    }

    /**
     * PTT 콜드 발화 음성 메모 전송 — 녹음 파일을 audio 첨부 메시지로 게시(autoplay=true).
     * Repository가 Storage 업로드 + Firestore set + markAudioSent + 임시파일 삭제 처리.
     */
    fun sendPttVoiceMemo(file: File, durationMs: Long) {
        if (!isReady) {
            Log.w(TAG, "[sendPttVoiceMemo] 필수 정보 부족 - 전송 스킵")
            runCatching { file.delete() }
            return
        }
        val name = _senderName.value ?: "관리자" // PTT는 name 미로드여도 드롭 안 함(녹음 보존)
        chatRepository.sendAudioMessage(
            provinceId, cityId, officeId, senderId, name, senderRole,
            file = file, durationMs = durationMs, autoplay = true,
        )
    }

    /**
     * [STT] PTT 라이브 발화 전사 텍스트 → 무음 PTT-텍스트 메시지(type="ptt").
     * 이미 라이브로 들은 내용의 텍스트 기록이라 무음(발신자 있는 일반 말풍선, 소리·알림 0).
     */
    fun sendPttText(text: String) {
        if (!isReady) {
            Log.w(TAG, "[sendPttText] 필수 정보 부족 - 전송 스킵")
            return
        }
        val name = _senderName.value ?: "관리자" // PTT는 name 미로드여도 드롭 안 함
        chatRepository.sendPttText(provinceId, cityId, officeId, senderId, name, senderRole, text)
    }

    companion object {
        private const val TAG = "ChatViewModel"
    }
}
