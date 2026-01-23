package com.designated.driverapp.worker

import android.content.Context
import android.util.Log
import androidx.work.*
import com.designated.driverapp.data.repository.SettlementRepository
import java.util.concurrent.TimeUnit

/**
 * 정산 데이터 백그라운드 동기화 Worker
 * - 오프라인에서 생성된 데이터를 네트워크 연결 시 동기화
 */
class SettlementSyncWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val TAG = "SettlementSyncWorker"
    private val repository = SettlementRepository.getInstance(context)

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting settlement sync work")

        return try {
            val result = repository.syncAllPending()

            result.fold(
                onSuccess = { syncedCount ->
                    Log.d(TAG, "Sync completed: $syncedCount items synced")
                    Result.success()
                },
                onFailure = { error ->
                    Log.e(TAG, "Sync failed", error)
                    if (runAttemptCount < 3) {
                        Result.retry()
                    } else {
                        Result.failure()
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during sync", e)
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    companion object {
        private const val WORK_NAME = "settlement_sync_work"
        private const val PERIODIC_WORK_NAME = "settlement_periodic_sync"

        /**
         * 즉시 동기화 요청 (일회성)
         */
        fun enqueueOneTimeSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<SettlementSyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    WORK_NAME,
                    ExistingWorkPolicy.KEEP,
                    workRequest
                )

            Log.d("SettlementSyncWorker", "One-time sync work enqueued")
        }

        /**
         * 주기적 동기화 설정 (15분마다)
         */
        fun enqueuePeriodicSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val periodicWorkRequest = PeriodicWorkRequestBuilder<SettlementSyncWorker>(
                15, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    PERIODIC_WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    periodicWorkRequest
                )

            Log.d("SettlementSyncWorker", "Periodic sync work enqueued")
        }

        /**
         * 네트워크 연결 시 자동 동기화
         */
        fun enqueueOnNetworkAvailable(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<SettlementSyncWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    "${WORK_NAME}_network",
                    ExistingWorkPolicy.REPLACE,
                    workRequest
                )
        }

        /**
         * 모든 동기화 작업 취소
         */
        fun cancelAllSync(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
        }
    }
}
