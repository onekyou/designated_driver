package com.designated.callmanager.data

data class AddressSearchResult(
    val address: String,           // 전체 주소
    val roadAddress: String? = null,   // 도로명 주소
    val jibunAddress: String? = null,  // 지번 주소
    val placeName: String? = null,     // 장소명
    val latitude: Double? = null,      // 위도
    val longitude: Double? = null      // 경도
)