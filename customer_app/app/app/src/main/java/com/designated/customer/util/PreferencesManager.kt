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
        private const val KEY_OFFICE_PHONE = "office_phone"
        private const val KEY_BANK_NAME = "bank_name"
        private const val KEY_ACCOUNT_NUMBER = "account_number"
        private const val KEY_ACCOUNT_HOLDER = "account_holder"
        private const val KEY_HOME_ADDRESS = "home_address"
        private const val KEY_FAVORITE_ADDRESSES = "favorite_addresses"
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

    // 사무실 연락처 정보 저장/조회
    fun saveOfficeContactInfo(
        officePhone: String,
        bankName: String,
        accountNumber: String,
        accountHolder: String
    ) {
        prefs.edit().apply {
            putString(KEY_OFFICE_PHONE, officePhone)
            putString(KEY_BANK_NAME, bankName)
            putString(KEY_ACCOUNT_NUMBER, accountNumber)
            putString(KEY_ACCOUNT_HOLDER, accountHolder)
            apply()
        }
    }

    fun getOfficePhone(): String? = prefs.getString(KEY_OFFICE_PHONE, null)
    fun getBankName(): String? = prefs.getString(KEY_BANK_NAME, null)
    fun getAccountNumber(): String? = prefs.getString(KEY_ACCOUNT_NUMBER, null)
    fun getAccountHolder(): String? = prefs.getString(KEY_ACCOUNT_HOLDER, null)

    // 집주소 저장/조회
    fun saveHomeAddress(address: String) {
        prefs.edit().putString(KEY_HOME_ADDRESS, address).apply()
    }

    fun getHomeAddress(): String = prefs.getString(KEY_HOME_ADDRESS, "") ?: ""

    // 즐겨찾기 주소 저장/조회
    fun saveFavoriteAddresses(addresses: List<String>) {
        val joined = addresses.joinToString("|")
        prefs.edit().putString(KEY_FAVORITE_ADDRESSES, joined).apply()
    }

    fun getFavoriteAddresses(): List<String> {
        val saved = prefs.getString(KEY_FAVORITE_ADDRESSES, "") ?: ""
        return if (saved.isEmpty()) emptyList() else saved.split("|")
    }

    fun addFavoriteAddress(address: String) {
        val current = getFavoriteAddresses().toMutableList()
        if (!current.contains(address)) {
            current.add(address)
            saveFavoriteAddresses(current)
        }
    }

    fun removeFavoriteAddress(address: String) {
        val current = getFavoriteAddresses().toMutableList()
        current.remove(address)
        saveFavoriteAddresses(current)
    }

    // 로그아웃
    fun clearUserData() {
        prefs.edit().apply {
            remove(KEY_PHONE_NUMBER)
            remove(KEY_IS_PHONE_VERIFIED)
            remove(KEY_CUSTOMER_GRADE)
            remove(KEY_HOME_ADDRESS)
            remove(KEY_FAVORITE_ADDRESSES)
            apply()
        }
    }

    // 전체 초기화
    fun clearAll() {
        prefs.edit().clear().apply()
    }
}