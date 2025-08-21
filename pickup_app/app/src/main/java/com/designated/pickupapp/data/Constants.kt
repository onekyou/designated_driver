package com.designated.pickupapp.data

object Constants {
    // SharedPreferences 키
    const val PREFS_NAME = "pickup_driver_prefs"
    const val PREF_EMAIL = "email"
    const val PREF_PASSWORD = "password"
    const val PREF_AUTO_LOGIN = "auto_login"
    const val PREF_KEY_REGION_ID = "regionId"
    const val PREF_KEY_OFFICE_ID = "officeId"
    const val PREF_KEY_DRIVER_ID = "driverId"
    const val PREF_KEY_DRIVER_NAME = "driverName"
    const val PREF_KEY_PHONE_NUMBER = "phoneNumber"
    
    // Firestore 컬렉션 및 필드
    const val COLLECTION_REGIONS = "regions"
    const val COLLECTION_OFFICES = "offices"
    const val COLLECTION_DRIVERS = "pickup_drivers"
    const val COLLECTION_PENDING_DRIVERS = "pending_pickup_drivers"
    const val COLLECTION_CALLS = "calls"
    
    const val FIELD_STATUS = "status"
    const val FIELD_FCM_TOKEN = "fcmToken"
    const val FIELD_NAME = "name"
    const val FIELD_PHONE_NUMBER = "phoneNumber"
    const val FIELD_AUTH_UID = "authUid"
    const val FIELD_TIMESTAMP = "timestamp"
    const val FIELD_ASSIGNED_DRIVER_ID = "assignedDriverId"
    
    // 픽업 기사 상태
    const val STATUS_AVAILABLE = "AVAILABLE"
    const val STATUS_BUSY = "BUSY"
    const val STATUS_OFFLINE = "OFFLINE"
    const val STATUS_ONLINE = "AVAILABLE"  // ONLINE은 AVAILABLE과 동일
    
    // 콜 상태
    const val STATUS_WAITING = "WAITING"
    const val STATUS_ASSIGNED = "ASSIGNED"
    const val STATUS_ACCEPTED = "ACCEPTED"
    const val STATUS_IN_PROGRESS = "IN_PROGRESS"
    const val STATUS_COMPLETED = "COMPLETED"
    const val STATUS_AWAITING_SETTLEMENT = "AWAITING_SETTLEMENT"
}