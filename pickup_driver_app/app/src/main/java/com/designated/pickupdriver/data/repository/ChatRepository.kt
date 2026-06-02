package com.designated.pickupdriver.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.designated.pickupdriver.data.local.AppDatabase
import com.designated.pickupdriver.data.local.LocalChatMessage
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * 채팅 Repository (사무실 단톡방 V1) — pickup_driver_app
 *
 * call_manager의 ChatRepository를 그대로 이식. 패키지만 변경.
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
    @ApplicationContext private val context: Context,
    private val database: AppDatabase,
    private val firestore: FirebaseFirestore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val chatDao = database.chatMessageDao()
    private val storage by lazy { FirebaseStorage.getInstance() }

    companion object {
        private const val TAG = "ChatRepository"
        private const val INITIAL_LOAD_LIMIT = 50L

        const val ROLE_MANAGER = "MANAGER"
        const val ROLE_DESIGNATED_DRIVER = "DESIGNATED_DRIVER"
        const val ROLE_PICKUP_DRIVER = "PICKUP_DRIVER"

        // 이미지 압축 사양
        private const val IMAGE_MAX_DIM = 640
        private const val IMAGE_JPEG_QUALITY = 60
        private const val IMAGE_UPLOAD_TIMEOUT_MS = 30_000L

        // 음성 메모 (PTT 콜드 발화)
        private const val AUDIO_UPLOAD_TIMEOUT_MS = 30_000L
        private const val AUDIO_CONTENT_TYPE = "audio/mp4"
    }

    /** 이미지 업로드 결과. */
    data class UploadResult(
        val url: String,
        val path: String,
        val width: Int,
        val height: Int,
    )

    /** 음성 메모 업로드 결과. */
    data class AudioUploadResult(
        val url: String,
        val path: String,
    )

    // ===== Flow 노출 =====

    fun getMessagesFlow(provinceId: String, cityId: String, officeId: String): Flow<List<LocalChatMessage>> =
        chatDao.getMessagesFlow(provinceId, cityId, officeId)

    fun getLatestMessageFlow(provinceId: String, cityId: String, officeId: String): Flow<LocalChatMessage?> =
        chatDao.getLatestMessageFlow(provinceId, cityId, officeId)

    // ===== 보내기 (Optimistic UI) =====

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
        if (!message.imageUrl.isNullOrEmpty()) {
            Log.w(TAG, "[retryMessage] 이미지 메시지 retry 미지원 (Phase 1) — 갤러리 재선택 권장: ${message.id}")
            return
        }
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

    fun onRemoteMessageReceived(payload: Map<String, String>) {
        scope.launch(Dispatchers.IO) {
            try {
                val messageId = payload["messageId"] ?: return@launch
                val senderId = payload["senderId"] ?: return@launch
                val senderName = payload["senderName"] ?: "알 수 없음"
                val senderRole = payload["senderRole"] ?: ROLE_MANAGER
                val text = payload["text"] ?: ""
                val imageUrl = payload["imageUrl"]?.takeIf { it.isNotEmpty() }
                val imagePath = payload["imagePath"]?.takeIf { it.isNotEmpty() }
                val imageWidth = payload["imageWidth"]?.toIntOrNull()
                val imageHeight = payload["imageHeight"]?.toIntOrNull()
                val audioUrl = payload["audioUrl"]?.takeIf { it.isNotEmpty() }
                val audioPath = payload["audioPath"]?.takeIf { it.isNotEmpty() }
                val audioDurationMs = payload["audioDurationMs"]?.toLongOrNull()
                val audioAutoplay = payload["audioAutoplay"] == "true"
                val createdAt = payload["createdAt"]?.toLongOrNull() ?: System.currentTimeMillis()
                val clientCreatedAt = payload["clientCreatedAt"]?.toLongOrNull() ?: createdAt
                val provinceId = payload["provinceId"] ?: return@launch
                val cityId = payload["cityId"] ?: return@launch
                val officeId = payload["officeId"] ?: return@launch

                if (text.isBlank() && imageUrl.isNullOrEmpty() && audioUrl.isNullOrEmpty()) {
                    Log.w(TAG, "[onRemoteMessageReceived] text/imageUrl/audioUrl 모두 없음 — skip ($messageId)")
                    return@launch
                }

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
                        imageUrl = imageUrl,
                        imagePath = imagePath,
                        imageWidth = imageWidth,
                        imageHeight = imageHeight,
                        audioUrl = audioUrl,
                        audioPath = audioPath,
                        audioDurationMs = audioDurationMs,
                        audioAutoplay = audioAutoplay,
                    )
                )
                Log.d(TAG, "[onRemoteMessageReceived] INSERT: $messageId" +
                    when { imageUrl != null -> " (image)"; audioUrl != null -> " (audio)"; else -> "" })
            } catch (e: Exception) {
                Log.e(TAG, "[onRemoteMessageReceived] 처리 실패", e)
            }
        }
    }

    // ===== 첫 진입 시 reconciliation (.get 1회) =====

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
                val text = doc.getString("text") ?: ""
                val imageUrl = doc.getString("imageUrl")?.takeIf { it.isNotEmpty() }
                val imagePath = doc.getString("imagePath")?.takeIf { it.isNotEmpty() }
                val imageWidth = doc.getLong("imageWidth")?.toInt()
                val imageHeight = doc.getLong("imageHeight")?.toInt()
                val audioUrl = doc.getString("audioUrl")?.takeIf { it.isNotEmpty() }
                val audioPath = doc.getString("audioPath")?.takeIf { it.isNotEmpty() }
                val audioDurationMs = doc.getLong("audioDurationMs")
                val audioAutoplay = doc.getBoolean("audioAutoplay") ?: false
                val createdAt = (doc.get("createdAt") as? Timestamp)?.toDate()?.time
                    ?: doc.getLong("clientCreatedAt") ?: return@mapNotNull null
                val clientCreatedAt = doc.getLong("clientCreatedAt") ?: createdAt

                if (text.isBlank() && imageUrl.isNullOrEmpty() && audioUrl.isNullOrEmpty()) return@mapNotNull null

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
                    imageUrl = imageUrl,
                    imagePath = imagePath,
                    imageWidth = imageWidth,
                    imageHeight = imageHeight,
                    audioUrl = audioUrl,
                    audioPath = audioPath,
                    audioDurationMs = audioDurationMs,
                    audioAutoplay = audioAutoplay,
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

    // ===== 음성 메모 (PTT 콜드 발화) =====

    /**
     * 음성 메모 파일을 Storage에 업로드. contentType="audio/mp4" 명시(storage rule audio 타입 통과).
     * path: provinces/.../chat_audio/{messageId}.m4a
     */
    suspend fun uploadChatAudio(
        provinceId: String,
        cityId: String,
        officeId: String,
        messageId: String,
        file: File,
    ): AudioUploadResult = withContext(Dispatchers.IO) {
        val bytes = file.readBytes()
        val path = "provinces/$provinceId/cities/$cityId/offices/$officeId/chat_audio/$messageId.m4a"
        val ref = storage.reference.child(path)
        val metadata = StorageMetadata.Builder().setContentType(AUDIO_CONTENT_TYPE).build()
        val downloadUrl = withTimeoutOrNull(AUDIO_UPLOAD_TIMEOUT_MS) {
            ref.putBytes(bytes, metadata).await()
            ref.downloadUrl.await().toString()
        } ?: throw IllegalStateException("음성 업로드 30초 초과: $messageId")

        AudioUploadResult(url = downloadUrl, path = path)
    }

    /**
     * 음성 메모 메시지 전송 (PTT 콜드 발화). text는 빈 문자열, audioAutoplay 지정.
     * 흐름은 sendImageMessage 미러: Optimistic INSERT → upload → Firestore set → markAudioSent.
     * 성공/실패 무관 임시 파일 삭제.
     */
    fun sendAudioMessage(
        provinceId: String,
        cityId: String,
        officeId: String,
        senderId: String,
        senderName: String,
        senderRole: String,
        file: File,
        durationMs: Long,
        autoplay: Boolean,
    ) {
        scope.launch {
            val messageRef = firestore
                .collection("provinces").document(provinceId)
                .collection("cities").document(cityId)
                .collection("offices").document(officeId)
                .collection("chatRoom").document("main")
                .collection("messages").document()
            val messageId = messageRef.id
            val nowMs = System.currentTimeMillis()

            // 1) Optimistic INSERT — uploading sentinel UI 즉시 표시
            val placeholder = LocalChatMessage(
                id = messageId,
                provinceId = provinceId,
                cityId = cityId,
                officeId = officeId,
                senderId = senderId,
                senderName = senderName,
                senderRole = senderRole,
                text = "",
                createdAt = nowMs,
                clientCreatedAt = nowMs,
                sendStatus = LocalChatMessage.SEND_STATUS_SENDING,
                audioUrl = LocalChatMessage.UPLOADING_SENTINEL,
                audioDurationMs = durationMs,
                audioAutoplay = autoplay,
            )
            chatDao.insert(placeholder)

            // 2) 업로드 + Firestore set
            try {
                val uploaded = uploadChatAudio(provinceId, cityId, officeId, messageId, file)

                val firestoreData = mapOf(
                    "id" to messageId,
                    "senderId" to senderId,
                    "senderName" to senderName,
                    "senderRole" to senderRole,
                    "audioUrl" to uploaded.url,
                    "audioPath" to uploaded.path,
                    "audioDurationMs" to durationMs,
                    "audioAutoplay" to autoplay,
                    "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                    "clientCreatedAt" to nowMs,
                    "status" to "SENT",
                    // text 필드 omit — audio-only 메시지
                )
                messageRef.set(firestoreData).await()

                val serverDoc = messageRef.get().await()
                val serverCreatedAt = serverDoc.getTimestamp("createdAt")?.toDate()?.time ?: nowMs
                chatDao.markAudioSent(
                    id = messageId,
                    audioUrl = uploaded.url,
                    audioPath = uploaded.path,
                    audioDurationMs = durationMs,
                    createdAt = serverCreatedAt,
                )
                Log.d(TAG, "[sendAudioMessage] 발송 성공: $messageId (${durationMs}ms)")
            } catch (e: Exception) {
                Log.e(TAG, "[sendAudioMessage] 발송 실패: $messageId", e)
                // Storage cleanup (best-effort)
                try {
                    val path = "provinces/$provinceId/cities/$cityId/offices/$officeId/chat_audio/$messageId.m4a"
                    storage.reference.child(path).delete().await()
                } catch (_: Exception) { /* 업로드 자체 실패 시 파일 없음 */ }
                chatDao.updateSendStatus(messageId, LocalChatMessage.SEND_STATUS_FAILED)
            } finally {
                // 성공/실패 무관 임시 녹음 파일 삭제
                try { if (file.exists()) file.delete() } catch (_: Exception) {}
            }
        }
    }

    suspend fun clearAllMessagesInOffice(provinceId: String, cityId: String, officeId: String) {
        chatDao.deleteAllInOffice(provinceId, cityId, officeId)
    }

    // ===== Local-first 가드 =====

    suspend fun isEmptyInOffice(provinceId: String, cityId: String, officeId: String): Boolean {
        return chatDao.countInOffice(provinceId, cityId, officeId) == 0
    }

    // ===== 이미지 업로드 (V1.1) =====

    suspend fun uploadChatImage(
        provinceId: String,
        cityId: String,
        officeId: String,
        messageId: String,
        uri: Uri,
    ): UploadResult = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver

        val orientation = resolver.openInputStream(uri)?.use { input ->
            ExifInterface(input).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        } ?: ExifInterface.ORIENTATION_NORMAL

        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, boundsOpts) }
        val origW = boundsOpts.outWidth
        val origH = boundsOpts.outHeight
        if (origW <= 0 || origH <= 0) {
            throw IllegalStateException("이미지 디코드 실패: $uri")
        }

        val sampleSize = calculateInSampleSize(origW, origH, IMAGE_MAX_DIM * 2)
        val actualOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, actualOpts) }
            ?: throw IllegalStateException("이미지 디코드 실패: $uri")

        val rotated = rotateBitmap(decoded, orientation)
        if (rotated !== decoded) decoded.recycle()

        val rotW = rotated.width
        val rotH = rotated.height
        val scale = if (max(rotW, rotH) > IMAGE_MAX_DIM) IMAGE_MAX_DIM.toFloat() / max(rotW, rotH) else 1f
        val targetW = (rotW * scale).toInt()
        val targetH = (rotH * scale).toInt()
        val resized = if (scale < 1f) {
            Bitmap.createScaledBitmap(rotated, targetW, targetH, true).also {
                if (it !== rotated) rotated.recycle()
            }
        } else {
            rotated
        }

        val baos = ByteArrayOutputStream()
        resized.compress(Bitmap.CompressFormat.JPEG, IMAGE_JPEG_QUALITY, baos)
        val bytes = baos.toByteArray()
        val finalW = resized.width
        val finalH = resized.height
        if (resized !== rotated) resized.recycle()

        val path = "provinces/$provinceId/cities/$cityId/offices/$officeId/chat_images/$messageId.jpg"
        val ref = storage.reference.child(path)
        val downloadUrl = withTimeoutOrNull(IMAGE_UPLOAD_TIMEOUT_MS) {
            ref.putBytes(bytes).await()
            ref.downloadUrl.await().toString()
        } ?: throw IllegalStateException("이미지 업로드 30초 초과: $messageId")

        UploadResult(url = downloadUrl, path = path, width = finalW, height = finalH)
    }

    private fun calculateInSampleSize(w: Int, h: Int, reqMaxDim: Int): Int {
        var inSampleSize = 1
        var halfW = w / 2
        var halfH = h / 2
        while (halfW / inSampleSize >= reqMaxDim || halfH / inSampleSize >= reqMaxDim) {
            inSampleSize *= 2
        }
        return inSampleSize
    }

    private fun rotateBitmap(src: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return src
        }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
    }

    fun sendImageMessage(
        provinceId: String,
        cityId: String,
        officeId: String,
        senderId: String,
        senderName: String,
        senderRole: String,
        uri: Uri,
    ) {
        scope.launch {
            val messageRef = firestore
                .collection("provinces").document(provinceId)
                .collection("cities").document(cityId)
                .collection("offices").document(officeId)
                .collection("chatRoom").document("main")
                .collection("messages").document()
            val messageId = messageRef.id
            val nowMs = System.currentTimeMillis()

            val placeholder = LocalChatMessage(
                id = messageId,
                provinceId = provinceId,
                cityId = cityId,
                officeId = officeId,
                senderId = senderId,
                senderName = senderName,
                senderRole = senderRole,
                text = "",
                createdAt = nowMs,
                clientCreatedAt = nowMs,
                sendStatus = LocalChatMessage.SEND_STATUS_SENDING,
                imageUrl = LocalChatMessage.UPLOADING_SENTINEL,
            )
            chatDao.insert(placeholder)

            try {
                val uploaded = uploadChatImage(provinceId, cityId, officeId, messageId, uri)
                val firestoreData = mapOf(
                    "id" to messageId,
                    "senderId" to senderId,
                    "senderName" to senderName,
                    "senderRole" to senderRole,
                    "imageUrl" to uploaded.url,
                    "imagePath" to uploaded.path,
                    "imageWidth" to uploaded.width,
                    "imageHeight" to uploaded.height,
                    "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                    "clientCreatedAt" to nowMs,
                    "status" to "SENT",
                )
                messageRef.set(firestoreData).await()

                val serverDoc = messageRef.get().await()
                val serverCreatedAt = serverDoc.getTimestamp("createdAt")?.toDate()?.time ?: nowMs
                chatDao.markImageSent(
                    id = messageId,
                    imageUrl = uploaded.url,
                    imagePath = uploaded.path,
                    imageWidth = uploaded.width,
                    imageHeight = uploaded.height,
                    createdAt = serverCreatedAt,
                )
                Log.d(TAG, "[sendImageMessage] 발송 성공: $messageId (${uploaded.width}x${uploaded.height})")
            } catch (e: Exception) {
                Log.e(TAG, "[sendImageMessage] 발송 실패: $messageId", e)
                try {
                    val path = "provinces/$provinceId/cities/$cityId/offices/$officeId/chat_images/$messageId.jpg"
                    storage.reference.child(path).delete().await()
                } catch (_: Exception) { /* 업로드 실패 시 파일 없음 */ }
                chatDao.updateSendStatus(messageId, LocalChatMessage.SEND_STATUS_FAILED)
            }
        }
    }
}
