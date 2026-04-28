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

    @Query("DELETE FROM chat_messages WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM chat_messages WHERE provinceId = :provinceId AND cityId = :cityId AND officeId = :officeId")
    suspend fun deleteAllInOffice(provinceId: String, cityId: String, officeId: String)
}
