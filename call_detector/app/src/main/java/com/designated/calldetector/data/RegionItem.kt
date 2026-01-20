package com.designated.calldetector.data

// 하위 호환성을 위해 RegionItem 유지 (ProvinceItem의 별칭으로 사용 가능)
data class RegionItem(
    val id: String = "",
    val name: String = ""
)

data class ProvinceItem(
    val id: String = "",
    val name: String = "" // Firestore 'provinces' 문서에 'name' 필드가 있다고 가정
)

data class CityItem(
    val id: String = "",
    val name: String = "" // Firestore 'cities' 문서에 'name' 필드가 있다고 가정
) 