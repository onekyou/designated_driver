package com.designated.callmanager.data.local

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.content.Context

/**
 * 콜매니저 앱의 로컬 Room 데이터베이스
 * 포인트 거래 내역과 잔액 정보를 로컬에 저장하여 Firebase 읽기 비용 절감
 */
@Database(
    entities = [
        LocalPointTransaction::class,
        LocalPointsInfo::class,
        LocalCallInfo::class,
        LocalDriverInfo::class,
        LocalChatMessage::class
    ],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun pointTransactionDao(): PointTransactionDao
    abstract fun pointsInfoDao(): PointsInfoDao
    abstract fun callDao(): CallDao
    abstract fun driverDao(): DriverDao
    abstract fun chatMessageDao(): ChatMessageDao

    companion object {
        private const val DATABASE_NAME = "call_manager_db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * 데이터베이스 싱글톤 인스턴스 반환
         */
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .fallbackToDestructiveMigration() // 개발 단계에서는 데이터 손실 허용
                .build()
                INSTANCE = instance
                instance
            }
        }

        /**
         * v1 → v2 마이그레이션: calls와 drivers 테이블 추가
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // calls 테이블 생성
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS calls (
                        id TEXT PRIMARY KEY NOT NULL,
                        phoneNumber TEXT NOT NULL,
                        customerName TEXT,
                        customerAddress TEXT,
                        status TEXT NOT NULL,
                        timestamp INTEGER,
                        departure_set TEXT,
                        destination_set TEXT,
                        waypoints_set TEXT,
                        fare_set INTEGER,
                        assignedDriverId TEXT,
                        assignedDriverAuthUid TEXT,
                        assignedDriverName TEXT,
                        assignedDriverPhone TEXT,
                        callType TEXT,
                        fromCallDetector INTEGER,
                        regionId TEXT NOT NULL,
                        officeId TEXT NOT NULL,
                        synced INTEGER NOT NULL DEFAULT 1,
                        lastUpdated INTEGER NOT NULL
                    )
                """)

                // drivers 테이블 생성
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS drivers (
                        id TEXT PRIMARY KEY NOT NULL,
                        name TEXT NOT NULL,
                        phoneNumber TEXT NOT NULL,
                        authUid TEXT,
                        status TEXT NOT NULL,
                        createdAt INTEGER,
                        updatedAt INTEGER,
                        regionId TEXT NOT NULL,
                        officeId TEXT NOT NULL,
                        synced INTEGER NOT NULL DEFAULT 1,
                        lastUpdated INTEGER NOT NULL
                    )
                """)
            }
        }

        /**
         * v3 → v4 마이그레이션: drivers 테이블에 lastLoginTime 컬럼 추가
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE drivers ADD COLUMN lastLoginTime INTEGER")
            }
        }

        /**
         * v4 → v5 마이그레이션: calls 테이블에 memoText 컬럼 추가 (배차 임시 메모)
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE calls ADD COLUMN memoText TEXT")
            }
        }

        /**
         * v5 → v6 마이그레이션: chat_messages 테이블 추가 (사무실 단톡방 V1)
         */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS chat_messages (
                        id TEXT PRIMARY KEY NOT NULL,
                        provinceId TEXT NOT NULL,
                        cityId TEXT NOT NULL,
                        officeId TEXT NOT NULL,
                        senderId TEXT NOT NULL,
                        senderName TEXT NOT NULL,
                        senderRole TEXT NOT NULL,
                        text TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        clientCreatedAt INTEGER NOT NULL,
                        sendStatus TEXT NOT NULL DEFAULT 'SENT'
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS idx_chat_messages_office_time
                    ON chat_messages (provinceId, cityId, officeId, createdAt)
                    """.trimIndent()
                )
            }
        }

        /**
         * 테스트용 인메모리 데이터베이스 생성
         */
        fun getInMemoryDatabase(context: Context): AppDatabase {
            return Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                AppDatabase::class.java
            ).build()
        }

        /**
         * 데이터베이스 인스턴스 강제 초기화 (테스트용)
         */
        fun destroyInstance() {
            INSTANCE?.close()
            INSTANCE = null
        }
    }
}