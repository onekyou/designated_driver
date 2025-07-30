package com.designated.callmanager.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "credit_persons")
data class CreditPersonEntity(
    @PrimaryKey val id: String,
    val name: String,
    val phone: String,
    val memo: String = "",
    val totalAmount: Int = 0,
    val lastUpdated: Long = System.currentTimeMillis()
)