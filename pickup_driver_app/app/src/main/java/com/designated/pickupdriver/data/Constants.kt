package com.designated.pickupdriver.data

object Constants {
    const val PREFS_NAME = "pickup_driver_prefs"
    const val PREF_KEY_PROVINCE_ID = "pref_province_id"
    const val PREF_KEY_CITY_ID = "pref_city_id"
    const val PREF_KEY_OFFICE_ID = "pref_office_id"
    const val PREF_KEY_DRIVER_ID = "pref_driver_id"

    const val APPROVAL_STATUS_APPROVED = "APPROVED"

    const val COLLECTION_PENDING_DRIVERS = "pending_drivers"
    const val COLLECTION_GROUP_PICKUP_DRIVERS = "pickup_drivers"
    const val COLLECTION_PROVINCES = "provinces"
    const val COLLECTION_CITIES = "cities"
    const val COLLECTION_OFFICES = "offices"
    const val COLLECTION_CALLS = "calls"

    const val DRIVER_TYPE_PICKUP = "픽업기사"

    const val FIELD_FCM_TOKEN = "fcmToken"
    const val FIELD_AUTH_UID = "authUid"

    const val STATUS_WAITING = "WAITING"
    const val STATUS_ASSIGNED = "ASSIGNED"
    const val STATUS_ACCEPTED = "ACCEPTED"
    const val STATUS_IN_PROGRESS = "IN_PROGRESS"
    const val STATUS_AWAITING_SETTLEMENT = "AWAITING_SETTLEMENT"

    // FCM 메시지 타입
    const val MSG_TYPE_NEW_CHAT_MESSAGE = "NEW_CHAT_MESSAGE"
    const val MSG_TYPE_NEW_CALL = "NEW_CALL"
    const val MSG_TYPE_CALL_STATUS_UPDATE = "CALL_STATUS_UPDATE"

    // WAITING 콜 노출 컷오프 (30분)
    const val WAITING_CUTOFF_MS: Long = 30L * 60L * 1000L
}
