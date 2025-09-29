package com.designated.customer.util

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "customer_app_prefs",
        Context.MODE_PRIVATE
    )

    companion object {
        private const val KEY_OFFICE_ID = "office_id"
        private const val KEY_REGION_ID = "region_id"
        private const val KEY_PHONE_NUMBER = "phone_number"
        private const val KEY_IS_PHONE_VERIFIED = "is_phone_verified"
        private const val KEY_CUSTOMER_GRADE = "customer_grade"
    }

    // 사무실 정보 저장/조회
    fun saveOfficeInfo(officeId: String, regionId: String) {
        prefs.edit().apply {
            putString(KEY_OFFICE_ID, officeId)
            putString(KEY_REGION_ID, regionId)
            apply()
        }
    }

    fun getOfficeId(): String? = prefs.getString(KEY_OFFICE_ID, null)
    fun getRegionId(): String? = prefs.getString(KEY_REGION_ID, null)

    // 전화번호 저장/조회
    fun savePhoneNumber(phoneNumber: String) {
        prefs.edit().apply {
            putString(KEY_PHONE_NUMBER, phoneNumber)
            putBoolean(KEY_IS_PHONE_VERIFIED, true)
            apply()
        }
    }

    fun getPhoneNumber(): String? = prefs.getString(KEY_PHONE_NUMBER, null)
    fun isPhoneVerified(): Boolean = prefs.getBoolean(KEY_IS_PHONE_VERIFIED, false)

    // 고객 등급
    fun saveCustomerGrade(grade: String) {
        prefs.edit().putString(KEY_CUSTOMER_GRADE, grade).apply()
    }

    fun getCustomerGrade(): String = prefs.getString(KEY_CUSTOMER_GRADE, "BRONZE") ?: "BRONZE"

    // 로그아웃
    fun clearUserData() {
        prefs.edit().apply {
            remove(KEY_PHONE_NUMBER)
            remove(KEY_IS_PHONE_VERIFIED)
            remove(KEY_CUSTOMER_GRADE)
            apply()
        }
    }

    // 전체 초기화
    fun clearAll() {
        prefs.edit().clear().apply()
    }
}