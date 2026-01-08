package com.designated.callmanager.data.local

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.content.Context

/**
 * 콜매니저 앱의 로컬 Room 데이터베이스
 * 포인트, 콜, 기사 정보를 로컬에 저장하여 Firebase 읽기 비용 절감
 */
@Database(
    entities = [
        LocalPointTransaction::class,
        LocalPointsInfo::class,
        LocalCallInfo::class,
        LocalDriverInfo::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun pointTransactionDao(): PointTransactionDao
    abstract fun pointsInfoDao(): PointsInfoDao
    abstract fun callDao(): CallDao
    abstract fun driverDao(): DriverDao

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
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration() // 개발 단계에서는 데이터 손실 허용
                .build()
                INSTANCE = instance
                instance
            }
        }

        /**
         * 버전 1 → 2 마이그레이션: 콜, 기사 테이블 추가
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // calls 테이블 생성
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS calls (
                        id TEXT PRIMARY KEY NOT NULL,
                        phoneNumber TEXT NOT NULL,
                        customerAddress TEXT,
                        customerName TEXT,
                        address TEXT,
                        status TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        detectedTimestamp INTEGER,
                        timestampClient INTEGER,
                        assignedDriverId TEXT,
                        assignedDriverName TEXT,
                        assignedDriverPhone TEXT,
                        assignedTimestamp INTEGER,
                        departure TEXT,
                        destination TEXT,
                        departure_set TEXT,
                        destination_set TEXT,
                        waypoints_set TEXT,
                        fare INTEGER,
                        fare_set INTEGER,
                        cashAmount INTEGER,
                        paymentMethod TEXT,
                        callType TEXT,
                        deviceName TEXT,
                        trip_summary TEXT,
                        cancelReason TEXT,
                        isSummaryConfirmed INTEGER,
                        summaryConfirmedTimestamp INTEGER,
                        regionId TEXT,
                        officeId TEXT,
                        fromCallDetector INTEGER,
                        fromCallManager INTEGER,
                        customerId TEXT,
                        customerGrade TEXT,
                        createdFrom TEXT,
                        pointsEarned INTEGER,
                        pointsUsed INTEGER,
                        isAppCustomer INTEGER,
                        attributionScore INTEGER,
                        attributionSource TEXT,
                        synced INTEGER NOT NULL DEFAULT 1,
                        lastUpdated INTEGER NOT NULL
                    )
                """)

                // drivers 테이블 생성
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS drivers (
                        id TEXT PRIMARY KEY NOT NULL,
                        authUid TEXT,
                        name TEXT NOT NULL,
                        phoneNumber TEXT NOT NULL,
                        email TEXT,
                        driverType TEXT,
                        status TEXT NOT NULL,
                        approvalStatus TEXT NOT NULL,
                        createdAt INTEGER,
                        updatedAt INTEGER,
                        approvedAt INTEGER,
                        regionId TEXT,
                        officeId TEXT,
                        referralQrUrl TEXT,
                        synced INTEGER NOT NULL DEFAULT 1,
                        lastUpdated INTEGER NOT NULL
                    )
                """)

                // 인덱스 생성 (성능 최적화)
                database.execSQL("CREATE INDEX IF NOT EXISTS index_calls_office ON calls(regionId, officeId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_calls_status ON calls(status)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_drivers_office ON drivers(regionId, officeId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_drivers_authUid ON drivers(authUid)")
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