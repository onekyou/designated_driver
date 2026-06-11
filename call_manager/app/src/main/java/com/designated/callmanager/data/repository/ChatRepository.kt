package com.designated.callmanager.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.designated.callmanager.data.local.AppDatabase
import com.designated.callmanager.data.local.LocalChatMessage
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import kotlin.math.max

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
    private val context: Context,
    private val database: AppDatabase,
    private val firestore: FirebaseFirestore,
    private val scope: CoroutineScope,
) {
    private val chatDao = database.chatMessageDao()
    private val storage by lazy { FirebaseStorage.getInstance() }

    companion object {
        private const val TAG = "ChatRepository"
        private const val INITIAL_LOAD_LIMIT = 50L

        const val ROLE_MANAGER = "MANAGER"
        const val ROLE_DESIGNATED_DRIVER = "DESIGNATED_DRIVER"
        const val ROLE_PICKUP_DRIVER = "PICKUP_DRIVER"

        // 이미지 압축 사양 — 긴 변 max 640px, JPEG 60% (~50~100KB)
        private const val IMAGE_MAX_DIM = 640
        private const val IMAGE_JPEG_QUALITY = 60
        private const val IMAGE_UPLOAD_TIMEOUT_MS = 30_000L

        // 음성 메모 업로드 — 30초 타임아웃, contentType 명시(storage rule audio 타입 통과)
        private const val AUDIO_UPLOAD_TIMEOUT_MS = 30_000L
        private const val AUDIO_CONTENT_TYPE = "audio/mp4"
    }

    /** 이미지 업로드 결과 — Firestore에 저장할 메타데이터. */
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
     * [STT] PTT 라이브 발화 전사 텍스트 → 무음 PTT-텍스트 메시지(type="ptt").
     *  - sendMessage 와 동일하되 type="ptt" 를 Room INSERT + Firestore set 에 포함.
     *  - 렌더: type!="system" 이라 발신자 있는 일반 말풍선(SystemMessageBubble 아님), 필터 토글 통과.
     *  - 무음: onChatMessageCreated 가 chatType="ptt" 전파 → 3앱 FCM 핸들러가 무음 INSERT.
     *  - rules: text(1~2000) + type!="system" + senderRole!="SYSTEM" → 통과(클라 write 허용).
     */
    fun sendPttText(
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
            Log.w(TAG, "[sendPttText] 텍스트 길이 검증 실패: ${cleanText.length}")
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
                type = "ptt",
            )

            // 1) Optimistic INSERT
            chatDao.insert(local)

            // 2) Firestore set (type="ptt")
            try {
                val firestoreData = mapOf(
                    "id" to messageId,
                    "senderId" to senderId,
                    "senderName" to senderName,
                    "senderRole" to senderRole,
                    "text" to cleanText,
                    "type" to "ptt",
                    "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                    "clientCreatedAt" to nowMs,
                    "status" to "SENT",
                )
                messageRef.set(firestoreData).await()

                val serverDoc = messageRef.get().await()
                val serverCreatedAt = serverDoc.getTimestamp("createdAt")?.toDate()?.time ?: nowMs
                chatDao.markSent(messageId, serverCreatedAt)
                Log.d(TAG, "[sendPttText] 발송 성공: $messageId")
            } catch (e: Exception) {
                Log.e(TAG, "[sendPttText] 발송 실패: $messageId", e)
                chatDao.updateSendStatus(messageId, LocalChatMessage.SEND_STATUS_FAILED)
            }
        }
    }

    /**
     * 실패 메시지 재시도. ViewModel은 LocalChatMessage 객체를 직접 전달.
     * Phase 1: 이미지 메시지 retry 미지원 — 사용자가 갤러리 다시 선택해서 새 메시지 생성.
     */
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
                val text = payload["text"] ?: ""
                val imageUrl = payload["imageUrl"]?.takeIf { it.isNotEmpty() }
                val imagePath = payload["imagePath"]?.takeIf { it.isNotEmpty() }
                val imageWidth = payload["imageWidth"]?.toIntOrNull()
                val imageHeight = payload["imageHeight"]?.toIntOrNull()
                val audioUrl = payload["audioUrl"]?.takeIf { it.isNotEmpty() }
                val audioPath = payload["audioPath"]?.takeIf { it.isNotEmpty() }
                val audioDurationMs = payload["audioDurationMs"]?.toLongOrNull()
                val audioAutoplay = payload["audioAutoplay"] == "true"
                val type = payload["chatType"] ?: "" // 블랙박스 9-B: "system" = 시스템 이벤트(무음)
                val createdAt = payload["createdAt"]?.toLongOrNull() ?: System.currentTimeMillis()
                val clientCreatedAt = payload["clientCreatedAt"]?.toLongOrNull() ?: createdAt
                val provinceId = payload["provinceId"] ?: return@launch
                val cityId = payload["cityId"] ?: return@launch
                val officeId = payload["officeId"] ?: return@launch

                // text / imageUrl / audioUrl 중 하나는 있어야 함
                if (text.isBlank() && imageUrl.isNullOrEmpty() && audioUrl.isNullOrEmpty()) {
                    Log.w(TAG, "[onRemoteMessageReceived] text/imageUrl/audioUrl 모두 없음 — skip ($messageId)")
                    return@launch
                }

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
                        imageUrl = imageUrl,
                        imagePath = imagePath,
                        imageWidth = imageWidth,
                        imageHeight = imageHeight,
                        audioUrl = audioUrl,
                        audioPath = audioPath,
                        audioDurationMs = audioDurationMs,
                        audioAutoplay = audioAutoplay,
                        type = type,
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
                val text = doc.getString("text") ?: ""
                val imageUrl = doc.getString("imageUrl")?.takeIf { it.isNotEmpty() }
                val imagePath = doc.getString("imagePath")?.takeIf { it.isNotEmpty() }
                val imageWidth = doc.getLong("imageWidth")?.toInt()
                val imageHeight = doc.getLong("imageHeight")?.toInt()
                val audioUrl = doc.getString("audioUrl")?.takeIf { it.isNotEmpty() }
                val audioPath = doc.getString("audioPath")?.takeIf { it.isNotEmpty() }
                val audioDurationMs = doc.getLong("audioDurationMs")
                val audioAutoplay = doc.getBoolean("audioAutoplay") ?: false
                val type = doc.getString("type") ?: "" // 블랙박스 9-B: "system" = 시스템 이벤트
                val createdAt = (doc.get("createdAt") as? Timestamp)?.toDate()?.time
                    ?: doc.getLong("clientCreatedAt") ?: return@mapNotNull null
                val clientCreatedAt = doc.getLong("clientCreatedAt") ?: createdAt

                // text / imageUrl / audioUrl 중 하나는 있어야 함
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
                    type = type,
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

    // ===== Local-first 가드 =====

    /**
     * Room이 비어있는지 확인 — ChatViewModel.init에서 Room empty 시만 loadInitialMessages 호출 (비용 절감).
     * 첫 설치/destructive migration 후에만 true → Firestore fetch 1회.
     */
    suspend fun isEmptyInOffice(provinceId: String, cityId: String, officeId: String): Boolean {
        return chatDao.countInOffice(provinceId, cityId, officeId) == 0
    }

    // ===== 이미지 업로드 (V1.1) =====

    /**
     * 갤러리 Uri → 압축 → Firebase Storage 업로드 → downloadUrl/path/dimensions 반환.
     *
     * 처리 흐름:
     * 1. EXIF orientation 읽기 (회전된 사진 방지 — 빠뜨리면 옆으로 누워 저장됨)
     * 2. inSampleSize 2단계 디코드 (boundsOnly → actual, 50MB+ 사진 OOM 방지)
     * 3. EXIF 회전 적용 (Matrix.postRotate)
     * 4. 긴 변 max 640px 비율 유지 리사이즈 (정사각 강제 X)
     * 5. JPEG 60% 압축 (~50~100KB)
     * 6. Storage path: provinces/$p/cities/$c/offices/$o/chat_images/$messageId.jpg
     * 7. putBytes + downloadUrl, 30초 타임아웃
     *
     * @throws IllegalStateException 이미지 디코드 실패 시
     * @throws kotlinx.coroutines.TimeoutCancellationException 30초 초과 시 (caller가 cleanup)
     */
    suspend fun uploadChatImage(
        provinceId: String,
        cityId: String,
        officeId: String,
        messageId: String,
        uri: Uri,
    ): UploadResult = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver

        // 1. EXIF orientation 읽기 (압축 전)
        val orientation = resolver.openInputStream(uri)?.use { input ->
            ExifInterface(input).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        } ?: ExifInterface.ORIENTATION_NORMAL

        // 2. boundsOnly 디코드로 원본 크기 확인 (메모리 안전)
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, boundsOpts) }
        val origW = boundsOpts.outWidth
        val origH = boundsOpts.outHeight
        if (origW <= 0 || origH <= 0) {
            throw IllegalStateException("이미지 디코드 실패: $uri")
        }

        // 3. inSampleSize 계산 (큰 이미지를 메모리에 안전하게 로드)
        val sampleSize = calculateInSampleSize(origW, origH, IMAGE_MAX_DIM * 2)
        val actualOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, actualOpts) }
            ?: throw IllegalStateException("이미지 디코드 실패: $uri")

        // 4. EXIF orientation 적용 (Matrix 회전)
        val rotated = rotateBitmap(decoded, orientation)
        if (rotated !== decoded) decoded.recycle()

        // 5. 긴 변 max 640 비율 유지 리사이즈
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

        UploadResult(
            url = downloadUrl,
            path = path,
            width = finalW,
            height = finalH,
        )
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
     * 이미지 메시지 전송. text는 빈 문자열.
     *
     * 흐름:
     * 1. messageId 사전 발급
     * 2. 로컬 INSERT (SENDING + imageUrl="uploading://" sentinel) — UI 즉시 placeholder 표시
     * 3. uploadChatImage (EXIF + 압축 + Storage 업로드, 30초 타임아웃)
     * 4. Firestore set (text 필드 omit, imageUrl/imagePath/dimensions 포함)
     * 5. markImageSent (로컬 update — 실제 URL/path/dimensions/서버 시간)
     *
     * 실패 시: Storage cleanup (best-effort) + FAILED 마킹.
     * Phase 1: retry 없음 — 사용자가 갤러리 재선택해서 새 메시지 생성.
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
                imageUrl = LocalChatMessage.UPLOADING_SENTINEL,
            )
            chatDao.insert(placeholder)

            // 2) 업로드 + Firestore set
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
                    // text 필드 omit — image-only 메시지
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
                // Storage cleanup (best-effort)
                try {
                    val path = "provinces/$provinceId/cities/$cityId/offices/$officeId/chat_images/$messageId.jpg"
                    storage.reference.child(path).delete().await()
                } catch (_: Exception) { /* 업로드 자체 실패 시 파일 없음 */ }
                chatDao.updateSendStatus(messageId, LocalChatMessage.SEND_STATUS_FAILED)
            }
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

    // ===== 정리 =====

    suspend fun clearAllMessagesInOffice(provinceId: String, cityId: String, officeId: String) {
        chatDao.deleteAllInOffice(provinceId, cityId, officeId)
    }
}
