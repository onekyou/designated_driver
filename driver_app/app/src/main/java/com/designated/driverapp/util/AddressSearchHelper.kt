package com.designated.driverapp.util

import android.util.Log
import com.designated.driverapp.BuildConfig
import com.designated.driverapp.data.AddressSearchResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class AddressSearchHelper {
    companion object {
        private const val TAG = "AddressSearchHelper"
        private val KAKAO_API_KEY = BuildConfig.KAKAO_API_KEY
        private const val KAKAO_SEARCH_URL = "https://dapi.kakao.com/v2/local/search/keyword.json"
        private const val KAKAO_ADDRESS_URL = "https://dapi.kakao.com/v2/local/search/address.json"
    }

    /**
     * 키워드로 주소 검색
     */
    fun searchAddress(query: String, onResult: (List<AddressSearchResult>) -> Unit) {
        if (query.isBlank()) {
            onResult(emptyList())
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val results = performSearch(query)
                withContext(Dispatchers.Main) {
                    onResult(results)
                }
            } catch (e: Exception) {
                Log.e(TAG, "주소 검색 실패", e)
                withContext(Dispatchers.Main) {
                    onResult(emptyList())
                }
            }
        }
    }

    private fun performSearch(query: String): List<AddressSearchResult> {
        val results = mutableListOf<AddressSearchResult>()

        // 1. 키워드 검색 시도
        val keywordResults = searchByKeyword(query)
        results.addAll(keywordResults)

        // 2. 주소 검색 시도
        if (results.isEmpty()) {
            val addressResults = searchByAddress(query)
            results.addAll(addressResults)
        }

        return results.take(10)
    }

    private fun searchByKeyword(query: String): List<AddressSearchResult> {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = URL("$KAKAO_SEARCH_URL?query=$encodedQuery&size=10")

        return executeRequest(url)
    }

    private fun searchByAddress(query: String): List<AddressSearchResult> {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = URL("$KAKAO_ADDRESS_URL?query=$encodedQuery&size=10")

        return executeRequest(url)
    }

    private fun executeRequest(url: URL): List<AddressSearchResult> {
        val results = mutableListOf<AddressSearchResult>()
        var connection: HttpURLConnection? = null

        try {
            connection = url.openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "KakaoAK $KAKAO_API_KEY")
                setRequestProperty("Content-Type", "application/json")
                connectTimeout = 5000
                readTimeout = 5000
            }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonObject = JSONObject(response)
                val documents = jsonObject.optJSONArray("documents")

                documents?.let {
                    for (i in 0 until it.length()) {
                        val doc = it.getJSONObject(i)

                        val placeName = doc.optString("place_name", null)
                        val roadAddress = doc.optString("road_address_name", null)
                        val jibunAddress = doc.optString("address_name", null)

                        val addressName = doc.optString("address_name", null)
                        val roadAddressName = doc.optJSONObject("road_address")
                            ?.optString("address_name", null)

                        val displayAddress = when {
                            !roadAddress.isNullOrEmpty() -> roadAddress
                            !roadAddressName.isNullOrEmpty() -> roadAddressName
                            !jibunAddress.isNullOrEmpty() -> jibunAddress
                            !addressName.isNullOrEmpty() -> addressName
                            else -> continue
                        }

                        val lat = doc.optString("y", null)?.toDoubleOrNull()
                        val lng = doc.optString("x", null)?.toDoubleOrNull()

                        results.add(
                            AddressSearchResult(
                                address = if (!placeName.isNullOrEmpty()) {
                                    "$placeName ($displayAddress)"
                                } else {
                                    displayAddress
                                },
                                roadAddress = roadAddress ?: roadAddressName,
                                jibunAddress = jibunAddress ?: addressName,
                                placeName = placeName,
                                latitude = lat,
                                longitude = lng
                            )
                        )
                    }
                }
            } else {
                Log.e(TAG, "API 요청 실패: ${connection.responseCode}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "API 요청 중 오류", e)
        } finally {
            // 모든 경우에서 연결 해제 (리소스 누수 방지)
            try {
                connection?.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "연결 해제 중 오류", e)
            }
        }

        return results
    }
}