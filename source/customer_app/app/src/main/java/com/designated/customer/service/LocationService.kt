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
                val location: Location = fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    cancellationTokenSource.token
                ).await()

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