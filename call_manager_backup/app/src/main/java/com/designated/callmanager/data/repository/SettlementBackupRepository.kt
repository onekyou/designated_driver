package com.designated.callmanager.data.repository

import android.content.Context
import android.os.Build
import android.util.Log
// BuildConfig import 제거
import com.designated.callmanager.data.SettlementBackup
import com.designated.callmanager.data.SettlementBackupItem
import com.designated.callmanager.data.SettlementBackupMetadata
import com.designated.callmanager.data.SettlementData
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

/**
 * 정산 데이터 백업/복원을 위한 Repository
 * Firebase Realtime Database 사용으로 비용 최적화
 */
class SettlementBackupRepository(
    private val context: Context
) {
    companion object {
        private const val TAG = "SettlementBackupRepo"
        private const val BACKUP_ROOT = "settlement_backups"
        private const val MAX_BACKUP_COUNT = 30 // 최대 30개 백업 보관
    }

    private val database = FirebaseDatabase.getInstance()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    /**
     * 정산 데이터 백업
     */
    suspend fun backupSettlements(
        regionId: String,
        officeId: String,
        settlements: List<SettlementData>
    ): Result<String> {
        return try {
            if (settlements.isEmpty()) {
                return Result.failure(Exception("백업할 정산 데이터가 없습니다"))
            }

            val backupDate = dateFormat.format(Date())
            val backupId = "${regionId}_${officeId}_${System.currentTimeMillis()}"

            // 백업 데이터 생성
            val backupItems = settlements.map { SettlementBackupItem.fromSettlementData(it) }
            val metadata = createBackupMetadata(settlements)

            val backup = SettlementBackup(
                backupId = backupId,
                regionId = regionId,
                officeId = officeId,
                backupDate = backupDate,
                createdAt = System.currentTimeMillis(),
                settlements = backupItems,
                metadata = metadata
            )

            // Firebase Realtime Database에 저장
            val backupRef = database.getReference(BACKUP_ROOT)
                .child(regionId)
                .child(officeId)
                .child(backupId)

            backupRef.setValue(backup).await()

            // 오래된 백업 정리
            cleanupOldBackups(regionId, officeId)

            Log.d(TAG, "정산 데이터 백업 완료: ${settlements.size}건, ID: $backupId")
            Result.success(backupId)

        } catch (e: Exception) {
            Log.e(TAG, "정산 데이터 백업 실패", e)
            Result.failure(e)
        }
    }

    /**
     * 정산 데이터 복원
     */
    suspend fun restoreSettlements(
        regionId: String,
        officeId: String,
        backupId: String? = null
    ): Result<List<SettlementData>> {
        return try {
            val targetBackupId = backupId ?: getLatestBackupId(regionId, officeId)
            if (targetBackupId == null) {
                return Result.failure(Exception("복원할 백업이 없습니다"))
            }

            val backupRef = database.getReference(BACKUP_ROOT)
                .child(regionId)
                .child(officeId)
                .child(targetBackupId)

            val snapshot = backupRef.get().await()
            if (!snapshot.exists()) {
                return Result.failure(Exception("백업 데이터를 찾을 수 없습니다: $targetBackupId"))
            }

            val backup = snapshot.getValue(SettlementBackup::class.java)
                ?: return Result.failure(Exception("백업 데이터 파싱 실패"))

            val settlements = backup.settlements.map { it.toSettlementData(regionId, officeId) }

            Log.d(TAG, "정산 데이터 복원 완료: ${settlements.size}건, ID: $targetBackupId")
            Result.success(settlements)

        } catch (e: Exception) {
            Log.e(TAG, "정산 데이터 복원 실패", e)
            Result.failure(e)
        }
    }

    /**
     * 백업 목록 조회
     */
    fun getBackupList(regionId: String, officeId: String): Flow<List<SettlementBackup>> = callbackFlow {
        val backupRef = database.getReference(BACKUP_ROOT)
            .child(regionId)
            .child(officeId)

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val backups = mutableListOf<SettlementBackup>()

                for (child in snapshot.children) {
                    try {
                        val backup = child.getValue(SettlementBackup::class.java)
                        if (backup != null) {
                            backups.add(backup)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "백업 파싱 실패: ${child.key}", e)
                    }
                }

                // 최신순 정렬
                backups.sortByDescending { it.createdAt }
                trySend(backups)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "백업 목록 조회 실패", error.toException())
                close(error.toException())
            }
        }

        backupRef.addValueEventListener(listener)

        awaitClose {
            backupRef.removeEventListener(listener)
        }
    }

    /**
     * 백업 삭제
     */
    suspend fun deleteBackup(regionId: String, officeId: String, backupId: String): Result<Unit> {
        return try {
            val backupRef = database.getReference(BACKUP_ROOT)
                .child(regionId)
                .child(officeId)
                .child(backupId)

            backupRef.removeValue().await()

            Log.d(TAG, "백업 삭제 완료: $backupId")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "백업 삭제 실패", e)
            Result.failure(e)
        }
    }

    /**
     * 백업 존재 여부 확인
     */
    suspend fun hasBackups(regionId: String, officeId: String): Boolean {
        return try {
            val backupRef = database.getReference(BACKUP_ROOT)
                .child(regionId)
                .child(officeId)

            val snapshot = backupRef.limitToFirst(1).get().await()
            snapshot.exists() && snapshot.hasChildren()

        } catch (e: Exception) {
            Log.e(TAG, "백업 존재 여부 확인 실패", e)
            false
        }
    }

    /**
     * 최신 백업 ID 조회
     */
    private suspend fun getLatestBackupId(regionId: String, officeId: String): String? {
        return try {
            val backupRef = database.getReference(BACKUP_ROOT)
                .child(regionId)
                .child(officeId)

            val snapshot = backupRef.orderByChild("createdAt").limitToLast(1).get().await()

            snapshot.children.firstOrNull()?.key

        } catch (e: Exception) {
            Log.e(TAG, "최신 백업 ID 조회 실패", e)
            null
        }
    }

    /**
     * 백업 메타데이터 생성
     */
    private fun createBackupMetadata(settlements: List<SettlementData>): SettlementBackupMetadata {
        val paymentMethodCounts = settlements.groupingBy { sanitizeKey(it.paymentMethod) }.eachCount()
        val driverCounts = settlements.groupingBy { sanitizeKey(it.driverName) }.eachCount()
        val totalFare = settlements.sumOf { it.fare }

        return SettlementBackupMetadata(
            totalCount = settlements.size,
            totalFare = totalFare,
            paymentMethodCounts = paymentMethodCounts,
            driverCounts = driverCounts,
            appVersion = "1.0.0",
            deviceModel = Build.MODEL
        )
    }

    /**
     * Firebase Realtime Database 키로 사용할 수 없는 문자를 제거/치환
     * 금지 문자: . $ # [ ] / ASCII 제어 문자(0-31, 127)
     */
    private fun sanitizeKey(key: String): String {
        return key.replace(".", "_")
            .replace("$", "_")
            .replace("#", "_")
            .replace("[", "_")
            .replace("]", "_")
            .replace("/", "_")
            .filter { it.code in 32..126 } // ASCII 제어 문자 제거
            .ifEmpty { "unknown" }
    }

    /**
     * 오래된 백업 정리
     */
    private suspend fun cleanupOldBackups(regionId: String, officeId: String) {
        try {
            val backupRef = database.getReference(BACKUP_ROOT)
                .child(regionId)
                .child(officeId)

            val snapshot = backupRef.orderByChild("createdAt").get().await()
            val backups = snapshot.children.toList()

            if (backups.size > MAX_BACKUP_COUNT) {
                // 오래된 백업들 삭제 (최신 MAX_BACKUP_COUNT개만 유지)
                val backupsToDelete = backups.dropLast(MAX_BACKUP_COUNT)

                for (backup in backupsToDelete) {
                    backup.ref.removeValue().await()
                }

                Log.d(TAG, "오래된 백업 ${backupsToDelete.size}개 정리 완료")
            }

        } catch (e: Exception) {
            Log.e(TAG, "백업 정리 실패", e)
        }
    }
}