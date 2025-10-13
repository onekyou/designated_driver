package com.designated.customer.data.dao

import androidx.room.*
import com.designated.customer.data.model.DailyStepData
import com.designated.customer.data.model.WeeklyStepSummary
import com.designated.customer.data.model.MonthlyStepSummary
import kotlinx.coroutines.flow.Flow

/**
 * 걸음 수 데이터 접근 인터페이스 (DAO)
 */
@Dao
interface StepDao {

    // ========== 일일 데이터 ==========

    /**
     * 특정 날짜의 걸음 수 조회
     */
    @Query("SELECT * FROM daily_steps WHERE date = :date")
    suspend fun getStepsByDate(date: String): DailyStepData?

    /**
     * 특정 날짜의 걸음 수 조회 (Flow)
     */
    @Query("SELECT * FROM daily_steps WHERE date = :date")
    fun getStepsByDateFlow(date: String): Flow<DailyStepData?>

    /**
     * 일일 걸음 수 저장/업데이트
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDailySteps(data: DailyStepData)

    /**
     * 최근 30일 걸음 수 조회
     */
    @Query("SELECT * FROM daily_steps ORDER BY date DESC LIMIT 30")
    fun getLastMonthSteps(): Flow<List<DailyStepData>>

    /**
     * 특정 기간의 걸음 수 조회
     */
    @Query("SELECT * FROM daily_steps WHERE date BETWEEN :startDate AND :endDate ORDER BY date ASC")
    suspend fun getStepsBetween(startDate: String, endDate: String): List<DailyStepData>

    /**
     * 전체 일일 데이터 삭제
     */
    @Query("DELETE FROM daily_steps")
    suspend fun deleteAllDailySteps()

    // ========== 주간 집계 ==========

    /**
     * 특정 주의 집계 조회
     */
    @Query("SELECT * FROM weekly_summary WHERE weekKey = :weekKey")
    suspend fun getWeeklySummary(weekKey: String): WeeklyStepSummary?

    /**
     * 특정 주의 집계 조회 (Flow)
     */
    @Query("SELECT * FROM weekly_summary WHERE weekKey = :weekKey")
    fun getWeeklySummaryFlow(weekKey: String): Flow<WeeklyStepSummary?>

    /**
     * 주간 집계 저장/업데이트
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeeklySummary(summary: WeeklyStepSummary)

    /**
     * 최근 12주 집계 조회
     */
    @Query("SELECT * FROM weekly_summary ORDER BY weekKey DESC LIMIT 12")
    fun getLastWeeks(): Flow<List<WeeklyStepSummary>>

    /**
     * 전체 주간 집계 삭제
     */
    @Query("DELETE FROM weekly_summary")
    suspend fun deleteAllWeeklySummaries()

    // ========== 월간 집계 ==========

    /**
     * 특정 월의 집계 조회
     */
    @Query("SELECT * FROM monthly_summary WHERE monthKey = :monthKey")
    suspend fun getMonthlySummary(monthKey: String): MonthlyStepSummary?

    /**
     * 특정 월의 집계 조회 (Flow)
     */
    @Query("SELECT * FROM monthly_summary WHERE monthKey = :monthKey")
    fun getMonthlySummaryFlow(monthKey: String): Flow<MonthlyStepSummary?>

    /**
     * 월간 집계 저장/업데이트
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMonthlySummary(summary: MonthlyStepSummary)

    /**
     * 최근 12개월 집계 조회
     */
    @Query("SELECT * FROM monthly_summary ORDER BY monthKey DESC LIMIT 12")
    fun getLastMonths(): Flow<List<MonthlyStepSummary>>

    /**
     * 전체 월간 집계 삭제
     */
    @Query("DELETE FROM monthly_summary")
    suspend fun deleteAllMonthlySummaries()

    // ========== 통계 쿼리 ==========

    /**
     * 총 걸음 수 합계 조회
     */
    @Query("SELECT SUM(steps) FROM daily_steps")
    suspend fun getTotalSteps(): Int?

    /**
     * 평균 걸음 수 조회
     */
    @Query("SELECT AVG(steps) FROM daily_steps WHERE steps > 0")
    suspend fun getAverageSteps(): Float?
}
