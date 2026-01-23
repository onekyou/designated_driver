package com.designated.driverapp.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.designated.driverapp.data.settlement.CallSettlement
import com.designated.driverapp.data.settlement.SettlementMetadata
import com.designated.driverapp.data.settlement.SettlementSession
import com.designated.driverapp.data.settlement.SettlementTotals
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 정산 세션 로컬 캐시 Entity
 * Firestore 공유 문서의 로컬 복사본
 */
@Entity(tableName = "settlement_cache")
data class SettlementCacheEntity(
    @PrimaryKey
    val sessionDate: String,  // YYYY-MM-DD (Primary Key)

    // 사무실 정보
    val provinceId: String,
    val cityId: String,
    val officeId: String,

    // 버전 (Firestore 문서 버전과 동기화)
    val version: Long = 0,

    // 메타데이터 JSON
    val metadataJson: String,

    // 집계 데이터 JSON
    val totalsJson: String,

    // 콜 목록 JSON
    val callsJson: String,

    // 로컬 캐시 시간
    val cachedAt: Long = System.currentTimeMillis()
) {
    companion object {
        private val gson = Gson()

        fun fromSettlementSession(
            session: SettlementSession,
            sessionDate: String,
            provinceId: String,
            cityId: String,
            officeId: String
        ): SettlementCacheEntity {
            return SettlementCacheEntity(
                sessionDate = sessionDate,
                provinceId = provinceId,
                cityId = cityId,
                officeId = officeId,
                version = session.metadata.version,
                metadataJson = gson.toJson(session.metadata.toMap()),
                totalsJson = gson.toJson(session.totals.toMap()),
                callsJson = gson.toJson(session.calls.map { it.toMap() })
            )
        }
    }

    fun toSettlementSession(): SettlementSession? {
        return try {
            val metadataMap: Map<String, Any?> = gson.fromJson(
                metadataJson,
                object : TypeToken<Map<String, Any?>>() {}.type
            )
            val totalsMap: Map<String, Any?> = gson.fromJson(
                totalsJson,
                object : TypeToken<Map<String, Any?>>() {}.type
            )
            val callsList: List<Map<String, Any?>> = gson.fromJson(
                callsJson,
                object : TypeToken<List<Map<String, Any?>>>() {}.type
            )

            SettlementSession(
                metadata = SettlementMetadata.fromMap(metadataMap),
                totals = SettlementTotals.fromMap(totalsMap),
                calls = callsList.mapNotNull { CallSettlement.fromMap(it) }
            )
        } catch (e: Exception) {
            null
        }
    }
}
