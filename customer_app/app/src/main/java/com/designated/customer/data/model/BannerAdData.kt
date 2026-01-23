package com.designated.customer.data.model

import com.google.firebase.Timestamp

/**
 * 배너 광고 데이터 모델
 * Firebase Firestore에서 배너 정보를 가져와 표시
 */
data class BannerAdData(
    val id: String = "",
    val text: String = "스마트 대리운전 통합 시스템",
    val imageUrl: String = "",  // 빈 값이면 텍스트만 표시
    val linkUrl: String = "",   // 클릭 시 이동할 URL
    val backgroundColor1: String = "#FF6B35",  // 그라디언트 시작색 (주황색)
    val backgroundColor2: String = "#F7931E",  // 그라디언트 종료색 (밝은 주황색)
    val textColor: String = "#FFFFFF",  // 텍스트 색상 (흰색)
    val isActive: Boolean = true,  // 활성화 여부
    val priority: Int = 0,  // 우선순위 (여러 배너가 있을 경우)
    val createdAt: Timestamp = Timestamp.now(),
    val updatedAt: Timestamp = Timestamp.now()
) {
    /**
     * Firestore에 저장할 Map으로 변환
     */
    fun toMap(): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "text" to text,
            "imageUrl" to imageUrl,
            "linkUrl" to linkUrl,
            "backgroundColor1" to backgroundColor1,
            "backgroundColor2" to backgroundColor2,
            "textColor" to textColor,
            "isActive" to isActive,
            "priority" to priority,
            "createdAt" to createdAt,
            "updatedAt" to updatedAt
        )
    }

    companion object {
        /**
         * Firestore Map에서 BannerAdData 객체로 변환
         */
        fun fromMap(data: Map<String, Any>): BannerAdData {
            return BannerAdData(
                id = data["id"] as? String ?: "",
                text = data["text"] as? String ?: "스마트 대리운전 통합 시스템",
                imageUrl = data["imageUrl"] as? String ?: "",
                linkUrl = data["linkUrl"] as? String ?: "",
                backgroundColor1 = data["backgroundColor1"] as? String ?: "#FF6B35",
                backgroundColor2 = data["backgroundColor2"] as? String ?: "#F7931E",
                textColor = data["textColor"] as? String ?: "#FFFFFF",
                isActive = data["isActive"] as? Boolean ?: true,
                priority = (data["priority"] as? Long)?.toInt() ?: 0,
                createdAt = data["createdAt"] as? Timestamp ?: Timestamp.now(),
                updatedAt = data["updatedAt"] as? Timestamp ?: Timestamp.now()
            )
        }
    }
}
