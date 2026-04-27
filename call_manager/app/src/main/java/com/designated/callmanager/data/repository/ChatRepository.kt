package com.designated.callmanager.data.repository

import android.util.Log
import com.designated.callmanager.data.local.AppDatabase
import com.designated.callmanager.data.local.LocalChatMessage
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * 채팅 Repository (사무실 단톡방 V1)
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
class ChatRepository(
    private val database: AppDatabase,
    private val firestore: FirebaseFirestore,
    private val scope: CoroutineScope,
) {
    private val chatDao = database.chatMessageDao()

    companion object {
        private const val TAG = "ChatRepository"
        private const val INITIAL_LOAD_LIMIT = 50L

        const val ROLE_MANAGER = "MANAGER"
        const val ROLE_DESIGNATED_DRIVER = "DESIGNATED_DRIVER"
        const val ROLE_PICKUP_DRIVER = "PICKUP_DRIVER"
    }

    // ===== Flow 노출 =====

    fun getMessagesFlow(provinceId: String, cityId: String, officeId: String): Flow<List<LocalChatMessage>> =
        chatDao.getMessagesFlow(provinceId, cityId, officeId)

    fun getLatestMessageFlow(provinceId: String, cityId: String, officeId: String): Flow<LocalChatMessage?> =
        chatDao.getLatestMessageFlow(provinceId, cityId, officeId)

    // ===== 보내기 (Optimistic UI) =====

    /**
     * 메시지 전송. messageId는 Firestore docId 사전 발급(클라이언트) 사용.
     * 1) 로컬 INSERT (sendStatus=SENDING) → UI 즉시 반영
     * 2) Firestore set → 성공 시 markSent(SENT), 실패 시 updateSendStatus(FAILED)
     * 3) Cloud Function onChatMessageCreated가 다른 멤버에게 FCM 발송
     */
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
                .collection("messages").document()  // Firestore docId 사전 발급
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
                createdAt = nowMs,                  // 임시값. Firestore set 후 서버 시간으로 갱신
                clientCreatedAt = nowMs,
                sendStatus = LocalChatMessage.SEND_STATUS_SENDING,
            )

            // 1) Optimistic INSERT
            chatDao.insert(local)

            // 2) Firestore set
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

                // 서버 시간 재조회 (createdAt 정확히 반영)
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

    /**
     * 실패 메시지 재시도. ViewModel은 LocalChatMessage 객체를 직접 전달.
     */
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

    // ===== 받기 (FCM 트리거) =====

    /**
     * FCM `NEW_CHAT_MESSAGE` 페이로드 수신 시 호출.
     * 본인 senderId 메시지는 skip (이미 Optimistic INSERT 됨).
     */
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

                // 본인 메시지면 skip (이미 로컬에 SENDING/SENT로 있음)
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

    // ===== 첫 진입 시 reconciliation (.get 1회) =====

    /**
     * 채팅방 첫 진입 시 호출. 최근 50건 fetch → 로컬 미존재분만 INSERT.
     * 이후 메시지는 FCM으로 트리거.
     */
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

    // ===== 정리 =====

    suspend fun clearAllMessagesInOffice(provinceId: String, cityId: String, officeId: String) {
        chatDao.deleteAllInOffice(provinceId, cityId, officeId)
    }
}
