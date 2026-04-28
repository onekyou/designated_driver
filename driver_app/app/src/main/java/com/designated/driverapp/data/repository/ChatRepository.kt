package com.designated.driverapp.data.repository

import android.util.Log
import com.designated.driverapp.data.local.ChatAppDatabase
import com.designated.driverapp.data.local.LocalChatMessage
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 채팅 Repository (사무실 단톡방 V1) — driver_app
 *
 * call_manager의 ChatRepository를 그대로 이식. 패키지 + DB 클래스만 변경.
 *
 * 아키텍처:
 *  - 로컬 Room DB가 단일 진실 공급원
 *  - UI는 Flow<List<LocalChatMessage>> 구독
 *  - 보낼 때: Optimistic INSERT(SENDING) → Firestore set → 성공 시 SENT, 실패 시 FAILED
 *  - 받을 때: FCM 페이로드 → onRemoteMessageReceived → 로컬 INSERT (본인 senderId면 skip)
 *  - 첫 진입 시: loadInitialMessages로 최근 50건 fetch + 로컬 미존재분만 INSERT
 *
 * 관련 스펙: docs/chat-shared-spec.md
 */
@Singleton
class ChatRepository @Inject constructor(
    private val database: ChatAppDatabase,
    private val firestore: FirebaseFirestore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val chatDao = database.chatMessageDao()

    companion object {
        private const val TAG = "ChatRepository"
        private const val INITIAL_LOAD_LIMIT = 50L

        const val ROLE_MANAGER = "MANAGER"
        const val ROLE_DESIGNATED_DRIVER = "DESIGNATED_DRIVER"
        const val ROLE_PICKUP_DRIVER = "PICKUP_DRIVER"
    }

    fun getMessagesFlow(provinceId: String, cityId: String, officeId: String): Flow<List<LocalChatMessage>> =
        chatDao.getMessagesFlow(provinceId, cityId, officeId)

    fun getLatestMessageFlow(provinceId: String, cityId: String, officeId: String): Flow<LocalChatMessage?> =
        chatDao.getLatestMessageFlow(provinceId, cityId, officeId)

    fun sendMessage(
        provinceId: String,
        cityId: String,
        officeId: String,
        senderId: String,
        senderName: String,
        senderRole: String,
        text: String,
    ) {
        val cleanText = text.trim()
        if (cleanText.isEmpty() || cleanText.length > 2000) {
            Log.w(TAG, "[sendMessage] 텍스트 길이 검증 실패: ${cleanText.length}")
            return
        }

        scope.launch {
            val messageRef = firestore
                .collection("provinces").document(provinceId)
                .collection("cities").document(cityId)
                .collection("offices").document(officeId)
                .collection("chatRoom").document("main")
                .collection("messages").document()
            val messageId = messageRef.id
            val nowMs = System.currentTimeMillis()

            val local = LocalChatMessage(
                id = messageId,
                provinceId = provinceId,
                cityId = cityId,
                officeId = officeId,
                senderId = senderId,
                senderName = senderName,
                senderRole = senderRole,
                text = cleanText,
                createdAt = nowMs,
                clientCreatedAt = nowMs,
                sendStatus = LocalChatMessage.SEND_STATUS_SENDING,
            )

            chatDao.insert(local)

            try {
                val firestoreData = mapOf(
                    "id" to messageId,
                    "senderId" to senderId,
                    "senderName" to senderName,
                    "senderRole" to senderRole,
                    "text" to cleanText,
                    "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                    "clientCreatedAt" to nowMs,
                    "status" to "SENT",
                )
                messageRef.set(firestoreData).await()

                val serverDoc = messageRef.get().await()
                val serverCreatedAt = serverDoc.getTimestamp("createdAt")?.toDate()?.time ?: nowMs
                chatDao.markSent(messageId, serverCreatedAt)
                Log.d(TAG, "[sendMessage] 발송 성공: $messageId")
            } catch (e: Exception) {
                Log.e(TAG, "[sendMessage] 발송 실패: $messageId", e)
                chatDao.updateSendStatus(messageId, LocalChatMessage.SEND_STATUS_FAILED)
            }
        }
    }

    fun retryMessage(message: LocalChatMessage) {
        scope.launch {
            chatDao.updateSendStatus(message.id, LocalChatMessage.SEND_STATUS_SENDING)
            try {
                val messageRef = firestore
                    .collection("provinces").document(message.provinceId)
                    .collection("cities").document(message.cityId)
                    .collection("offices").document(message.officeId)
                    .collection("chatRoom").document("main")
                    .collection("messages").document(message.id)

                val firestoreData = mapOf(
                    "id" to message.id,
                    "senderId" to message.senderId,
                    "senderName" to message.senderName,
                    "senderRole" to message.senderRole,
                    "text" to message.text,
                    "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                    "clientCreatedAt" to message.clientCreatedAt,
                    "status" to "SENT",
                )
                messageRef.set(firestoreData).await()

                val serverDoc = messageRef.get().await()
                val serverCreatedAt = serverDoc.getTimestamp("createdAt")?.toDate()?.time ?: message.createdAt
                chatDao.markSent(message.id, serverCreatedAt)
                Log.d(TAG, "[retryMessage] 재전송 성공: ${message.id}")
            } catch (e: Exception) {
                Log.e(TAG, "[retryMessage] 재전송 실패: ${message.id}", e)
                chatDao.updateSendStatus(message.id, LocalChatMessage.SEND_STATUS_FAILED)
            }
        }
    }

    fun onRemoteMessageReceived(payload: Map<String, String>) {
        scope.launch(Dispatchers.IO) {
            try {
                val messageId = payload["messageId"] ?: return@launch
                val senderId = payload["senderId"] ?: return@launch
                val senderName = payload["senderName"] ?: "알 수 없음"
                val senderRole = payload["senderRole"] ?: ROLE_MANAGER
                val text = payload["text"] ?: return@launch
                val createdAt = payload["createdAt"]?.toLongOrNull() ?: System.currentTimeMillis()
                val clientCreatedAt = payload["clientCreatedAt"]?.toLongOrNull() ?: createdAt
                val provinceId = payload["provinceId"] ?: return@launch
                val cityId = payload["cityId"] ?: return@launch
                val officeId = payload["officeId"] ?: return@launch

                val currentUid = FirebaseAuth.getInstance().currentUser?.uid
                if (currentUid != null && currentUid == senderId) {
                    Log.d(TAG, "[onRemoteMessageReceived] 본인 메시지 — skip (id=$messageId)")
                    return@launch
                }

                chatDao.insert(
                    LocalChatMessage(
                        id = messageId,
                        provinceId = provinceId,
                        cityId = cityId,
                        officeId = officeId,
                        senderId = senderId,
                        senderName = senderName,
                        senderRole = senderRole,
                        text = text,
                        createdAt = createdAt,
                        clientCreatedAt = clientCreatedAt,
                        sendStatus = LocalChatMessage.SEND_STATUS_SENT,
                    )
                )
                Log.d(TAG, "[onRemoteMessageReceived] INSERT: $messageId")
            } catch (e: Exception) {
                Log.e(TAG, "[onRemoteMessageReceived] 처리 실패", e)
            }
        }
    }

    suspend fun loadInitialMessages(provinceId: String, cityId: String, officeId: String) {
        try {
            val collection = firestore
                .collection("provinces").document(provinceId)
                .collection("cities").document(cityId)
                .collection("offices").document(officeId)
                .collection("chatRoom").document("main")
                .collection("messages")

            val snapshot = collection
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(INITIAL_LOAD_LIMIT)
                .get()
                .await()

            val messages = snapshot.documents.mapNotNull { doc ->
                val senderId = doc.getString("senderId") ?: return@mapNotNull null
                val senderName = doc.getString("senderName") ?: "알 수 없음"
                val senderRole = doc.getString("senderRole") ?: ROLE_MANAGER
                val text = doc.getString("text") ?: return@mapNotNull null
                val createdAt = (doc.get("createdAt") as? Timestamp)?.toDate()?.time
                    ?: doc.getLong("clientCreatedAt") ?: return@mapNotNull null
                val clientCreatedAt = doc.getLong("clientCreatedAt") ?: createdAt

                LocalChatMessage(
                    id = doc.id,
                    provinceId = provinceId,
                    cityId = cityId,
                    officeId = officeId,
                    senderId = senderId,
                    senderName = senderName,
                    senderRole = senderRole,
                    text = text,
                    createdAt = createdAt,
                    clientCreatedAt = clientCreatedAt,
                    sendStatus = LocalChatMessage.SEND_STATUS_SENT,
                )
            }

            if (messages.isNotEmpty()) {
                chatDao.insertAll(messages)
                Log.d(TAG, "[loadInitialMessages] INSERT ${messages.size}건")
            }
        } catch (e: Exception) {
            Log.e(TAG, "[loadInitialMessages] 실패", e)
        }
    }

    suspend fun clearAllMessagesInOffice(provinceId: String, cityId: String, officeId: String) {
        chatDao.deleteAllInOffice(provinceId, cityId, officeId)
    }
}
