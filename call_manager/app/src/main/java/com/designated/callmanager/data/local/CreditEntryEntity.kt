package com.designated.callmanager.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "credit_entries",
    foreignKeys = [
        ForeignKey(
            entity = CreditPersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["personId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["personId"])
    ]
)
data class CreditEntryEntity(
    @PrimaryKey val id: String,
    val personId: String,
    val customerName: String,
    val driverName: String,
    val date: String,
    val departure: String,
    val destination: String,
    val paymentMethod: String = "외상",
    val amount: Int,
    val createdAt: Long = System.currentTimeMillis()
)