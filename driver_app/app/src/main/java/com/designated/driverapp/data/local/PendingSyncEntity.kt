package com.designated.driverapp.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.designated.driverapp.data.settlement.CallSettlement
import com.designated.driverapp.data.settlement.SyncStatus
import com.google.firebase.Timestamp
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 오프라인 동기화 대기 데이터 Entity
 * 네트워크 연결이 없을 때 로컬에 저장하고, 연결 시 Firestore에 동기화
 */
@Entity(tableName = "pending_syncs")
data class PendingSyncEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // 콜 정산 정보 (JSON으로 직렬화)
    val callSettlementJson: String,

    // 세션 날짜 (YYYY-MM-DD)
    val sessionDate: String,

    // 사무실 정보
    val provinceId: String,
    val cityId: String,
    val officeId: String,

    // 동기화 상태
    val status: String = SyncStatus.PENDING.name,

    // 재시도 횟수
    val retryCount: Int = 0,

    // 생성 시간
    val createdAt: Long = System.currentTimeMillis(),

    // 마지막 시도 시간
    val lastAttemptAt: Long? = null,

    // 에러 메시지
    val errorMessage: String? = null
) {
    companion object {
        private val gson = Gson()

        fun fromCallSettlement(
            callSettlement: CallSettlement,
            sessionDate: String,
            provinceId: String,
            cityId: String,
            officeId: String
        ): PendingSyncEntity {
            return PendingSyncEntity(
                callSettlementJson = gson.toJson(callSettlement),
                sessionDate = sessionDate,
                provinceId = provinceId,
                cityId = cityId,
                officeId = officeId
            )
        }
    }

    fun toCallSettlement(): CallSettlement? {
        return try {
            gson.fromJson(callSettlementJson, CallSettlement::class.java)
        } catch (e: Exception) {
            null
        }
    }

    fun getSyncStatus(): SyncStatus {
        return try {
            SyncStatus.valueOf(status)
        } catch (e: Exception) {
            SyncStatus.PENDING
        }
    }
}
