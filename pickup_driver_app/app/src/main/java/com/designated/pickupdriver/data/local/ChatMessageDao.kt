package com.designated.pickupdriver.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * 채팅 메시지 DAO
 * 사무실 단톡방 (V1)
 */
@Dao
interface ChatMessageDao {

    /**
     * 특정 사무실의 채팅 메시지를 시간 내림차순(최근→과거)으로 Flow 노출.
     * LazyColumn(reverseLayout=true) + index=0=최신 가정에 정합.
     */
    @Query(
        """
        SELECT * FROM chat_messages
        WHERE provinceId = :provinceId AND cityId = :cityId AND officeId = :officeId
        ORDER BY createdAt DESC, clientCreatedAt DESC
        """
    )
    fun getMessagesFlow(provinceId: String, cityId: String, officeId: String): Flow<List<LocalChatMessage>>

    /**
     * 가장 최근 메시지 1건 (BottomSheet peek 미리보기용)
     */
    @Query(
        """
        SELECT * FROM chat_messages
        WHERE provinceId = :provinceId AND cityId = :cityId AND officeId = :officeId
        ORDER BY createdAt DESC, clientCreatedAt DESC
        LIMIT 1
        """
    )
    fun getLatestMessageFlow(provinceId: String, cityId: String, officeId: String): Flow<LocalChatMessage?>

    /**
     * 가장 최근 메시지의 createdAt (페이지네이션 reconciliation에 사용)
     */
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

    /**
     * 메시지 본문 + 서버 시간 갱신 (Firestore set 성공 시 호출)
     */
    @Query("UPDATE chat_messages SET createdAt = :createdAt, sendStatus = :status WHERE id = :id")
    suspend fun markSent(id: String, createdAt: Long, status: String = LocalChatMessage.SEND_STATUS_SENT)

    @Query("DELETE FROM chat_messages WHERE id = :id")
    suspend fun delete(id: String)

    /**
     * 특정 사무실의 모든 메시지 삭제 (로그아웃/사무실 변경 시)
     */
    @Query("DELETE FROM chat_messages WHERE provinceId = :provinceId AND cityId = :cityId AND officeId = :officeId")
    suspend fun deleteAllInOffice(provinceId: String, cityId: String, officeId: String)
}
