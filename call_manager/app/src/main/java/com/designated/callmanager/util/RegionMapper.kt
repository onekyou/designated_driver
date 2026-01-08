package com.designated.callmanager.util

/**
 * Firestore 구조 변경 (regions → provinces/cities) 매핑
 *
 * 기존: regions/{regionId}/offices/{officeId}
 * 신규: provinces/{provinceId}/cities/{cityId}/offices/{officeId}
 */
object RegionMapper {

    data class Location(
        val provinceId: String,
        val cityId: String
    )

    /**
     * 기존 regionId를 새로운 provinceId/cityId로 매핑
     */
    private val regionToCityMap = mapOf(
        // 강원특별자치도
        "Hongchon" to Location("gangwon", "hongchon"),
        "hongchon" to Location("gangwon", "hongchon"),

        // 경기도
        "yangpyong" to Location("gyeonggi", "yangpyong"),
        "Yangpyong" to Location("gyeonggi", "yangpyong")
    )

    /**
     * regionId를 Location(provinceId, cityId)로 변환
     *
     * @param regionId 기존 region ID (예: "Hongchon", "yangpyong")
     * @return Location 객체 또는 null (매핑 없을 경우)
     */
    fun mapRegionToLocation(regionId: String): Location? {
        return regionToCityMap[regionId]
    }

    /**
     * Firestore 경로 생성 (offices 상위까지)
     *
     * @param regionId 기존 region ID
     * @return "provinces/{provinceId}/cities/{cityId}" 또는 기존 형식
     */
    fun getOfficesPath(regionId: String): String {
        val location = mapRegionToLocation(regionId)
        return if (location != null) {
            "provinces/${location.provinceId}/cities/${location.cityId}"
        } else {
            // 매핑 없으면 기존 구조 사용 (호환성)
            "regions/$regionId"
        }
    }
}
