package com.designated.callmanager.di

import android.content.Context
import com.designated.callmanager.data.local.AppDatabase
import com.designated.callmanager.data.repository.PointRepository
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * 데이터베이스 및 Repository 생성 헬퍼 (Hilt 없이)
 */
object DatabaseProvider {

    /**
     * Room 데이터베이스 제공
     */
    fun provideAppDatabase(context: Context): AppDatabase {
        return AppDatabase.getDatabase(context)
    }

    /**
     * Repository용 CoroutineScope 제공
     */
    fun provideRepositoryScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    /**
     * PointRepository 제공
     */
    fun providePointRepository(
        database: AppDatabase,
        firestore: FirebaseFirestore,
        scope: CoroutineScope
    ): PointRepository {
        return PointRepository(database, firestore, scope)
    }
}