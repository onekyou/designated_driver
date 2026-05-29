package com.designated.callmanager.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        SettlementEntity::class,
        SessionEntity::class
    ],
    version = 8,
    exportSchema = false
)
abstract class CallManagerDatabase : RoomDatabase() {
    abstract fun settlementDao(): SettlementDao
    abstract fun sessionDao(): SessionDao

    companion object {
        @Volatile
        private var INSTANCE: CallManagerDatabase? = null

        fun getInstance(context: Context): CallManagerDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    CallManagerDatabase::class.java,
                    "callmanager.db"
                )
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
        }
    }
}