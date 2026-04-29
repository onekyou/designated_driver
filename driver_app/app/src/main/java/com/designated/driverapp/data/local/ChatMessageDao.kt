package com.designated.driverapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {

    @Query(
        """
        SELECT * FROM chat_messages
        WHERE provinceId = :provinceId AND cityId = :cityId AND officeId = :officeId
        ORDER BY createdAt DESC, clientCreatedAt DESC
        """
    )
    fun getMessagesFlow(provinceId: String, cityId: String, officeId: String): Flow<List<LocalChatMessage>>

    @Query(
        """
        SELECT * FROM chat_messages
        WHERE provinceId = :provinceId AND cityId = :cityId AND officeId = :officeId
        ORDER BY createdAt DESC, clientCreatedAt DESC
        LIMIT 1
        """
    )
    fun getLatestMessageFlow(provinceId: String, cityId: String, officeId: String): Flow<LocalChatMessage?>

    @Query(
        """
        SELECT MAX(createdAt) FROM chat_messages
        WHERE provinceId = :provinceId AND cityId = :cityId AND officeId = :officeId
        """
    )
    suspend fun getMaxCreatedAt(provinceId: String, cityId: String, officeId: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: LocalChatMessage)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<LocalChatMessage>)

    @Query("UPDATE chat_messages SET sendStatus = :status WHERE id = :id")
    suspend fun updateSendStatus(id: String, status: String)

    @Query("UPDATE chat_messages SET createdAt = :createdAt, sendStatus = :status WHERE id = :id")
    suspend fun markSent(id: String, createdAt: Long, status: String = LocalChatMessage.SEND_STATUS_SENT)

    /**
     * 이미지 메시지 업로드 완료 시 imageUrl/imagePath/dimensions + 서버 시간 갱신
     */
    @Query(
        """
        UPDATE chat_messages
        SET imageUrl = :imageUrl, imagePath = :imagePath,
            imageWidth = :imageWidth, imageHeight = :imageHeight,
            createdAt = :createdAt, sendStatus = :status
        WHERE id = :id
        """
    )
    suspend fun markImageSent(
        id: String,
        imageUrl: String,
        imagePath: String,
        imageWidth: Int,
        imageHeight: Int,
        createdAt: Long,
        status: String = LocalChatMessage.SEND_STATUS_SENT,
    )

    /**
     * Local-first 강화: Room empty 여부 가드 (driver_app은 destructive migration 후 자동 복구용)
     */
    @Query(
        """
        SELECT COUNT(*) FROM chat_messages
        WHERE provinceId = :provinceId AND cityId = :cityId AND officeId = :officeId
        """
    )
    suspend fun countInOffice(provinceId: String, cityId: String, officeId: String): Int

    @Query("DELETE FROM chat_messages WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM chat_messages WHERE provinceId = :provinceId AND cityId = :cityId AND officeId = :officeId")
    suspend fun deleteAllInOffice(provinceId: String, cityId: String, officeId: String)
}
