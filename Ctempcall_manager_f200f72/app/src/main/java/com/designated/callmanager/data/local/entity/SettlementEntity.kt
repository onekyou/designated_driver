package com.designated.callmanager.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "settlement_cache")
data class SettlementEntity(
    @PrimaryKey
    val callId: String,
    val status: String, // WAITING, COMPLETED
    val timestamp: Long = System.currentTimeMillis()
)