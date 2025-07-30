package com.designated.callmanager.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.designated.callmanager.data.local.dao.SettlementDao
import com.designated.callmanager.data.local.entity.SettlementEntity
import com.designated.callmanager.data.local.entity.CreditEntity

@Database(
    entities = [SettlementEntity::class, CreditEntity::class],
    version = 1,
    exportSchema = false
)
abstract class SettlementDatabase : RoomDatabase() {
    abstract fun settlementDao(): SettlementDao
    
    companion object {
        @Volatile
        private var INSTANCE: SettlementDatabase? = null
        
        fun getDatabase(context: Context): SettlementDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SettlementDatabase::class.java,
                    "settlement_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}