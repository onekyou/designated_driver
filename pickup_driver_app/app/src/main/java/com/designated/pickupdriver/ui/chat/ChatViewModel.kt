package com.designated.pickupdriver.ui.chat

import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.designated.pickupdriver.data.Constants
import com.designated.pickupdriver.data.local.LocalChatMessage
import com.designated.pickupdriver.data.repository.ChatRepository
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
 * 사무실 단톡방 ViewModel (pickup_driver_app)
 *
 * 책임:
 *  - SharedPreferences (Constants.PREFS_NAME)에서 provinceId/cityId/officeId 로드
 *  - pickup_drivers collectionGroup에서 authUid 매칭으로 name 필드 조회 → senderName
 *  - senderRole = ROLE_PICKUP_DRIVER 고정
 *  - 첫 진입 시 chatRepository.loadInitialMessages 1회 호출
 *
 * 관련 스펙: docs/chat-shared-spec.md
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val prefs: SharedPreferences,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
) : ViewModel() {

    private val provinceId: String = prefs.getString(Constants.PREF_KEY_PROVINCE_ID, "") ?: ""
    private val cityId: String = prefs.getString(Constants.PREF_KEY_CITY_ID, "") ?: ""
    private val officeId: String = prefs.getString(Constants.PREF_KEY_OFFICE_ID, "") ?: ""

    private val senderId: String = auth.currentUser?.uid ?: ""
    private val senderRole: String = ChatRepository.ROLE_PICKUP_DRIVER

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

    private suspend fun fetchSenderName() {
        try {
            val querySnapshot = firestore
                .collectionGroup(Constants.COLLECTION_GROUP_PICKUP_DRIVERS)
                .whereEqualTo("authUid", senderId)
                .limit(1)
                .get()
                .await()
            val name = querySnapshot.documents.firstOrNull()?.getString("name")
            _senderName.value = if (!name.isNullOrBlank()) name else "픽업기사"
            Log.d(TAG, "[fetchSenderName] senderName=${_senderName.value}")
        } catch (e: Exception) {
            Log.e(TAG, "[fetchSenderName] pickup_drivers 조회 실패", e)
            _senderName.value = "픽업기사"
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
