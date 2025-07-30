package com.designated.callmanager.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "credit_cache")
data class CreditEntity(
    @PrimaryKey
    val id: String, // "driverId_customerPhone" 형식
    val driverId: String,
    val driverName: String,
    val customerPhone: String,
    val customerName: String,
    val amount: Int,
    val isPaid: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)