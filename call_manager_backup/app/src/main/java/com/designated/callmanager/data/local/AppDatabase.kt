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
        LocalPointsInfo::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun pointTransactionDao(): PointTransactionDao
    abstract fun pointsInfoDao(): PointsInfoDao

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
                .addMigrations(MIGRATION_1_2) // 향후 스키마 변경 시 사용
                .fallbackToDestructiveMigration() // 개발 단계에서는 데이터 손실 허용
                .build()
                INSTANCE = instance
                instance
            }
        }

        /**
         * 향후 스키마 변경 시 사용할 마이그레이션 예시
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 스키마 변경 시 마이그레이션 로직 작성
                // 예: database.execSQL("ALTER TABLE point_transactions ADD COLUMN new_field TEXT")
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