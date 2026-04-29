package com.designated.pickupdriver.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * pickup_driver_app 로컬 Room 데이터베이스
 *
 * - v1: chat_messages 테이블 (사무실 단톡방 V1)
 * - v2: calls 테이블 추가 (Dashboard FCM+Room 전환)
 * - v3: chat_messages에 imageUrl/imagePath/imageWidth/imageHeight 추가 (이미지 메시지 V1.1, destructive)
 */
@Database(
    entities = [LocalChatMessage::class, LocalCall::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun chatMessageDao(): ChatMessageDao

    abstract fun callDao(): CallDao

    companion object {
        private const val DATABASE_NAME = "pickup_driver_db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
