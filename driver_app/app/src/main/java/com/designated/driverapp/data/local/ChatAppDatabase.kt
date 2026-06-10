package com.designated.driverapp.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 사무실 단톡방 V1 전용 Room DB (driver_app)
 *
 * 기존 SettlementDatabase와 별개 — 동일 앱에서 두 개의 Room DB를 운용.
 * 단순 분리(테이블 1개)이므로 oversized abstract X.
 */
@Database(
    entities = [LocalChatMessage::class],
    version = 4,
    exportSchema = false
)
abstract class ChatAppDatabase : RoomDatabase() {

    abstract fun chatMessageDao(): ChatMessageDao

    companion object {
        private const val DATABASE_NAME = "driver_app_chat_db"

        @Volatile
        private var INSTANCE: ChatAppDatabase? = null

        /**
         * v2 → v3: chat_messages에 음성 메모 4 컬럼 추가 (PTT 콜드 발화 수신).
         * 명시 migration으로 채팅 캐시 보존(destructive 회피). audioAutoplay는 NOT NULL DEFAULT 0.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE chat_messages ADD COLUMN audioUrl TEXT")
                database.execSQL("ALTER TABLE chat_messages ADD COLUMN audioPath TEXT")
                database.execSQL("ALTER TABLE chat_messages ADD COLUMN audioDurationMs INTEGER")
                database.execSQL("ALTER TABLE chat_messages ADD COLUMN audioAutoplay INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * v3 → v4: 블랙박스 9-B 시스템 이벤트 메시지 type 컬럼 추가.
         * type NOT NULL DEFAULT '' ("system" = 시스템 이벤트, "" = 일반 메시지)
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE chat_messages ADD COLUMN type TEXT NOT NULL DEFAULT ''")
            }
        }

        fun getDatabase(context: Context): ChatAppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ChatAppDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
