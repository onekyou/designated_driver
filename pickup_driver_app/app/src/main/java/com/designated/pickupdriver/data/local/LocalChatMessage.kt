package com.designated.pickupdriver.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room Database용 채팅 메시지 Entity
 *
 * 사무실 단톡방 메시지 (V1).
 * Firestore와 1:1 미러링되며 FCM + 로컬 트리거 패턴으로 동기화.
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
    val id: String,                    // Firestore docId와 동일

    val provinceId: String,
    val cityId: String,
    val officeId: String,

    val senderId: String,              // Firebase Auth UID
    val senderName: String,            // 발신자 표시 이름 (비정규화)
    val senderRole: String,            // MANAGER / DESIGNATED_DRIVER / PICKUP_DRIVER

    val text: String,                  // image-only 시 빈 문자열 ""
    val createdAt: Long,
    val clientCreatedAt: Long,

    @ColumnInfo(defaultValue = "SENT")
    val sendStatus: String = SEND_STATUS_SENT,

    // 이미지 메시지 (V1.1)
    val imageUrl: String? = null,
    val imagePath: String? = null,
    val imageWidth: Int? = null,
    val imageHeight: Int? = null,

    // 음성 메모 (PTT 콜드 발화 — V1.2)
    val audioUrl: String? = null,
    val audioPath: String? = null,
    val audioDurationMs: Long? = null,
    @ColumnInfo(defaultValue = "0")
    val audioAutoplay: Boolean = false,

    // 블랙박스 9-B: 시스템 이벤트 메시지 ("system") — 일반 메시지는 "" (무음 렌더 분기)
    @ColumnInfo(defaultValue = "")
    val type: String = "",
) {
    companion object {
        const val SEND_STATUS_SENDING = "SENDING"
        const val SEND_STATUS_SENT = "SENT"
        const val SEND_STATUS_FAILED = "FAILED"
        const val UPLOADING_SENTINEL = "uploading://"
    }
}
