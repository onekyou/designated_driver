package com.designated.callmanager.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.designated.callmanager.data.SessionInfo

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val sessionId: String,
    val endAt: Long,
    val totalTrips: Int,
    val totalFare: Long
) {
    fun toInfo() = SessionInfo(sessionId, null, totalFare, totalTrips)

    companion object {
        fun from(totalTrips: Int, totalFare: Long): SessionEntity {
            val id = System.currentTimeMillis().toString()
            return SessionEntity(id, System.currentTimeMillis(), totalTrips, totalFare)
        }
    }
} 