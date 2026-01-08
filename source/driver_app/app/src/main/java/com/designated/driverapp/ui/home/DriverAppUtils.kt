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
    val auth = Firebase.auth
    val userId = auth.currentUser?.uid
    val firestore = FirebaseFirestore.getInstance()

    viewModel.stopDriverService()

    if (userId != null) {
        val prefs = context.getSharedPreferences("driver_prefs", Context.MODE_PRIVATE)
        val regionId = prefs.getString("regionId", null)
        val officeId = prefs.getString("officeId", null)

        if (regionId != null && officeId != null) {
            val correctPath = "regions/$regionId/offices/$officeId/designated_drivers/$userId"
            val driverRef = firestore.document(correctPath)

            driverRef.update("status", Constants.DRIVER_STATUS_OFFLINE)
                .addOnSuccessListener {
                    performSignOut(context, scope)
                }
                .addOnFailureListener { e ->
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