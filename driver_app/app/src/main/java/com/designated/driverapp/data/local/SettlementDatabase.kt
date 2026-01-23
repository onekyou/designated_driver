package com.designated.driverapp.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * 정산 데이터 Room Database
 */
@Database(
    entities = [
        PendingSyncEntity::class,
        SettlementCacheEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class SettlementDatabase : RoomDatabase() {

    abstract fun settlementDao(): SettlementDao

    companion object {
        private const val DATABASE_NAME = "settlement_database"

        @Volatile
        private var INSTANCE: SettlementDatabase? = null

        fun getInstance(context: Context): SettlementDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SettlementDatabase::class.java,
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
