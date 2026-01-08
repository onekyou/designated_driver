package com.designated.customer.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 일일 걸음 수 데이터
 * Room Database Entity
 */
@Entity(tableName = "daily_steps")
data class DailyStepData(
    @PrimaryKey
    val date: String,                           // 날짜 (YYYY-MM-DD)
    val steps: Int,                             // 오늘 걸음 수
    val goal: Int = 10000,                      // 목표 걸음 수
    val updatedAt: Long = System.currentTimeMillis(),  // 업데이트 시간

    // 향후 확장 필드 (기본값 제공)
    val activeMinutes: Int = 0,                 // 활동 시간 (분)
    val calories: Float = 0f,                   // 소모 칼로리 (kcal)
    val distance: Float = 0f,                   // 이동 거리 (km)
    val avgSpeed: Float = 0f                    // 평균 속도 (km/h)
) {
    /**
     * 목표 달성률 계산 (0.0 ~ 1.0)
     */
    fun getProgress(): Float {
        return (steps.toFloat() / goal.toFloat()).coerceIn(0f, 1f)
    }

    /**
     * 목표 달성 여부
     */
    fun isGoalAchieved(): Boolean {
        return steps >= goal
    }

    /**
     * 목표 달성률 퍼센트 (0 ~ 100)
     */
    fun getProgressPercent(): Int {
        return ((steps * 100) / goal).coerceIn(0, 100)
    }
}

/**
 * 주간 걸음 수 집계
 */
@Entity(tableName = "weekly_summary")
data class WeeklyStepSummary(
    @PrimaryKey
    val weekKey: String,                        // 주차 키 (YYYY-Wnn)
    val totalSteps: Int,                        // 총 걸음 수
    val avgSteps: Int,                          // 평균 걸음 수
    val daysActive: Int,                        // 활동한 일수
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * 월간 걸음 수 집계
 */
@Entity(tableName = "monthly_summary")
data class MonthlyStepSummary(
    @PrimaryKey
    val monthKey: String,                       // 월 키 (YYYY-MM)
    val totalSteps: Int,                        // 총 걸음 수
    val avgSteps: Int,                          // 평균 걸음 수
    val daysActive: Int,                        // 활동한 일수
    val updatedAt: Long = System.currentTimeMillis()
)
