package com.designated.driverapp.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room Database용 채팅 메시지 Entity (사무실 단톡방 V1)
 * Firestore와 1:1 미러링. FCM + 로컬 트리거 패턴.
 * 관련 스펙: docs/chat-shared-spec.md
 */
@Entity(
    tableName = "chat_messages",
    indices = [
        Index(
            value = ["provinceId", "cityId", "officeId", "createdAt"],
            name = "idx_chat_messages_office_time",
        ),
    ],
)
data class LocalChatMessage(
    @PrimaryKey
    val id: String,

    val provinceId: String,
    val cityId: String,
    val officeId: String,

    val senderId: String,
    val senderName: String,
    val senderRole: String,

    val text: String,                  // image-only 시 빈 문자열 ""
    val createdAt: Long,
    val clientCreatedAt: Long,

    @ColumnInfo(defaultValue = "SENT")
    val sendStatus: String = SEND_STATUS_SENT,

    // 이미지 메시지 (V1.1)
    val imageUrl: String? = null,      // Storage downloadUrl ("uploading://" sentinel은 업로드 중)
    val imagePath: String? = null,     // Storage path (cleanup 안전성)
    val imageWidth: Int? = null,
    val imageHeight: Int? = null,

    // 음성 메모 메시지 (PTT 콜드 발화)
    val audioUrl: String? = null,
    val audioPath: String? = null,
    val audioDurationMs: Long? = null,

    @ColumnInfo(defaultValue = "0")
    val audioAutoplay: Boolean = false,
) {
    companion object {
        const val SEND_STATUS_SENDING = "SENDING"
        const val SEND_STATUS_SENT = "SENT"
        const val SEND_STATUS_FAILED = "FAILED"
        const val UPLOADING_SENTINEL = "uploading://"
    }
}
