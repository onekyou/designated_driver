package com.designated.driverapp.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.designated.driverapp.data.local.ChatAppDatabase
import com.designated.driverapp.data.local.LocalChatMessage
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
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
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

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
    @ApplicationContext private val context: Context,
    private val database: ChatAppDatabase,
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

        // 이미지 압축 사양 — 긴 변 max 640px, JPEG 60%
        private const val IMAGE_MAX_DIM = 640
        private const val IMAGE_JPEG_QUALITY = 60
        private const val IMAGE_UPLOAD_TIMEOUT_MS = 30_000L
    }

    /** 이미지 업로드 결과 — Firestore에 저장할 메타데이터. */
    data class UploadResult(
        val url: String,
        val path: String,
        val width: Int,
        val height: Int,
    )

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
                val createdAt = payload["createdAt"]?.toLongOrNull() ?: System.currentTimeMillis()
                val clientCreatedAt = payload["clientCreatedAt"]?.toLongOrNull() ?: createdAt
                val provinceId = payload["provinceId"] ?: return@launch
                val cityId = payload["cityId"] ?: return@launch
                val officeId = payload["officeId"] ?: return@launch

                if (text.isBlank() && imageUrl.isNullOrEmpty()) {
                    Log.w(TAG, "[onRemoteMessageReceived] text/imageUrl 둘 다 없음 — skip ($messageId)")
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
                    )
                )
                Log.d(TAG, "[onRemoteMessageReceived] INSERT: $messageId" +
                    if (imageUrl != null) " (image)" else "")
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
                val text = doc.getString("text") ?: ""
                val imageUrl = doc.getString("imageUrl")?.takeIf { it.isNotEmpty() }
                val imagePath = doc.getString("imagePath")?.takeIf { it.isNotEmpty() }
                val imageWidth = doc.getLong("imageWidth")?.toInt()
                val imageHeight = doc.getLong("imageHeight")?.toInt()
                val createdAt = (doc.get("createdAt") as? Timestamp)?.toDate()?.time
                    ?: doc.getLong("clientCreatedAt") ?: return@mapNotNull null
                val clientCreatedAt = doc.getLong("clientCreatedAt") ?: createdAt

                if (text.isBlank() && imageUrl.isNullOrEmpty()) return@mapNotNull null

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

    // ===== Local-first 가드 =====

    /**
     * Room이 비어있는지 확인 — destructive migration 후 또는 첫 설치 시 자동 복구용.
     */
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

        // 1. EXIF orientation
        val orientation = resolver.openInputStream(uri)?.use { input ->
            ExifInterface(input).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        } ?: ExifInterface.ORIENTATION_NORMAL

        // 2. boundsOnly 디코드
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, boundsOpts) }
        val origW = boundsOpts.outWidth
        val origH = boundsOpts.outHeight
        if (origW <= 0 || origH <= 0) {
            throw IllegalStateException("이미지 디코드 실패: $uri")
        }

        // 3. inSampleSize
        val sampleSize = calculateInSampleSize(origW, origH, IMAGE_MAX_DIM * 2)
        val actualOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, actualOpts) }
            ?: throw IllegalStateException("이미지 디코드 실패: $uri")

        // 4. EXIF 회전
        val rotated = rotateBitmap(decoded, orientation)
        if (rotated !== decoded) decoded.recycle()

        // 5. 긴 변 max 640 비율 유지
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

        // 6. JPEG 60% 압축
        val baos = ByteArrayOutputStream()
        resized.compress(Bitmap.CompressFormat.JPEG, IMAGE_JPEG_QUALITY, baos)
        val bytes = baos.toByteArray()
        val finalW = resized.width
        val finalH = resized.height
        if (resized !== rotated) resized.recycle()

        // 7. Storage 업로드 (30초 타임아웃)
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

    /**
     * 이미지 메시지 전송. text 빈 문자열, imageUrl/imagePath/dimensions 포함.
     */
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
