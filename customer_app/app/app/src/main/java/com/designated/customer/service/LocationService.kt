package com.designated.customer.service

import android.content.Context
import android.location.Geocoder
import android.location.LocationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

class LocationService(private val context: Context) {

    suspend fun getCurrentLocation(): String {
        return withContext(Dispatchers.IO) {
            try {
                val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

                // 실제 구현에서는 위치 권한 확인 및 GPS 위치 가져오기가 필요
                // 현재는 임시로 기본 위치 반환
                "현재 위치를 가져올 수 없습니다"
            } catch (e: Exception) {
                throw Exception("위치 서비스를 사용할 수 없습니다")
            }
        }
    }

    suspend fun getAddressFromCoordinates(latitude: Double, longitude: Double): String {
        return withContext(Dispatchers.IO) {
            try {
                val geocoder = Geocoder(context, Locale.getDefault())
                val addresses = geocoder.getFromLocation(latitude, longitude, 1)

                if (addresses?.isNotEmpty() == true) {
                    val address = addresses[0]
                    buildString {
                        if (address.adminArea != null) append("${address.adminArea} ")
                        if (address.locality != null) append("${address.locality} ")
                        if (address.thoroughfare != null) append("${address.thoroughfare} ")
                        if (address.featureName != null) append(address.featureName)
                    }.trim()
                } else {
                    "주소를 찾을 수 없습니다"
                }
            } catch (e: Exception) {
                throw Exception("주소 변환 중 오류가 발생했습니다")
            }
        }
    }
}