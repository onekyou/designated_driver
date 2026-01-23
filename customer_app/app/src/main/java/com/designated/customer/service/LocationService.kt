package com.designated.customer.service

import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import java.util.*

class LocationService(private val context: Context) {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    suspend fun getCurrentLocation(): String {
        return withContext(Dispatchers.IO) {
            try {
                // 위치 권한 확인
                val hasFineLocation = ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED

                val hasCoarseLocation = ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED

                if (!hasFineLocation && !hasCoarseLocation) {
                    throw Exception("위치 권한이 필요합니다")
                }

                // 현재 위치 가져오기
                val cancellationTokenSource = CancellationTokenSource()
                val location: Location? = fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    cancellationTokenSource.token
                ).await()

                // Null 체크 - 위치를 가져오지 못한 경우
                if (location == null) {
                    throw Exception("위치 정보를 가져올 수 없습니다. GPS가 켜져 있는지 확인해주세요.")
                }

                // 위치를 주소로 변환
                getAddressFromCoordinates(location.latitude, location.longitude)
            } catch (e: SecurityException) {
                android.util.Log.e("LocationService", "위치 권한 없음", e)
                throw Exception("위치 권한이 필요합니다")
            } catch (e: Exception) {
                android.util.Log.e("LocationService", "현재 위치 가져오기 실패", e)
                throw Exception("현재 위치를 가져올 수 없습니다: ${e.message}")
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
                    // 간략한 주소 표시: 읍/면 + 번지 (예: 용문면 212-3)
                    buildString {
                        // subLocality가 있으면 사용 (읍/면/동)
                        if (address.subLocality != null) {
                            append("${address.subLocality} ")
                        } else if (address.thoroughfare != null) {
                            // subLocality가 없으면 thoroughfare 사용
                            append("${address.thoroughfare} ")
                        }
                        // 번지/건물명
                        if (address.featureName != null && address.featureName != address.subLocality && address.featureName != address.thoroughfare) {
                            append(address.featureName)
                        }
                    }.trim().ifEmpty {
                        // 간략 주소가 비어있으면 전체 주소 반환
                        buildString {
                            if (address.locality != null) append("${address.locality} ")
                            if (address.thoroughfare != null) append("${address.thoroughfare} ")
                            if (address.featureName != null) append(address.featureName)
                        }.trim()
                    }
                } else {
                    "주소를 찾을 수 없습니다"
                }
            } catch (e: Exception) {
                throw Exception("주소 변환 중 오류가 발생했습니다")
            }
        }
    }
}