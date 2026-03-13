package com.designated.driverapp.ui.home

import android.app.Activity
import android.content.Context
import android.widget.Toast
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import android.util.Log
import com.designated.driverapp.viewmodel.DriverViewModel
import com.designated.driverapp.data.Constants
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date

fun logoutUserAndExitApp(context: Context, scope: CoroutineScope, viewModel: DriverViewModel) {
    Log.d("DriverAppUtils", "🔴 [LOGOUT] logoutUserAndExitApp 호출됨")
    Log.d("DriverAppUtils", "🔴 [LOGOUT] 호출 스택:", Exception("Stack trace"))

    val auth = Firebase.auth
    val userId = auth.currentUser?.uid
    val firestore = FirebaseFirestore.getInstance()

    viewModel.stopDriverService()

    if (userId != null) {
        val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        val provinceId = prefs.getString(Constants.PREF_KEY_PROVINCE_ID, null)
        val cityId = prefs.getString(Constants.PREF_KEY_CITY_ID, null)
        val officeId = prefs.getString(Constants.PREF_KEY_OFFICE_ID, null)

        if (provinceId != null && cityId != null && officeId != null) {
            val correctPath = "provinces/$provinceId/cities/$cityId/offices/$officeId/designated_drivers/$userId"
            val driverRef = firestore.document(correctPath)

            Log.d("DriverAppUtils", "🔴 [LOGOUT] status를 OFFLINE으로 업데이트: $correctPath")

            driverRef.update("status", Constants.DRIVER_STATUS_OFFLINE)
                .addOnSuccessListener {
                    Log.d("DriverAppUtils", "✅ [LOGOUT] OFFLINE 업데이트 성공")
                    performSignOut(context, scope)
                }
                .addOnFailureListener { e ->
                    Log.e("DriverAppUtils", "❌ [LOGOUT] OFFLINE 업데이트 실패", e)
                    Toast.makeText(context, "상태 업데이트 실패. 로그아웃을 진행합니다.", Toast.LENGTH_SHORT).show()
                    performSignOut(context, scope)
                }
        } else {
            Toast.makeText(context, "오류: 지역/사무실 정보를 찾을 수 없어 상태 업데이트 불가. 로그아웃만 진행합니다.", Toast.LENGTH_LONG).show()
            performSignOut(context, scope)
        }
    } else {
        performSignOut(context, scope)
    }
}

fun performSignOut(context: Context, scope: CoroutineScope) {
    scope.launch(Dispatchers.IO) {
        try {
            withContext(Dispatchers.Main) {
                Firebase.auth.signOut()
                Toast.makeText(context, "로그아웃되었습니다.", Toast.LENGTH_SHORT).show()
                delay(100)
                (context as? Activity)?.finishAffinity()
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "로그아웃 중 오류 발생: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }
}

/**
 * 저장되지 않은 운행 내역이 있는지 확인
 */
fun hasUnsavedTripHistory(context: Context): Boolean {
    val prefs = context.getSharedPreferences("trip_history", Context.MODE_PRIVATE)
    val historyJson = prefs.getString("history_list", "[]")
    val historyList = JSONArray(historyJson)
    return historyList.length() > 0
}

/**
 * 현재 운행 내역 개수 반환
 */
fun getUnsavedTripCount(context: Context): Int {
    val prefs = context.getSharedPreferences("trip_history", Context.MODE_PRIVATE)
    val historyJson = prefs.getString("history_list", "[]")
    val historyList = JSONArray(historyJson)
    return historyList.length()
}

/**
 * 정산 데이터를 저장하고 초기화
 */
fun saveAndClearSettlement(context: Context) {
    Log.d("DriverAppUtils", "📊 [SETTLEMENT] 정산 저장 및 초기화 시작")

    // 현재 운행 내역 로드
    val tripPrefs = context.getSharedPreferences("trip_history", Context.MODE_PRIVATE)
    val historyJson = tripPrefs.getString("history_list", "[]")
    val historyList = JSONArray(historyJson)

    if (historyList.length() == 0) {
        Log.d("DriverAppUtils", "📊 [SETTLEMENT] 저장할 운행 내역 없음")
        return
    }

    // 정산 계산
    val settlementPrefs = context.getSharedPreferences("settlement_prefs", Context.MODE_PRIVATE)
    val depositPercent = settlementPrefs.getInt("deposit_percent", 60)

    var totalCount = 0
    var totalFare = 0L
    var totalDeposit = 0L
    var totalCredit = 0L

    for (i in 0 until historyList.length()) {
        val item = historyList.getString(i)
        // 요금 파싱: "...요금: 30,000원..." 형식
        val fareMatch = Regex("요금:\\s*([\\d,]+)원").find(item)
        val fare = fareMatch?.groupValues?.get(1)?.replace(",", "")?.toLongOrNull() ?: 0L

        // 납입 여부 파싱: "(납입)" 또는 "(미납)"
        val isDeposited = item.contains("(납입)")

        totalCount++
        totalFare += fare
        if (isDeposited) {
            totalDeposit += (fare * depositPercent / 100)
        } else {
            totalCredit += (fare * depositPercent / 100)
        }
    }

    val realDeposit = totalDeposit
    val realIncome = totalFare - totalDeposit - totalCredit

    // 세션 저장
    val sessionPrefs = context.getSharedPreferences("trip_sessions", Context.MODE_PRIVATE)
    val sessionsJson = sessionPrefs.getString("sessions", "[]")
    val sessionsArr = JSONArray(sessionsJson)

    val now = SimpleDateFormat("yyyy-MM-dd HH:mm").format(Date())
    val historyArr = JSONArray()
    for (i in 0 until historyList.length()) {
        historyArr.put(historyList.getString(i))
    }

    val summaryObj = JSONObject().apply {
        put("totalCount", totalCount)
        put("totalFare", totalFare)
        put("totalDeposit", totalDeposit)
        put("totalCredit", totalCredit)
        put("realDeposit", realDeposit)
        put("realIncome", realIncome)
    }

    val newSession = JSONObject().apply {
        put("date", now)
        put("history", historyArr)
        put("summary", summaryObj)
    }

    sessionsArr.put(newSession)

    // 최대 5개만 유지
    while (sessionsArr.length() > 5) {
        sessionsArr.remove(0)
    }

    sessionPrefs.edit().putString("sessions", sessionsArr.toString()).apply()

    // 현재 운행 내역 초기화
    tripPrefs.edit().putString("history_list", "[]").apply()

    Log.d("DriverAppUtils", "[SETTLEMENT] Settlement saved: " + totalCount + " trips, total " + totalFare + " won")
}