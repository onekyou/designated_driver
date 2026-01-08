package com.designated.customer.data.repository

import com.designated.customer.data.dao.StepDao
import com.designated.customer.data.model.DailyStepData
import com.designated.customer.data.model.WeeklyStepSummary
import com.designated.customer.data.model.MonthlyStepSummary
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.*

/**
 * 걸음 수 데이터 저장/조회를 담당하는 Repository
 */
class StepRepository(private val stepDao: StepDao) {

    // ========== 일일 데이터 ==========

    /**
     * 오늘 걸음 수 조회
     */
    suspend fun getTodaySteps(): DailyStepData? {
        val today = getCurrentDate()
        return stepDao.getStepsByDate(today)
    }

    /**
     * 오늘 걸음 수 조회 (Flow)
     */
    fun getTodayStepsFlow(): Flow<DailyStepData?> {
        val today = getCurrentDate()
        return stepDao.getStepsByDateFlow(today)
    }

    /**
     * 걸음 수 업데이트
     */
    suspend fun updateSteps(steps: Int, goal: Int = 10000) {
        val today = getCurrentDate()
        val existingData = stepDao.getStepsByDate(today)

        val data = if (existingData != null) {
            existingData.copy(
                steps = steps,
                goal = goal,
                updatedAt = System.currentTimeMillis()
            )
        } else {
            DailyStepData(
                date = today,
                steps = steps,
                goal = goal
            )
        }

        stepDao.insertDailySteps(data)

        // 주간/월간 집계 업데이트
        updateWeeklySummary()
        updateMonthlySummary()
    }

    /**
     * 최근 30일 걸음 수 조회
     */
    fun getLastMonthSteps(): Flow<List<DailyStepData>> {
        return stepDao.getLastMonthSteps()
    }

    // ========== 주간 집계 ==========

    /**
     * 이번 주 집계 조회 (Flow)
     */
    fun getThisWeekSummaryFlow(): Flow<WeeklyStepSummary?> {
        val weekKey = getCurrentWeekKey()
        return stepDao.getWeeklySummaryFlow(weekKey)
    }

    /**
     * 주간 집계 업데이트
     */
    private suspend fun updateWeeklySummary() {
        val weekKey = getCurrentWeekKey()
        val (startDate, endDate) = getWeekDateRange()

        val weeklyData = stepDao.getStepsBetween(startDate, endDate)

        if (weeklyData.isNotEmpty()) {
            val totalSteps = weeklyData.sumOf { it.steps }
            val daysActive = weeklyData.count { it.steps > 0 }
            val avgSteps = if (daysActive > 0) totalSteps / daysActive else 0

            val summary = WeeklyStepSummary(
                weekKey = weekKey,
                totalSteps = totalSteps,
                avgSteps = avgSteps,
                daysActive = daysActive
            )

            stepDao.insertWeeklySummary(summary)
        }
    }

    // ========== 월간 집계 ==========

    /**
     * 이번 달 집계 조회 (Flow)
     */
    fun getThisMonthSummaryFlow(): Flow<MonthlyStepSummary?> {
        val monthKey = getCurrentMonthKey()
        return stepDao.getMonthlySummaryFlow(monthKey)
    }

    /**
     * 월간 집계 업데이트
     */
    private suspend fun updateMonthlySummary() {
        val monthKey = getCurrentMonthKey()
        val (startDate, endDate) = getMonthDateRange()

        val monthlyData = stepDao.getStepsBetween(startDate, endDate)

        if (monthlyData.isNotEmpty()) {
            val totalSteps = monthlyData.sumOf { it.steps }
            val daysActive = monthlyData.count { it.steps > 0 }
            val avgSteps = if (daysActive > 0) totalSteps / daysActive else 0

            val summary = MonthlyStepSummary(
                monthKey = monthKey,
                totalSteps = totalSteps,
                avgSteps = avgSteps,
                daysActive = daysActive
            )

            stepDao.insertMonthlySummary(summary)
        }
    }

    // ========== 목표 설정 ==========

    /**
     * 목표 걸음 수 변경
     */
    suspend fun updateGoal(newGoal: Int) {
        val today = getCurrentDate()
        val existingData = stepDao.getStepsByDate(today)

        if (existingData != null) {
            val updatedData = existingData.copy(
                goal = newGoal,
                updatedAt = System.currentTimeMillis()
            )
            stepDao.insertDailySteps(updatedData)
        } else {
            // 오늘 데이터가 없으면 새로 생성
            val data = DailyStepData(
                date = today,
                steps = 0,
                goal = newGoal
            )
            stepDao.insertDailySteps(data)
        }
    }

    // ========== 유틸리티 함수 ==========

    /**
     * 현재 날짜 문자열 (YYYY-MM-DD)
     */
    private fun getCurrentDate(): String {
        return LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
    }

    /**
     * 현재 주차 키 (YYYY-Wnn)
     */
    private fun getCurrentWeekKey(): String {
        val now = LocalDate.now()
        val weekFields = WeekFields.of(Locale.getDefault())
        val weekNumber = now.get(weekFields.weekOfWeekBasedYear())
        return "${now.year}-W${weekNumber.toString().padStart(2, '0')}"
    }

    /**
     * 현재 월 키 (YYYY-MM)
     */
    private fun getCurrentMonthKey(): String {
        val now = LocalDate.now()
        return "${now.year}-${now.monthValue.toString().padStart(2, '0')}"
    }

    /**
     * 이번 주 시작일과 종료일
     */
    private fun getWeekDateRange(): Pair<String, String> {
        val now = LocalDate.now()
        val weekFields = WeekFields.of(Locale.getDefault())
        val startOfWeek = now.with(weekFields.dayOfWeek(), 1)
        val endOfWeek = now.with(weekFields.dayOfWeek(), 7)

        return Pair(
            startOfWeek.format(DateTimeFormatter.ISO_LOCAL_DATE),
            endOfWeek.format(DateTimeFormatter.ISO_LOCAL_DATE)
        )
    }

    /**
     * 이번 달 시작일과 종료일
     */
    private fun getMonthDateRange(): Pair<String, String> {
        val now = LocalDate.now()
        val startOfMonth = now.withDayOfMonth(1)
        val endOfMonth = now.withDayOfMonth(now.lengthOfMonth())

        return Pair(
            startOfMonth.format(DateTimeFormatter.ISO_LOCAL_DATE),
            endOfMonth.format(DateTimeFormatter.ISO_LOCAL_DATE)
        )
    }
}
