package com.designated.driverapp.data.repository

import com.designated.driverapp.data.model.CustomerPoints

/**
 * 고객 포인트 Repository 인터페이스
 * - Firestore 쿼리를 추상화
 * - 캐싱 전략 적용
 */
interface CustomerPointsRepository {

    /**
     * 고객 포인트 정보 조회 (캐시 우선)
     * @param phoneNumber 고객 전화번호
     * @param forceRefresh true면 캐시 무시하고 Firestore에서 조회
     * @return 고객 포인트 정보, 없으면 null
     */
    suspend fun getCustomerPoints(
        phoneNumber: String,
        forceRefresh: Boolean = false
    ): CustomerPoints?

    /**
     * 고객 포인트 정보 업데이트
     * @param phoneNumber 고객 전화번호
     * @param points 업데이트할 포인트 정보
     */
    suspend fun updateCustomerPoints(
        phoneNumber: String,
        points: CustomerPoints
    ): Boolean

    /**
     * 고객 포인트 생성 (신규 고객)
     * @param phoneNumber 고객 전화번호
     * @return 생성된 포인트 정보
     */
    suspend fun createCustomerPoints(
        phoneNumber: String
    ): CustomerPoints

    /**
     * 캐시 초기화
     */
    fun clearCache()

    /**
     * 특정 고객의 캐시만 무효화
     */
    fun invalidateCache(phoneNumber: String)
}
