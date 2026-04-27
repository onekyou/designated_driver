package com.designated.callmanager.ui.chat

import android.app.Application
import android.content.Context
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
                runCatching {
                    chatRepository.loadInitialMessages(provinceId, cityId, officeId)
                }.onFailure { e ->
                    Log.e(TAG, "[init] loadInitialMessages 실패", e)
                }
            }
        } else {
            Log.w(TAG, "[init] 필수 정보 부족 - provinceId=$provinceId, cityId=$cityId, officeId=$officeId, senderId=$senderId")
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

    companion object {
        private const val TAG = "ChatViewModel"
    }
}
