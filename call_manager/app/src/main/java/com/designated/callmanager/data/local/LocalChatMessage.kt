package com.designated.callmanager.data.local

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

    val text: String,                  // 메시지 본문 (1~2000자, image-only 시 빈 문자열 "")
    val createdAt: Long,               // 서버 시간 (epoch ms). FCM 시점에 알 수 있음
    val clientCreatedAt: Long,         // 클라이언트 시간 (정렬 보조)

    @ColumnInfo(defaultValue = "SENT")
    val sendStatus: String = SEND_STATUS_SENT,  // SENDING / SENT / FAILED (로컬 한정)

    // 이미지 메시지 (V1.1) — text-only 메시지는 모두 null
    val imageUrl: String? = null,      // Storage downloadUrl ("uploading://" sentinel은 업로드 중)
    val imagePath: String? = null,     // Storage path (cleanup 안전성, URL 파싱 회피)
    val imageWidth: Int? = null,       // 압축 후 width (px)
    val imageHeight: Int? = null,      // 압축 후 height (px)

    // 음성 메모 메시지 (PTT 콜드 발화) — 그 외 메시지는 모두 null
    val audioUrl: String? = null,      // Storage downloadUrl ("uploading://" sentinel은 업로드 중)
    val audioPath: String? = null,     // Storage path (cleanup 안전성)
    val audioDurationMs: Long? = null, // 녹음 길이 (ms, UI 표시)

    @ColumnInfo(defaultValue = "0")
    val audioAutoplay: Boolean = false, // 수신측 자동재생 여부 (PTT 콜드=true)
) {
    companion object {
        const val SEND_STATUS_SENDING = "SENDING"
        const val SEND_STATUS_SENT = "SENT"
        const val SEND_STATUS_FAILED = "FAILED"
        const val UPLOADING_SENTINEL = "uploading://"
    }
}
