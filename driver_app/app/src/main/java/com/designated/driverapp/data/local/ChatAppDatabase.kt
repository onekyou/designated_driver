package com.designated.driverapp.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * 사무실 단톡방 V1 전용 Room DB (driver_app)
 *
 * 기존 SettlementDatabase와 별개 — 동일 앱에서 두 개의 Room DB를 운용.
 * 단순 분리(테이블 1개)이므로 oversized abstract X.
 */
@Database(
    entities = [LocalChatMessage::class],
    version = 2,
    exportSchema = false
)
abstract class ChatAppDatabase : RoomDatabase() {

    abstract fun chatMessageDao(): ChatMessageDao

    companion object {
        private const val DATABASE_NAME = "driver_app_chat_db"

        @Volatile
        private var INSTANCE: ChatAppDatabase? = null

        fun getDatabase(context: Context): ChatAppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ChatAppDatabase::class.java,
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
