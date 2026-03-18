package com.designated.customer.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.designated.customer.data.dao.StepDao
import com.designated.customer.data.model.DailyStepData
import com.designated.customer.data.model.WeeklyStepSummary
import com.designated.customer.data.model.MonthlyStepSummary

/**
 * 걸음 수 데이터 저장을 위한 Room Database
 */
@Database(
    entities = [
        DailyStepData::class,
        WeeklyStepSummary::class,
        MonthlyStepSummary::class
    ],
    version = 1,
    exportSchema = false
)
abstract class StepDatabase : RoomDatabase() {

    abstract fun stepDao(): StepDao

    companion object {
        @Volatile
        private var INSTANCE: StepDatabase? = null

        /**
         * Singleton 패턴으로 Database 인스턴스 제공
         */
        fun getInstance(context: Context): StepDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    StepDatabase::class.java,
                    "step_database"
                )
                    .fallbackToDestructiveMigration() // 버전 업그레이드 시 데이터 초기화
                    .build()
                INSTANCE = instance
                instance
            }
        }

    }
}
