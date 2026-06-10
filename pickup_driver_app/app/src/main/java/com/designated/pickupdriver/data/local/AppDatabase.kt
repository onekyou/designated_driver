package com.designated.pickupdriver.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * pickup_driver_app 로컬 Room 데이터베이스
 *
 * - v1: chat_messages 테이블 (사무실 단톡방 V1)
 * - v2: calls 테이블 추가 (Dashboard FCM+Room 전환)
 * - v3: chat_messages에 imageUrl/imagePath/imageWidth/imageHeight 추가 (이미지 메시지 V1.1, destructive)
 * - v4: chat_messages에 audio 4컬럼 추가 (PTT 콜드 음성메모, 무손실 migration)
 */
@Database(
    entities = [LocalChatMessage::class, LocalCall::class],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun chatMessageDao(): ChatMessageDao

    abstract fun callDao(): CallDao

    companion object {
        private const val DATABASE_NAME = "pickup_driver_db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * v3 → v4: chat_messages에 음성 메모 4 컬럼 추가 (PTT 콜드 발화 송수신).
         * 명시 migration으로 채팅+콜 캐시 보존(destructive 회피). audioAutoplay는 NOT NULL DEFAULT 0.
         * calls 테이블은 무변경(ALTER 대상 아님).
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE chat_messages ADD COLUMN audioUrl TEXT")
                database.execSQL("ALTER TABLE chat_messages ADD COLUMN audioPath TEXT")
                database.execSQL("ALTER TABLE chat_messages ADD COLUMN audioDurationMs INTEGER")
                database.execSQL("ALTER TABLE chat_messages ADD COLUMN audioAutoplay INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * v4 → v5: 블랙박스 9-B 시스템 이벤트 메시지 type 컬럼 추가.
         * type NOT NULL DEFAULT '' ("system" = 시스템 이벤트, "" = 일반 메시지). calls 테이블 무변경.
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE chat_messages ADD COLUMN type TEXT NOT NULL DEFAULT ''")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
