package com.designated.driverapp.ui.chat

import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.designated.driverapp.data.local.LocalChatMessage
import com.designated.driverapp.data.repository.ChatRepository
import com.designated.driverapp.util.SessionManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * 사무실 단톡방 ViewModel (driver_app)
 *
 * 책임:
 *  - SessionManager.currentSession 1차, SharedPreferences raw key 2차 fallback에서 provinceId/cityId/officeId 로드
 *  - senderName: SessionManager.currentSession.driverName 우선, 없으면 designated_drivers/{authUid}.name fetch
 *  - senderRole = ROLE_DESIGNATED_DRIVER 고정
 *
 * 관련 스펙: docs/chat-shared-spec.md
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val sessionManager: SessionManager,
    private val prefs: SharedPreferences,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
) : ViewModel() {

    // 1차 SessionManager, 2차 SharedPreferences raw key (LoginViewModel.kt:200-205 키와 일치)
    private val cachedSession = sessionManager.currentSession.value
    private val provinceId: String =
        cachedSession?.provinceId ?: prefs.getString("provinceId", "") ?: ""
    private val cityId: String =
        cachedSession?.cityId ?: prefs.getString("cityId", "") ?: ""
    private val officeId: String =
        cachedSession?.officeId ?: prefs.getString("officeId", "") ?: ""

    private val senderId: String = auth.currentUser?.uid ?: ""
    private val senderRole: String = ChatRepository.ROLE_DESIGNATED_DRIVER

    private val _senderName = MutableStateFlow<String?>(cachedSession?.driverName)
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
                if (_senderName.value.isNullOrBlank()) {
                    fetchSenderName()
                }
                // Local-first: Room이 비어있을 때만 50건 fetch (destructive migration 후 자동 복구)
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
     * 블랙박스(시스템) 메시지 증분 풀 — 채팅 시트 열 때마다 호출(init 아님: VM은 1회 생성).
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
            val querySnapshot = firestore
                .collectionGroup("designated_drivers")
                .whereEqualTo("authUid", senderId)
                .limit(1)
                .get()
                .await()
            val name = querySnapshot.documents.firstOrNull()?.getString("name")
            _senderName.value = if (!name.isNullOrBlank()) name else "대리기사"
            Log.d(TAG, "[fetchSenderName] senderName=${_senderName.value}")
        } catch (e: Exception) {
            Log.e(TAG, "[fetchSenderName] designated_drivers 조회 실패", e)
            _senderName.value = "대리기사"
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
     * 이미지 메시지 전송 — Repository에 위임 (EXIF + 압축 + Storage + Firestore set + markImageSent).
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

    companion object {
        private const val TAG = "ChatViewModel"
    }
}
