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
            val sharedPreferences = context.getSharedPreferences("driver_login_prefs", Context.MODE_PRIVATE)
            val autoLoginEnabled = sharedPreferences.getBoolean("auto_login", false)

            if (!autoLoginEnabled) {
                sharedPreferences.edit().apply {
                    remove("identifier")
                    remove("password")
                    apply()
                }
            } else {
            }

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